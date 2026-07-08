package com.example.s2daotestgen.cli;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import com.example.s2daotestgen.dao.DaoAnalyzer;
import com.example.s2daotestgen.dao.Dialect;
import com.example.s2daotestgen.dao.EntityAnalyzer;
import com.example.s2daotestgen.dao.SourceRepository;
import com.example.s2daotestgen.gen.GenerationReport;
import com.example.s2daotestgen.gen.MetaJsonReader;
import com.example.s2daotestgen.gen.TestClassGenerator;
import com.example.s2daotestgen.json.JsonWriter;
import com.example.s2daotestgen.model.MetaModel.DaoMeta;
import com.example.s2daotestgen.model.MetaModel.EntityMeta;
import com.example.s2daotestgen.sql.SqlFileIndex;

/**
 * s2dao-testgen CLI。
 *
 * <pre>
 * # フェーズ1: 解析(メタ JSON 出力)
 * java -jar s2dao-testgen.jar analyze --src &lt;javaソースdir&gt; --sql &lt;sqldir&gt; \
 *      --out &lt;出力dir&gt; [--dbms oracle|postgre|standard]
 *
 * # フェーズ2: テストコード生成(メタ JSON から)
 * java -jar s2dao-testgen.jar generate --meta &lt;metaJSONdir&gt; --out &lt;テスト出力dir&gt; \
 *      [--package &lt;pkg&gt;] [--encoding &lt;charset&gt;] [--dbms oracle|postgre]
 *
 * # 解析 + 生成 一括
 * java -jar s2dao-testgen.jar gen-all --src &lt;javaソースdir&gt; --sql &lt;sqldir&gt; \
 *      --out &lt;テスト出力dir&gt; [--meta &lt;中間metadir&gt;] [--package &lt;pkg&gt;] \
 *      [--encoding &lt;charset&gt;] [--dbms oracle|postgre]
 *
 * --encoding は生成する .java の文字コード(既定 UTF-8)。Pleiades/旧 Eclipse(Windows)の
 * MS932 環境向けに MS932 / Shift_JIS / Windows-31J を指定できる。
 * </pre>
 */
public final class Main {

