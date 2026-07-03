package com.example.s2daotestgen.cli;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.example.s2daotestgen.dao.DaoAnalyzer;
import com.example.s2daotestgen.dao.Dialect;
import com.example.s2daotestgen.dao.SourceRepository;
import com.example.s2daotestgen.json.JsonWriter;
import com.example.s2daotestgen.model.MetaModel.DaoMeta;
import com.example.s2daotestgen.sql.SqlFileIndex;

/**
 * フェーズ1 CLI。
 *
 * <pre>
 * java -jar s2dao-testgen.jar analyze --src &lt;javaソースdir&gt; --sql &lt;sqldir&gt; \
 *      --out &lt;出力dir&gt; [--dbms oracle|postgre|standard]
 * </pre>
 *
 * --src / --sql は同一ディレクトリでも可。両方とも再帰走査し、複数 DAO を一括処理する。
 */
public final class Main {

    public static void main(final String[] args) throws Exception {
        if (args.length == 0 || !args[0].equals("analyze")) {
            printUsage();
            System.exit(args.length == 0 ? 1 : 0);
            return;
        }
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

        final int count = run(srcDirs, sqlDirs, outDir, dialect);
        System.out.println("完了: " + count + " 件の DAO メタ JSON を出力しました → "
                + outDir.getAbsolutePath());
    }

    /** 解析本体。生成したメタ JSON 数を返す。 */
    public static int run(final List<File> srcDirs, final List<File> sqlDirs,
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
        return count;
    }

    private static void printUsage() {
        System.out.println("使い方:");
        System.out.println("  java -jar s2dao-testgen.jar analyze --src <javaソースdir> "
                + "--sql <sqldir> --out <出力dir> [--dbms oracle|postgre|standard]");
        System.out.println();
        System.out.println("  --src   DAO/エンティティ Java ソースのルート(複数指定可・再帰走査)");
        System.out.println("  --sql   2-way SQL(.sql)のルート(省略時は --src と同じ)");
        System.out.println("  --out   メタ JSON の出力先ディレクトリ");
        System.out.println("  --dbms  DB 方言(既定: standard)");
    }

    private Main() {
    }
}