    public static void main(final String[] args) throws Exception {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
            return;
        }
        final String cmd = args[0];
        if (cmd.equals("analyze")) {
            analyzeCmd(args);
        } else if (cmd.equals("generate")) {
            generateCmd(args);
        } else if (cmd.equals("gen-all")) {
            genAllCmd(args);
        } else {
            printUsage();
            System.exit(0);
        }
    }

    // ================= analyze =================

    private static void analyzeCmd(final String[] args) throws Exception {
        final List<File> srcDirs = new ArrayList<File>();
        final List<File> sqlDirs = new ArrayList<File>();
        File outDir = null;
        Dialect dialect = Dialect.STANDARD;

        for (int i = 1; i < args.length; i++) {
            final String a = args[i];
            if (a.equals("--src") && i + 1 < args.length) {
                srcDirs.add(new File(args[++i]));
            } else if (a.equals("--sql") && i + 1 < args.length) {
                sqlDirs.add(new File(args[++i]));
            } else if (a.equals("--out") && i + 1 < args.length) {
                outDir = new File(args[++i]);
            } else if (a.equals("--dbms") && i + 1 < args.length) {
                dialect = Dialect.fromString(args[++i]);
            } else {
                System.err.println("不明な引数: " + a);
                printUsage();
                System.exit(1);
                return;
            }
        }
        if (srcDirs.isEmpty() || outDir == null) {
            System.err.println("--src と --out は必須です。");
            printUsage();
            System.exit(1);
            return;
        }
        if (sqlDirs.isEmpty()) {
            sqlDirs.addAll(srcDirs);
        }

        final int count = analyze(srcDirs, sqlDirs, outDir, dialect);
        System.out.println("完了: " + count + " 件の DAO メタ JSON を出力しました → "
                + outDir.getAbsolutePath());
    }

    /** 解析本体。生成したメタ JSON 数を返す。 */
    public static int analyze(final List<File> srcDirs, final List<File> sqlDirs,
            final File outDir, final Dialect dialect) throws Exception {
        final SourceRepository repo = new SourceRepository();
        for (final File d : srcDirs) {
            repo.addSourceRoot(d);
        }
        final SqlFileIndex sqlIndex = new SqlFileIndex();
        for (final File d : sqlDirs) {
            sqlIndex.addRoot(d);
        }

        final DaoAnalyzer analyzer = new DaoAnalyzer(repo, sqlIndex, dialect);
        final EntityAnalyzer entityAnalyzer = new EntityAnalyzer(repo);
        final JsonWriter writer = new JsonWriter();
        outDir.mkdirs();

        int count = 0;
        for (final SourceRepository.TypeInfo info : repo.getAllTypes()) {
            if (!analyzer.isDao(info)) {
                continue;
            }
            final DaoMeta dao = analyzer.analyze(info);
            final File outFile = new File(outDir, info.simpleName + ".meta.json");
            writer.write(dao, outFile);
            System.out.println("  " + info.fqn + " → " + outFile.getName()
                    + " (methods=" + dao.methods.size() + ")");
            count++;
        }

        // --- スタンドアロンのエンティティメタ(<Entity>.entity.json)を出力 ---
        // S2JDBC の Service は BEAN を持たず、参照エンティティは戻り値ジェネリクスや
        // 別フォルダに存在するため、DAO 由来の登録だけでは辞書に載らない。
        // isEntityLike な全型を解析し、テーブル逆引き辞書を完全にする補完情報を出す。
        int entityCount = 0;
        for (final SourceRepository.TypeInfo info : repo.getAllTypes()) {
            if (!EntityAnalyzer.isEntityLike(info.decl)) {
                continue;
            }
            final EntityMeta em = entityAnalyzer.analyze(info);
            final File outFile = new File(outDir, info.simpleName + ".entity.json");
            writer.writeEntity(em, outFile);
            entityCount++;
        }
        if (entityCount > 0) {
            System.out.println("  エンティティメタ " + entityCount + " 件 → *.entity.json");
        }
        return count;
    }

    // ================= generate =================

    private static void generateCmd(final String[] args) throws Exception {
        File metaDir = null;
        File outDir = null;
        String pkg = null;
        String encoding = DEFAULT_ENCODING;
        for (int i = 1; i < args.length; i++) {
            final String a = args[i];
            if (a.equals("--meta") && i + 1 < args.length) {
                metaDir = new File(args[++i]);
            } else if (a.equals("--out") && i + 1 < args.length) {
                outDir = new File(args[++i]);
            } else if (a.equals("--package") && i + 1 < args.length) {
                pkg = args[++i];
            } else if (a.equals("--encoding") && i + 1 < args.length) {
                encoding = args[++i];
            } else if (a.equals("--dbms") && i + 1 < args.length) {
                ++i; // 生成側では方言は現状未使用(将来拡張用)
            } else {
                System.err.println("不明な引数: " + a);
                printUsage();
                System.exit(1);
                return;
            }
        }
        if (metaDir == null || outDir == null) {
            System.err.println("--meta と --out は必須です。");
            printUsage();
            System.exit(1);
            return;
        }
        final Charset charset = resolveCharset(encoding);
        final GenerationReport report = generate(metaDir, outDir, pkg, charset);
        System.out.println("出力エンコーディング: " + charset.name());
        System.out.println("完了: テストコードを生成しました → " + outDir.getAbsolutePath());
        System.out.println(report.toText());
    }

    /** メタ JSON ディレクトリからテストコードを生成する(UTF-8)。 */
    public static GenerationReport generate(final File metaDir, final File outDir,
            final String pkg) throws Exception {
        return generate(metaDir, outDir, pkg, java.nio.charset.Charset.forName(DEFAULT_ENCODING));
    }

    /** メタ JSON ディレクトリからテストコードを指定エンコーディングで生成する。 */
    public static GenerationReport generate(final File metaDir, final File outDir,
            final String pkg, final Charset charset) throws Exception {
        final MetaJsonReader reader = new MetaJsonReader();
        final TestClassGenerator gen = new TestClassGenerator();
        final GenerationReport report = new GenerationReport();
        outDir.mkdirs();

        final File[] files = metaDir.listFiles();
        if (files == null) {
            throw new IllegalArgumentException("メタディレクトリが読めません: " + metaDir);
        }
        // 決定的順序
        java.util.Arrays.sort(files, new java.util.Comparator<File>() {
            public int compare(File a, File b) {
                return a.getName().compareTo(b.getName());
            }
        });

        // 1パス目: 全 DAO のエンティティを収集(リレーション先の解決用レジストリ)
        final java.util.List<DaoMeta> daos = new java.util.ArrayList<DaoMeta>();
        for (int i = 0; i < files.length; i++) {
            final File f = files[i];
            if (!f.getName().endsWith(".meta.json")) {
                continue;
            }
            daos.add(reader.read(f));
        }
        // (a) 従来互換: simpleName(小文字)キー。既存のリレーション解決経路はこれを使う。
        final java.util.Map<String, Object> registry =
                new java.util.LinkedHashMap<String, Object>();
        // (b) 新規: tableName(大文字正規化)キー。テーブル名からエンティティを逆引きする。
        final java.util.Map<String, Object> byTable =
                new java.util.LinkedHashMap<String, Object>();
        for (final DaoMeta d : daos) {
            if (d.entity == null) {
                continue;
            }
            if (d.entity.simpleName != null) {
                registry.put(d.entity.simpleName.toLowerCase(java.util.Locale.ENGLISH),
                        d.entity);
            }
            if (d.entity.tableName != null && d.entity.tableName.trim().length() > 0) {
                byTable.put(d.entity.tableName.trim().toUpperCase(java.util.Locale.ENGLISH),
                        d.entity);
            }
        }
        // (c) 補完: スタンドアロンの *.entity.json を辞書へ登録する。
        //     DAO 由来(上の登録)を優先し、未登録キーのみ補う(putIfAbsent 相当)。
        //     これで Service(BEAN 無し)でも「対象テーブル → カラム/PK」を逆引きできる。
        for (int i = 0; i < files.length; i++) {
            final File f = files[i];
            if (!f.getName().endsWith(".entity.json")) {
                continue;
            }
            final EntityMeta em = reader.readEntity(f);
            if (em == null) {
                continue;
            }
            if (em.simpleName != null) {
                final String rk = em.simpleName.toLowerCase(java.util.Locale.ENGLISH);
                if (!registry.containsKey(rk)) {
                    registry.put(rk, em);
                }
            }
            if (em.tableName != null && em.tableName.trim().length() > 0) {
                final String tk = em.tableName.trim().toUpperCase(java.util.Locale.ENGLISH);
                if (!byTable.containsKey(tk)) {
                    byTable.put(tk, em);
                }
            }
        }
        gen.setEntityRegistry(registry);
        gen.setEntityByTable(byTable);

        for (final DaoMeta dao : daos) {
            final TestClassGenerator.Result r = gen.generate(dao, pkg, report);
            final File dir = (r.packageName != null && r.packageName.length() > 0)
                    ? new File(outDir, r.packageName.replace('.', '/')) : outDir;
            dir.mkdirs();
            final File out = new File(dir, r.className + ".java");
            writeText(out, r.source, charset);
            System.out.println("  " + dao.daoSimpleName + " → "
                    + (r.packageName != null ? r.packageName + "." : "") + r.className
                    + " (tests=" + r.testMethods + ", skipped=" + r.skipped + ")");
        }
        return report;
    }

    // ================= gen-all =================

    private static void genAllCmd(final String[] args) throws Exception {
        final List<File> srcDirs = new ArrayList<File>();
        final List<File> sqlDirs = new ArrayList<File>();
        File outDir = null;
        File metaDir = null;
        String pkg = null;
        String encoding = DEFAULT_ENCODING;
        Dialect dialect = Dialect.STANDARD;

        for (int i = 1; i < args.length; i++) {
            final String a = args[i];
            if (a.equals("--src") && i + 1 < args.length) {
                srcDirs.add(new File(args[++i]));
            } else if (a.equals("--sql") && i + 1 < args.length) {
                sqlDirs.add(new File(args[++i]));
            } else if (a.equals("--out") && i + 1 < args.length) {
                outDir = new File(args[++i]);
            } else if (a.equals("--meta") && i + 1 < args.length) {
                metaDir = new File(args[++i]);
            } else if (a.equals("--package") && i + 1 < args.length) {
                pkg = args[++i];
            } else if (a.equals("--encoding") && i + 1 < args.length) {
                encoding = args[++i];
            } else if (a.equals("--dbms") && i + 1 < args.length) {
                dialect = Dialect.fromString(args[++i]);
            } else {
                System.err.println("不明な引数: " + a);
                printUsage();
                System.exit(1);
                return;
            }
        }
        if (srcDirs.isEmpty() || outDir == null) {
            System.err.println("--src と --out は必須です。");
            printUsage();
            System.exit(1);
            return;
        }
        if (sqlDirs.isEmpty()) {
            sqlDirs.addAll(srcDirs);
        }
        if (metaDir == null) {
            metaDir = new File(outDir, "meta");
        }

        System.out.println("[1/2] 解析(analyze)...");
        final int n = analyze(srcDirs, sqlDirs, metaDir, dialect);
        System.out.println("  メタ JSON " + n + " 件 → " + metaDir.getAbsolutePath());
        System.out.println("[2/2] 生成(generate)...");
        final Charset charset = resolveCharset(encoding);
        final GenerationReport report = generate(metaDir, outDir, pkg, charset);
        System.out.println("出力エンコーディング: " + charset.name());
        System.out.println("完了: gen-all → " + outDir.getAbsolutePath());
        System.out.println(report.toText());
    }

    // ================= util =================

    /** 既定の出力エンコーディング。 */
    private static final String DEFAULT_ENCODING = "UTF-8";

    /**
     * エンコーディング名を {@link Charset} に解決する。
     * Pleiades/Eclipse(Windows・Java5 世代)向けに MS932 / Shift_JIS / Windows-31J を含む
     * JVM がサポートする任意の文字セットを指定できる。未知/未サポート名はエラー終了する。
     */
    private static Charset resolveCharset(final String encoding) {
        try {
            return Charset.forName(encoding);
        } catch (final Exception e) {
            System.err.println("サポートされていないエンコーディングです: " + encoding
                    + " (例: UTF-8, MS932, Shift_JIS, Windows-31J)");
            System.exit(1);
            throw new IllegalArgumentException(encoding, e);
        }
    }

    private static void writeText(final File out, final String content, final Charset charset)
            throws Exception {
        Writer w = null;
        try {
            w = new OutputStreamWriter(new FileOutputStream(out), charset);
            w.write(content);
        } finally {
            if (w != null) {
                w.close();
            }
        }
    }

    private static void printUsage() {
        System.out.println("使い方:");
        System.out.println("  analyze  --src <javaソースdir> --sql <sqldir> --out <出力dir> "
                + "[--dbms oracle|postgre|standard]");
        System.out.println("  generate --meta <metaJSONdir> --out <テスト出力dir> "
                + "[--package <pkg>] [--encoding <charset>] [--dbms oracle|postgre]");
        System.out.println("  gen-all  --src <javaソースdir> --sql <sqldir> --out <テスト出力dir> "
                + "[--meta <中間metadir>] [--package <pkg>] [--encoding <charset>] "
                + "[--dbms oracle|postgre]");
        System.out.println("    --encoding: 生成 .java の文字コード(既定 UTF-8。"
                + "Pleiades/旧Eclipse 向けは MS932 等)");
    }

    private Main() {
    }
}
