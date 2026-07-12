package com.example.s2daotestgen.gen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.s2daotestgen.model.MetaModel.ColumnBinding;
import com.example.s2daotestgen.model.MetaModel.DaoMeta;
import com.example.s2daotestgen.model.MetaModel.EntityMeta;
import com.example.s2daotestgen.model.MetaModel.MethodMeta;
import com.example.s2daotestgen.model.MetaModel.ParamMeta;
import com.example.s2daotestgen.model.MetaModel.PropertyMeta;
import com.example.s2daotestgen.model.MetaModel.SqlMeta;
import com.example.s2daotestgen.model.MetaModel.SqlStructure;

/**
 * {@link DaoMeta} 1 件から JUnit3 形式(Java5 互換構文)のテストクラスソースを生成する。
 *
 * <p>設計方針(DESIGN.md §3.3):</p>
 * <ul>
 *   <li>dao 単位に 1 テストクラス、メソッド単位に 1 テストメソッド</li>
 *   <li>setUp で S2TestContext 初期化・参照テーブルの DELETE・決定的テストデータ投入</li>
 *   <li>照合に関わる値(主キー・絞り込み条件・更新後値)は具体リテラルを埋め込み、
 *       埋め草カラムは ValueFactory(決定的)で生成</li>
 *   <li>PROCEDURE / 解析不能メソッドは // TODO を残してスキップ</li>
 * </ul>
 */
public final class TestClassGenerator {

    private static final String SUP = "com.example.s2daotestgen.support";
    private static final Pattern JOIN = Pattern.compile(
            "([A-Za-z_][A-Za-z0-9_]*)\\.([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*"
                    + "([A-Za-z_][A-Za-z0-9_]*)\\.([A-Za-z_][A-Za-z0-9_]*)");

    /**
     * エンティティ単純名(小文字)→ {@link EntityMeta}。
     * リレーション(_RELNO/@Relation)先エンティティの解決に使う全 DAO 横断レジストリ。
     * 未設定でも動作する(SQL の JOIN 検出のみに縮退)。
     */
    private Map entityRegistry = new LinkedHashMap();

    /**
     * テーブル名(大文字正規化)→ {@link EntityMeta}。
     * SQL の対象テーブルからエンティティのカラム/PK を逆引きするための辞書。
     * リレーション解決用の {@link #entityRegistry}(simpleName キー)とは別に保持する
     * (values() 汚染を避け、既存の生成ロジックに影響を与えない)。
     */
    private Map entityByTable = new LinkedHashMap();

    /** 全 DAO のエンティティレジストリを設定する(gen-all/generate 実行時に CLI が設定)。 */
    public void setEntityRegistry(Map registry) {
        this.entityRegistry = (registry != null) ? registry : new LinkedHashMap();
    }

    /** テーブル名逆引き辞書を設定する(キーは大文字正規化済みテーブル名)。 */
    public void setEntityByTable(Map registry) {
        this.entityByTable = (registry != null) ? registry : new LinkedHashMap();
    }

    /**
     * テーブル名から {@link EntityMeta} を逆引きする。大文字/小文字・前後空白のゆらぎを吸収する。
     * ステップ3(Service/S2JDBC 対応)が「SQL の対象テーブル → エンティティのカラム/PK」を
     * 解決するために使用する。未登録・null の場合は null を返す。
     */
    public EntityMeta lookupEntityByTable(String table) {
        if (table == null) {
            return null;
        }
        String key = table.trim().toUpperCase(java.util.Locale.ENGLISH);
        if (key.length() == 0) {
            return null;
        }
        return (EntityMeta) entityByTable.get(key);
    }

    private EntityMeta lookupEntity(String typeName) {
        if (typeName == null) {
            return null;
        }
        String t = typeName;
        int dot = t.lastIndexOf('.');
        if (dot >= 0) {
            t = t.substring(dot + 1);
        }
        return (EntityMeta) entityRegistry.get(t.toLowerCase(java.util.Locale.ENGLISH));
    }

    /** 1 クラス分の生成結果。 */
    public static final class Result {
        public String packageName;
        public String className;
        public String source;
        public int testMethods;
        public int skipped;
    }

    public Result generate(DaoMeta dao, String overridePackage, GenerationReport report) {
        Result r = "SERVICE".equals(dao.sourceKind)
                ? generateService(dao, overridePackage, report)
                : generateDao(dao, overridePackage, report);
        // ブロック先頭コメントの手前に空行を入れて可読性を上げる(生成物共通の整形)
        r.source = separateBlockComments(r.source);
        r.source = separateCatchFinally(r.source);
        return r;
    }

    /**
     * 行頭コメント(ブロックの先頭に置かれる {@code // ...} コメント)の直前に空行を 1 行挿入する。
     *
     * <p>直前行がすでに空行・行頭コメント・ブロック開始({@code {} で終わる行)のいずれかの場合は
     * 挿入しない(空行の重複や、波括弧直後の不自然な空行を避ける)。行末尾に付くインラインコメント
     * (コード + {@code // ...})や javadoc({@code /** ... *}{@code /})は対象外。</p>
     */
    public static String separateBlockComments(String source) {
        String[] lines = source.split("\n", -1);
        StringBuffer out = new StringBuffer();
        String prev = null;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (trimmed.startsWith("//") && prev != null) {
                String pt = prev.trim();
                boolean prevBlank = pt.length() == 0;
                boolean prevComment = pt.startsWith("//");
                boolean prevOpensBlock = pt.endsWith("{");
                if (!prevBlank && !prevComment && !prevOpensBlock) {
                    out.append("\n");
                }
            }
            out.append(line);
            if (i < lines.length - 1) {
                out.append("\n");
            }
            prev = line;
        }
        return out.toString();
    }

    /**
     * 「<code>} catch (...) {</code>」「<code>} finally {</code>」行の直前に空行を 1 行挿入する
     * (try ブロック末尾と例外処理/後始末の視覚的な区切り。可読性向上目的)。
     *
     * <p>直前行がすでに空行、または波括弧開始で終わる行(= try 本体が空)の場合は挿入しない。</p>
     */
    public static String separateCatchFinally(String source) {
        String[] lines = source.split("\n", -1);
        StringBuffer out = new StringBuffer();
        String prev = null;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if ((trimmed.startsWith("} catch") || trimmed.startsWith("} finally"))
                    && prev != null) {
                String pt = prev.trim();
                boolean prevBlank = pt.length() == 0;
                boolean prevOpensBlock = pt.endsWith("{");
                if (!prevBlank && !prevOpensBlock) {
                    out.append("\n");
                }
            }
            out.append(line);
            if (i < lines.length - 1) {
                out.append("\n");
            }
            prev = line;
        }
        return out.toString();
    }

    private Result generateDao(DaoMeta dao, String overridePackage, GenerationReport report) {
        String pkg = (overridePackage != null && overridePackage.length() > 0)
                ? overridePackage : nz(dao.packageName);
        String daoFq = fqDao(dao);
        String className = dao.daoSimpleName + "Test";

        // --- メソッドの選別(生成 or スキップ) ---
        List genMethods = new ArrayList(); // MethodMeta
        List skipComments = new ArrayList(); // String
        for (int i = 0; i < dao.methods.size(); i++) {
            MethodMeta m = (MethodMeta) dao.methods.get(i);
            String reason = skipReason(dao, m);
            if (reason == null) {
                genMethods.add(m);
            } else {
                skipComments.add("    // TODO: テスト未生成(スキップ) " + m.name + " : " + reason);
                report.addSkip(dao.daoSimpleName, m.name, reason);
            }
        }

        // --- 参照テーブルの収集 ---
        EntityMeta entity = dao.entity;
        String entityTable = (entity != null) ? entity.tableName : null;
        List refTables = collectTables(genMethods); // upperで一意化された表示用名
        List parentTables = new ArrayList();
        for (int i = 0; i < refTables.size(); i++) {
            String t = (String) refTables.get(i);
            if (entityTable == null || !t.equalsIgnoreCase(entityTable)) {
                parentTables.add(t);
            }
        }

        Set constrained = constrainedColumns(dao, genMethods);
        Set fkCols = fkColumns(entity, genMethods, entityTable);
        List relationParents = relationParentEntities(entity, entityTable);

        StringBuffer sb = new StringBuffer();
        emitHeader(sb, pkg, className, daoFq);
        emitSetUp(sb, dao, daoFq, entity, entityTable, parentTables, relationParents,
                genMethods, constrained);
        emitTearDown(sb);

        int testCount = 0;
        for (int i = 0; i < genMethods.size(); i++) {
            MethodMeta m = (MethodMeta) genMethods.get(i);
            emitTestMethod(sb, dao, entity, entityTable, m, fkCols);
            testCount++;
        }
        // スキップコメント
        for (int i = 0; i < skipComments.size(); i++) {
            sb.append(skipComments.get(i)).append("\n");
        }
        // 生成可能メソッドが 0 件ならプレースホルダ(JUnit3 の「テスト無し」失敗回避)
        if (testCount == 0) {
            sb.append("\n    /** このDAOには自動生成可能なテストメソッドがありません(上記スキップ参照)。 */\n");
            sb.append("    public void testNoGeneratableMethods() throws Exception {\n");
            sb.append("        assertTrue(true);\n");
            sb.append("    }\n");
        }
        sb.append("}\n");

        Result r = new Result();
        r.packageName = pkg;
        r.className = className;
        r.source = sb.toString();
        r.testMethods = testCount;
        r.skipped = skipComments.size();

        GenerationReport.ClassSummary cs = new GenerationReport.ClassSummary();
        cs.daoSimpleName = dao.daoSimpleName;
        cs.testMethods = testCount;
        cs.skipped = skipComments.size();
        report.classSummaries.add(cs);
        report.classes++;
        report.testMethods += testCount;
        return r;
    }

    // =========================================================================
    // ================= S2JDBC Service 経路(ステップ3) =======================
    // =========================================================================
    //
    // S2JDBC の Service は具象クラスで、BEAN(エンティティ)を持たず JdbcManager へ
    // 委譲する。dao.entity は null なので DAO 経路の setUp シード(BEAN 前提)は使えない。
    // そこで Service 専用に、各テストメソッド内で「SQL の対象テーブル → エンティティ
    // (テーブル逆引き辞書 lookupEntityByTable)」を引いて決定的データを投入し、実行後に
    // 検証する。DAO 経路の生成物は一切変更しない(このブロックは完全に独立)。

    private Result generateService(DaoMeta dao, String overridePackage, GenerationReport report) {
        String pkg = (overridePackage != null && overridePackage.length() > 0)
                ? overridePackage : nz(dao.packageName);
        String svcFq = fqDao(dao);
        String className = dao.daoSimpleName + "Test";

        List genMethods = new ArrayList(); // MethodMeta
        List skipComments = new ArrayList(); // String
        for (int i = 0; i < dao.methods.size(); i++) {
            MethodMeta m = (MethodMeta) dao.methods.get(i);
            String reason = serviceSkipReason(m);
            if (reason == null) {
                genMethods.add(m);
            } else {
                skipComments.add("    // TODO: テスト未生成(スキップ) " + m.name + " : " + reason);
                report.addSkip(dao.daoSimpleName, m.name, reason);
            }
        }

        StringBuffer sb = new StringBuffer();
        emitHeader(sb, pkg, className, svcFq);
        // setUp: コンテナから Service を取得するのみ(テーブル投入は各テスト内で行う)。
        sb.append("    protected void setUp() throws Exception {\n");
        sb.append("        super.setUp();\n");
        if (genMethods.isEmpty()) {
            sb.append("        // 自動生成可能なテストメソッドが無いため、コンテナ/DB は初期化しない\n");
            sb.append("    }\n\n");
        } else {
            sb.append("        ctx = new S2TestContext();\n");
            sb.append("        dao = (").append(svcFq).append(") ctx.getComponent(")
              .append(svcFq).append(".class);\n");
            sb.append("    }\n\n");
        }
        emitTearDown(sb);

        int testCount = 0;
        for (int i = 0; i < genMethods.size(); i++) {
            MethodMeta m = (MethodMeta) genMethods.get(i);
            emitServiceTestMethod(sb, dao, m);
            testCount++;
        }
        for (int i = 0; i < skipComments.size(); i++) {
            sb.append(skipComments.get(i)).append("\n");
        }
        if (testCount == 0) {
            sb.append("\n    /** この Service には自動生成可能なテストメソッドがありません(上記スキップ参照)。 */\n");
            sb.append("    public void testNoGeneratableMethods() throws Exception {\n");
            sb.append("        assertTrue(true);\n");
            sb.append("    }\n");
        }
        sb.append("}\n");

        Result r = new Result();
        r.packageName = pkg;
        r.className = className;
        r.source = sb.toString();
        r.testMethods = testCount;
        r.skipped = skipComments.size();

        GenerationReport.ClassSummary cs = new GenerationReport.ClassSummary();
        cs.daoSimpleName = dao.daoSimpleName;
        cs.testMethods = testCount;
        cs.skipped = skipComments.size();
        report.classSummaries.add(cs);
        report.classes++;
        report.testMethods += testCount;
        return r;
    }

    /** Service メソッドの生成可否。Map/ジェネリクス引数は許容する(DAO 経路との違い)。 */
    private String serviceSkipReason(MethodMeta m) {
        String kind = nz(m.methodKind);
        if (kind.equals("PROCEDURE")) {
            return "ストアドプロシージャ(PROCEDURE)は未対応";
        }
        if (m.sql == null) {
            return "SQL が解決できていない";
        }
        if ("UNRESOLVED".equals(m.sql.resolutionType)) {
            return "SQL 未解決(UNRESOLVED)";
        }
        SqlStructure st = m.sql.structure;
        if (st == null || st.tables == null || st.tables.isEmpty()) {
            return "対象テーブルを特定できない(DDL 等・データ組立不能)";
        }
        return null;
    }

    private String primaryTable(MethodMeta m) {
        SqlStructure st = (m.sql != null) ? m.sql.structure : null;
        if (st == null || st.tables == null || st.tables.isEmpty()) {
            return null;
        }
        return (String) st.tables.get(0);
    }

    private void emitServiceTestMethod(StringBuffer sb, DaoMeta dao, MethodMeta m) {
        String kind = nz(m.methodKind);
        sb.append("    /** ").append(m.name).append(" : ").append(kind)
          .append(" (").append(nz(m.sql != null ? m.sql.resolutionType : ""))
          .append(", S2JDBC Service) */\n");
        sb.append("    public void test").append(cap(m.name)).append("() throws Exception {\n");
        sb.append("        java.sql.Connection conn = ctx.getConnection();\n");
        sb.append("        try {\n");
        sb.append("            EvidenceWriter ev = ctx.newEvidenceWriter();\n");

        if (kind.equals("SELECT")) {
            emitServiceSelect(sb, dao, m);
        } else {
            emitServiceMutation(sb, dao, m, kind);
        }

        sb.append("        } finally {\n");
        sb.append("            conn.close();\n");
        sb.append("        }\n");
        sb.append("    }\n\n");
    }

    // ---- Service SELECT(find/get: List<Entity> 等) ----

    private void emitServiceSelect(StringBuffer sb, DaoMeta dao, MethodMeta m) {
        String table = primaryTable(m);
        EntityMeta te = lookupEntityByTable(table);
        // 戻り値ジェネリクスの型引数(List<Emp> → Emp)から結果エンティティを解決。無ければ対象表。
        EntityMeta resultEntity = lookupEntity(genericArg(m.returnType));
        if (resultEntity == null) {
            resultEntity = lookupEntity(m.returnType);
        }
        if (resultEntity == null) {
            resultEntity = te;
        }

        // WHERE '=' で束縛されるカラム集合(投入時に固定値を使う対象)
        Set whereCols = whereEqColumns(m);

        // 対象テーブルへ決定的データ投入(全永続カラムを BASE 値で 1 行)
        if (te != null) {
            sb.append("            // --- 対象テーブル ").append(table)
              .append(" へ決定的データ投入 ---\n");
            sb.append("            WriteDbUtil.deleteAll(conn, \"").append(table).append("\");\n");
            emitServiceSeedRow(sb, te);
        } else {
            sb.append("            // 対象テーブルのエンティティメタが辞書に無いため投入は省略\n");
        }

        // 引数準備
        List argVars = emitServiceArgs(sb, dao, m, te, whereCols);

        // 実行
        String ret = resolveReturnType(m.returnType, dao.packageName);
        boolean isVoid = ret.equals("void");
        boolean isCollection = ret.equals("java.util.List") || ret.equals("java.util.Collection");
        sb.append("            // --- Service 実行 ---\n");
        String callExpr = "dao." + m.name + "(" + join(argVars) + ")";
        if (isVoid) {
            sb.append("            ").append(callExpr).append(";\n");
        } else {
            sb.append("            ").append(ret).append(" result = ").append(callExpr).append(";\n");
        }

        // 戻り値 assert(find は強め: 件数 + 先頭行の主キー)
        sb.append("            // --- 戻り値 assert ---\n");
        boolean expectHit = te != null && serviceExpectHit(m);
        if (!isVoid) {
            if (isCollection) {
                sb.append("            assertNotNull(result);\n");
                if (expectHit) {
                    sb.append("            assertTrue(\"1 件以上ヒットするはず\", result.size() >= 1);\n");
                    emitServiceElementAssert(sb, resultEntity);
                }
            } else if (ret.endsWith("[]")) {
                sb.append("            assertNotNull(result);\n");
                if (expectHit) {
                    sb.append("            assertTrue(\"1 件以上ヒットするはず\", result.length >= 1);\n");
                }
            } else {
                if (expectHit) {
                    sb.append("            assertNotNull(\"該当行が取得できるはず\", result);\n");
                } else {
                    sb.append("            // 条件次第で null になりうるためエビデンス出力のみ\n");
                }
            }
            sb.append("            ev.writeReturn(\"").append(dao.daoSimpleName).append("\", \"")
              .append(m.name).append("\", ").append(boxResult(ret, "result")).append(");\n");
        } else {
            sb.append("            // 戻り値なし(void)\n");
        }

        // データセット + エビデンス
        emitServiceDataset(sb, dao, m, te, table);
    }

    /** 先頭要素の主キーが投入値(BASE)と一致することを assert する。 */
    private void emitServiceElementAssert(StringBuffer sb, EntityMeta resultEntity) {
        if (resultEntity == null || resultEntity.primaryKeyColumns.isEmpty()) {
            return;
        }
        String pkCol = (String) resultEntity.primaryKeyColumns.get(0);
        PropertyMeta pk = propByColumn(resultEntity, pkCol);
        if (pk == null || resultEntity.className == null) {
            return;
        }
        String fq = resultEntity.className;
        TestValues.Value v = TestValues.of(pk.javaType, pk.columnName, TestValues.BASE);
        sb.append("            ").append(fq).append(" row0 = (").append(fq)
          .append(") result.get(0);\n");
        sb.append("            assertEquals(\"先頭行の主キーが投入値と一致\", ")
          .append(boxForType(pk.javaType, v.expr)).append(", ")
          .append(boxGetter(pk.javaType, "row0.get" + cap(pk.propertyName) + "()")).append(");\n");
    }

    // ---- Service mutation(INSERT/UPDATE/DELETE: void/int, Map 入力) ----

    private void emitServiceMutation(StringBuffer sb, DaoMeta dao, MethodMeta m, String op) {
        String table = primaryTable(m);
        EntityMeta te = lookupEntityByTable(table);

        MutInfo info = new MutInfo();
        if (te != null) {
            sb.append("            // --- 対象テーブル ").append(table).append(" の準備(")
              .append(op).append(") ---\n");
            sb.append("            WriteDbUtil.deleteAll(conn, \"").append(table).append("\");\n");
            if (!op.equals("INSERT")) {
                // UPDATE/DELETE は対象行を投入してから実行する(BASE 値の完全な 1 行)
                emitServiceSeedRow(sb, te);
            }
            info = emitMutationMap(sb, m, te, op);
        } else {
            sb.append("            // 対象テーブルのエンティティメタが辞書に無いため、")
              .append("SQL のバインドキーのみで Map を構築(安全フォールバック)\n");
            info = emitMutationMap(sb, m, null, op);
        }

        // 呼び出し引数: Map 型パラメータには構築済み Map、スカラは決定的値、その他は null。
        List callArgs = new ArrayList();
        for (int i = 0; i < m.parameters.size(); i++) {
            ParamMeta p = (ParamMeta) m.parameters.get(i);
            String cat = typeCategory(p.type);
            if (cat.equals("GENERIC") && isMapType(p.type)) {
                callArgs.add(info.mapVar);
            } else if (cat.equals("SCALAR")) {
                callArgs.add(emitScalarArg(sb, dao, te, m, p, i));
            } else if (cat.equals("BEAN")) {
                callArgs.add(emitDtoArg(sb, dao, te, m, p));
            } else {
                String var = safeVar(p.name);
                sb.append("            ").append(resolveType(p.type, dao.packageName))
                  .append(" ").append(var).append(" = null; // 未対応型のため null\n");
                callArgs.add(var);
            }
        }

        String ret = resolveReturnType(m.returnType, dao.packageName);
        boolean isVoid = ret.equals("void");
        sb.append("            // --- Service 実行 ---\n");
        String callExpr = "dao." + m.name + "(" + join(callArgs) + ")";
        if (isVoid) {
            sb.append("            ").append(callExpr).append(";\n");
        } else {
            sb.append("            ").append(ret).append(" result = ").append(callExpr).append(";\n");
            if (ret.equals("int") || ret.equals("long")) {
                sb.append("            assertTrue(\"更新/削除/登録 件数は 1 以上\", result >= 1);\n");
            }
            sb.append("            ev.writeReturn(\"").append(dao.daoSimpleName).append("\", \"")
              .append(m.name).append("\", ").append(boxResult(ret, "result")).append(");\n");
        }

        // データセット取得 + 変更 assert
        emitServiceDataset(sb, dao, m, te, table);
        if (te != null && table != null && info.pkColUpper != null) {
            String dsVar = "ds_" + safeVar(table);
            if (op.equals("INSERT")) {
                sb.append("            assertNotNull(\"新規行が登録されていること\", GetDatasetUtil.find(")
                  .append(dsVar).append(", \"").append(info.pkColUpper).append("\", ")
                  .append(info.pkExpr).append("));\n");
            } else if (op.equals("DELETE")) {
                sb.append("            assertNull(\"対象行が削除されていること\", GetDatasetUtil.find(")
                  .append(dsVar).append(", \"").append(info.pkColUpper).append("\", ")
                  .append(info.pkExpr).append("));\n");
            } else if (op.equals("UPDATE")) {
                sb.append("            java.util.Map updated = GetDatasetUtil.find(")
                  .append(dsVar).append(", \"").append(info.pkColUpper).append("\", ")
                  .append(info.pkExpr).append(");\n");
                sb.append("            assertNotNull(\"対象行が存在すること\", updated);\n");
                if (info.assertColUpper != null) {
                    sb.append("            assertEquals(\"更新後の値が反映されていること\", EvidenceWriter.normalize(")
                      .append(info.assertColExpr).append("), EvidenceWriter.normalize(updated.get(\"")
                      .append(info.assertColUpper).append("\")));\n");
                }
            }
        }
    }

    /** mutation の Map 入力構築で得た検証用情報。 */
    private static final class MutInfo {
        String mapVar = "arg";
        String pkColUpper;   // 対象テーブル主キーのカラム名(大文字)
        String pkExpr;       // 主キーの値式(INSERT=投入PK, UPDATE/DELETE=BASE)
        String assertColUpper; // UPDATE で変化を検証する SET カラム(大文字)
        String assertColExpr;  // その変化後(ALT)値式
    }

    /**
     * SQL の bindVariables から Map 入力を組み立てるコードを出力する。
     * WHERE '=' 束縛キーは対象行に一致する BASE 値、SET キーは UPDATE で ALT 値(変化観測用)。
     */
    private MutInfo emitMutationMap(StringBuffer sb, MethodMeta m, EntityMeta te, String op) {
        MutInfo info = new MutInfo();
        Set whereBinds = whereBindExpressions(m);
        // 主キーカラム
        String pkCol = (te != null && !te.primaryKeyColumns.isEmpty())
                ? (String) te.primaryKeyColumns.get(0) : null;

        sb.append("            // --- 入力 Map 構築(SQL の /*key*/ バインドに対応) ---\n");
        sb.append("            java.util.Map ").append(info.mapVar)
          .append(" = new java.util.HashMap();\n");

        List binds = distinctBindRoots(m);
        for (int i = 0; i < binds.size(); i++) {
            String key = (String) binds.get(i);
            boolean isFilter = whereBinds.contains(key);
            // キー → カラム/型 の解決
            String col = columnForBind(m, te, key);
            PropertyMeta prop = (te != null) ? propByColumn(te, col) : null;
            String javaType = (prop != null) ? prop.javaType : guessType(col);
            boolean isPk = pkCol != null && TestValues.canonical(pkCol)
                    .equals(TestValues.canonical(col));
            boolean isVer = prop != null && prop.versionNo;
            boolean isTs = prop != null && prop.timestamp;
            int variant;
            if (op.equals("UPDATE") && !isFilter && !isPk && !isVer && !isTs) {
                variant = TestValues.ALT; // SET 対象列は変化させる
            } else {
                variant = TestValues.BASE; // フィルタ/PK/楽観ロック/INSERT/DELETE は BASE
            }
            TestValues.Value v = TestValues.of(javaType, col, variant);
            sb.append("            ").append(info.mapVar).append(".put(\"").append(key)
              .append("\", ").append(v.expr).append("); // ").append(col).append("=")
              .append(v.display);
            if (isFilter) {
                sb.append(" (WHERE 束縛)");
            }
            sb.append("\n");

            if (isPk) {
                info.pkColUpper = col.toUpperCase(java.util.Locale.ENGLISH);
                info.pkExpr = TestValues.of(javaType, col,
                        op.equals("INSERT") ? TestValues.BASE : TestValues.BASE).expr;
            }
            if (op.equals("UPDATE") && info.assertColUpper == null
                    && !isFilter && !isPk && !isVer && !isTs) {
                info.assertColUpper = col.toUpperCase(java.util.Locale.ENGLISH);
                info.assertColExpr = v.expr;
            }
        }
        return info;
    }

    /** 全永続カラムを BASE 値で 1 行投入する(Service の対象テーブルシード)。 */
    private void emitServiceSeedRow(StringBuffer sb, EntityMeta te) {
        List cols = new ArrayList();
        List vals = new ArrayList();
        List notes = new ArrayList();
        for (int i = 0; i < te.properties.size(); i++) {
            PropertyMeta p = (PropertyMeta) te.properties.get(i);
            if (!p.persistent) {
                continue;
            }
            TestValues.Value v = TestValues.of(p.javaType, p.columnName, TestValues.BASE);
            cols.add(p.columnName);
            vals.add(v.expr);
            notes.add(p.columnName + "=" + v.display);
        }
        emitInsert(sb, te.tableName, cols, vals, notes);
    }

    private void emitServiceDataset(StringBuffer sb, DaoMeta dao, MethodMeta m,
            EntityMeta te, String table) {
        sb.append("            // --- 操作後データセット取得 + エビデンス出力 ---\n");
        List tables = (m.sql != null && m.sql.structure != null)
                ? m.sql.structure.tables : new ArrayList();
        Set done = new LinkedHashSet();
        for (int i = 0; i < tables.size(); i++) {
            String t = (String) tables.get(i);
            String up = t.toUpperCase(java.util.Locale.ENGLISH);
            if (done.contains(up)) {
                continue;
            }
            done.add(up);
            EntityMeta ent = (t.equalsIgnoreCase(table)) ? te : lookupEntityByTable(t);
            String orderBy = orderByFor(ent, t, t);
            String dsVar = "ds_" + safeVar(t);
            sb.append("            java.util.List ").append(dsVar)
              .append(" = GetDatasetUtil.getDataset(conn, \"").append(t).append("\"")
              .append(orderBy).append(");\n");
            sb.append("            ev.writeDataset(\"").append(dao.daoSimpleName).append("\", \"")
              .append(m.name).append("\", \"").append(t).append("\", ").append(dsVar).append(");\n");
        }
        if (done.isEmpty()) {
            sb.append("            // 対象テーブル不明のためデータセット出力なし\n");
        }
    }

    // ---- Service 用 引数生成 ----

    private List emitServiceArgs(StringBuffer sb, DaoMeta dao, MethodMeta m,
            EntityMeta te, Set whereCols) {
        List vars = new ArrayList();
        if (!m.parameters.isEmpty()) {
            sb.append("            // --- 引数準備(投入データにヒットする決定的値) ---\n");
        }
        for (int i = 0; i < m.parameters.size(); i++) {
            ParamMeta p = (ParamMeta) m.parameters.get(i);
            String cat = typeCategory(p.type);
            if (cat.equals("GENERIC") && isMapType(p.type)) {
                vars.add(emitServiceMapArg(sb, m, te, p));
            } else if (cat.equals("SCALAR")) {
                vars.add(emitScalarArg(sb, dao, te, m, p, i));
            } else if (cat.equals("BEAN")) {
                vars.add(emitDtoArg(sb, dao, te, m, p));
            } else {
                // その他(配列/未知ジェネリクス): null を渡す安全フォールバック
                String var = safeVar(p.name);
                String decl = resolveType(p.type, dao.packageName);
                sb.append("            ").append(decl).append(" ").append(var)
                  .append(" = null; // 未対応型のため null\n");
                vars.add(var);
            }
        }
        return vars;
    }

    /** SELECT の Map 引数: WHERE '=' 束縛キーに投入値(BASE)を詰める。 */
    private String emitServiceMapArg(StringBuffer sb, MethodMeta m, EntityMeta te, ParamMeta p) {
        String var = safeVar(p.name);
        sb.append("            java.util.Map ").append(var)
          .append(" = new java.util.HashMap();\n");
        Set whereBinds = whereBindExpressions(m);
        List binds = distinctBindRoots(m);
        for (int i = 0; i < binds.size(); i++) {
            String key = (String) binds.get(i);
            String col = columnForBind(m, te, key);
            PropertyMeta prop = (te != null) ? propByColumn(te, col) : null;
            String javaType = (prop != null) ? prop.javaType : guessType(col);
            TestValues.Value v = TestValues.of(javaType, col, TestValues.BASE);
            sb.append("            ").append(var).append(".put(\"").append(key)
              .append("\", ").append(v.expr).append("); // ").append(col).append("=")
              .append(v.display);
            if (whereBinds.contains(key)) {
                sb.append(" (WHERE 束縛にヒット)");
            }
            sb.append("\n");
        }
        return var;
    }

    // ---- Service 用 ヘルパ ----

    private boolean isMapType(String type) {
        String t = nz(type).trim();
        int lt = t.indexOf('<');
        if (lt >= 0) {
            t = t.substring(0, lt).trim();
        }
        int dot = t.lastIndexOf('.');
        if (dot >= 0) {
            t = t.substring(dot + 1);
        }
        return t.equals("Map") || t.equals("HashMap") || t.equals("LinkedHashMap")
                || t.equals("TreeMap") || t.equals("SortedMap");
    }

    /** 戻り値型のジェネリクス型引数(先頭)を単純名で返す。無ければ null。 */
    private String genericArg(String type) {
        String t = nz(type);
        int lt = t.indexOf('<');
        int gt = t.lastIndexOf('>');
        if (lt < 0 || gt < 0 || gt <= lt + 1) {
            return null;
        }
        String inner = t.substring(lt + 1, gt).trim();
        int comma = inner.indexOf(',');
        if (comma >= 0) {
            inner = inner.substring(0, comma).trim();
        }
        int inLt = inner.indexOf('<');
        if (inLt >= 0) {
            inner = inner.substring(0, inLt).trim();
        }
        int dot = inner.lastIndexOf('.');
        if (dot >= 0) {
            inner = inner.substring(dot + 1);
        }
        return inner.length() > 0 ? inner : null;
    }

    /** SQL バインドの root 名を出現順・重複排除で返す。 */
    private List distinctBindRoots(MethodMeta m) {
        List out = new ArrayList();
        Set seen = new LinkedHashSet();
        if (m.sql == null) {
            return out;
        }
        for (int i = 0; i < m.sql.bindVariables.size(); i++) {
            com.example.s2daotestgen.model.MetaModel.BindVarMeta b =
                    (com.example.s2daotestgen.model.MetaModel.BindVarMeta) m.sql.bindVariables.get(i);
            String root = nz(b.rootParam);
            if (root.length() == 0) {
                root = nz(b.expression);
            }
            if (root.length() == 0 || seen.contains(root)) {
                continue;
            }
            seen.add(root);
            out.add(root);
        }
        return out;
    }

    /** WHERE 句でバインドされている式(bindExpression)の集合。 */
    private Set whereBindExpressions(MethodMeta m) {
        Set s = new LinkedHashSet();
        SqlStructure st = (m.sql != null) ? m.sql.structure : null;
        if (st == null) {
            return s;
        }
        for (int i = 0; i < st.whereBindings.size(); i++) {
            ColumnBinding wb = (ColumnBinding) st.whereBindings.get(i);
            if (wb.bindExpression != null) {
                s.add(wb.bindExpression);
            }
        }
        return s;
    }

    /** WHERE '=' で束縛されるカラム(正準化)集合。 */
    private Set whereEqColumns(MethodMeta m) {
        Set s = new LinkedHashSet();
        SqlStructure st = (m.sql != null) ? m.sql.structure : null;
        if (st == null) {
            return s;
        }
        for (int i = 0; i < st.whereBindings.size(); i++) {
            ColumnBinding wb = (ColumnBinding) st.whereBindings.get(i);
            if ("=".equals(wb.operator)) {
                s.add(TestValues.canonical(wb.column));
            }
        }
        return s;
    }

    /** バインドキーに対応するカラム名を解決する(WHERE 束縛→エンティティ照合→キー名)。 */
    private String columnForBind(MethodMeta m, EntityMeta te, String key) {
        SqlStructure st = (m.sql != null) ? m.sql.structure : null;
        if (st != null) {
            for (int i = 0; i < st.whereBindings.size(); i++) {
                ColumnBinding wb = (ColumnBinding) st.whereBindings.get(i);
                if (key.equals(wb.bindExpression) && wb.column != null) {
                    return wb.column;
                }
            }
        }
        if (te != null) {
            for (int i = 0; i < te.properties.size(); i++) {
                PropertyMeta p = (PropertyMeta) te.properties.get(i);
                if (p.propertyName.equals(key)
                        || TestValues.canonical(p.columnName).equals(TestValues.canonical(key))) {
                    return p.columnName;
                }
            }
        }
        return key;
    }

    /** エンティティ不明時のカラム型推定(数値っぽければ int、それ以外は String)。 */
    private String guessType(String col) {
        String canon = TestValues.canonical(col);
        if (canon.endsWith("no") || canon.equals("id") || canon.endsWith("id")) {
            return "int";
        }
        return "String";
    }

    /** Service SELECT のヒット保証(スカラ/Map 引数が全て '=' WHERE で束縛されるか)。 */
    private boolean serviceExpectHit(MethodMeta m) {
        SqlStructure st = (m.sql != null) ? m.sql.structure : null;
        if (st == null || st.whereBindings.isEmpty()) {
            // WHERE が無い = 全件。投入行があるので 1 件以上ヒットする。
            return st != null;
        }
        for (int i = 0; i < st.whereBindings.size(); i++) {
            ColumnBinding wb = (ColumnBinding) st.whereBindings.get(i);
            if (!"=".equals(wb.operator)) {
                return false;
            }
        }
        return true;
    }

    /** assertEquals の expected 側: プリミティブ数値式はボックス化して Object 化する。 */
    private String boxForType(String javaType, String expr) {
        // TestValues.of は数値/文字列とも既にボックス式(Integer.valueOf 等)を返すためそのまま。
        return expr;
    }

    /** getter 戻り値をボックス化して assertEquals(Object,Object) に渡せるようにする。 */
    private String boxGetter(String javaType, String getterCall) {
        String t = javaType == null ? "" : javaType.trim();
        int dot = t.lastIndexOf('.');
        if (dot >= 0) {
            t = t.substring(dot + 1);
        }
        if (t.equals("int")) {
            return "Integer.valueOf(" + getterCall + ")";
        }
        if (t.equals("long")) {
            return "Long.valueOf(" + getterCall + ")";
        }
        if (t.equals("short")) {
            return "Short.valueOf(" + getterCall + ")";
        }
        if (t.equals("byte")) {
            return "Byte.valueOf(" + getterCall + ")";
        }
        if (t.equals("float")) {
            return "Float.valueOf(" + getterCall + ")";
        }
        if (t.equals("double")) {
            return "Double.valueOf(" + getterCall + ")";
        }
        if (t.equals("boolean")) {
            return "Boolean.valueOf(" + getterCall + ")";
        }
        if (t.equals("char")) {
            return "Character.valueOf(" + getterCall + ")";
        }
        return getterCall;
    }

    // ================= ヘッダ / setUp / tearDown =================

    private void emitHeader(StringBuffer sb, String pkg, String className, String daoFq) {
        if (pkg != null && pkg.length() > 0) {
            sb.append("package ").append(pkg).append(";\n\n");
        }
        sb.append("import junit.framework.TestCase;\n");
        sb.append("import ").append(SUP).append(".S2TestContext;\n");
        sb.append("import ").append(SUP).append(".TestDataParam;\n");
        sb.append("import ").append(SUP).append(".WriteDbUtil;\n");
        sb.append("import ").append(SUP).append(".GetDatasetUtil;\n");
        sb.append("import ").append(SUP).append(".EvidenceWriter;\n");
        sb.append("import ").append(SUP).append(".ValueFactory;\n\n");
        sb.append("/**\n");
        sb.append(" * ").append(daoFq).append(" の自動生成 JUnit3 テスト。\n");
        sb.append(" * s2dao-testgen フェーズ2 が生成。Java5 互換構文のみ。\n");
        sb.append(" */\n");
        sb.append("public class ").append(className).append(" extends TestCase {\n\n");
        sb.append("    private S2TestContext ctx;\n");
        sb.append("    private ").append(daoFq).append(" dao;\n\n");
    }

    private void emitSetUp(StringBuffer sb, DaoMeta dao, String daoFq, EntityMeta entity,
            String entityTable, List parentTables, List relationParents,
            List genMethods, Set constrained) {
        sb.append("    protected void setUp() throws Exception {\n");
        sb.append("        super.setUp();\n");
        // 生成可能なテストメソッドが 0 件のプレースホルダクラスでは、
        // S2Container・DB 接続を用意しない(getComponent が bean 解決不能で失敗するのを避ける)。
        if (genMethods.isEmpty()) {
            sb.append("        // 自動生成可能なテストメソッドが無いため、コンテナ/DB は初期化しない\n");
            sb.append("    }\n\n");
            return;
        }
        sb.append("        ctx = new S2TestContext();\n");
        sb.append("        dao = (").append(daoFq).append(") ctx.getComponent(")
          .append(daoFq).append(".class);\n");

        // SQL 由来の親テーブル + リレーション(_RELNO/@Relation)由来の親テーブルを統合
        List allParents = new ArrayList(parentTables);
        Set parentUpper = new LinkedHashSet();
        for (int i = 0; i < parentTables.size(); i++) {
            parentUpper.add(((String) parentTables.get(i)).toUpperCase(java.util.Locale.ENGLISH));
        }
        for (int i = 0; i < relationParents.size(); i++) {
            EntityMeta rp = (EntityMeta) relationParents.get(i);
            String up = rp.tableName.toUpperCase(java.util.Locale.ENGLISH);
            if (!parentUpper.contains(up)) {
                parentUpper.add(up);
                allParents.add(rp.tableName);
            }
        }

        boolean hasTables = entityTable != null || !allParents.isEmpty();
        if (!hasTables) {
            sb.append("        // 参照テーブルが特定できないため投入は行わない\n");
            sb.append("    }\n\n");
            return;
        }

        sb.append("        java.sql.Connection conn = ctx.getConnection();\n");
        sb.append("        try {\n");
        // クリーンアップ: 対象テーブルを参照する子テーブル → 対象テーブル → 親テーブル
        // (FK 制約のある実 DB で親を先に消すと外部キー違反になるため子から削除する)
        List childTables = referencingChildTables(entity, entityTable, allParents);
        for (int i = 0; i < childTables.size(); i++) {
            sb.append("            WriteDbUtil.deleteAll(conn, \"")
              .append(childTables.get(i))
              .append("\"); // 対象テーブルを参照する子テーブル(FK対策で先に削除)\n");
        }
        if (entityTable != null) {
            sb.append("            WriteDbUtil.deleteAll(conn, \"").append(entityTable).append("\");\n");
        }
        for (int i = 0; i < allParents.size(); i++) {
            sb.append("            WriteDbUtil.deleteAll(conn, \"")
              .append(allParents.get(i)).append("\");\n");
        }
        // 投入: 親テーブル → 対象テーブル(親から投入)
        for (int i = 0; i < allParents.size(); i++) {
            String pt = (String) allParents.get(i);
            EntityMeta rp = relationParentByTable(relationParents, pt);
            if (rp != null) {
                // リレーション先エンティティのメタが分かる場合は全永続カラムを投入
                // (実 DB の NOT NULL/FK 制約に耐える完全な親行を作る)
                Set pc = new LinkedHashSet(constrained);
                for (int j = 0; j < rp.primaryKeyColumns.size(); j++) {
                    pc.add(TestValues.canonical((String) rp.primaryKeyColumns.get(j)));
                }
                emitEntityRowSeed(sb, rp, pt, pc,
                        "親/リレーション先テーブル " + pt + " の行(FK/JOIN 整合用)");
            } else {
                emitParentSeed(sb, dao, entity, genMethods, pt);
            }
        }
        if (entityTable != null && entity != null) {
            emitEntityRowSeed(sb, entity, entityTable, constrained,
                    "対象テーブル " + entityTable + " の決定的テストデータ");
        }
        sb.append("        } finally {\n");
        sb.append("            conn.close();\n");
        sb.append("        }\n");
        sb.append("    }\n\n");
    }

    /** リレーション親エンティティのうちテーブル名が一致するものを返す(無ければ null)。 */
    private EntityMeta relationParentByTable(List relationParents, String table) {
        for (int i = 0; i < relationParents.size(); i++) {
            EntityMeta rp = (EntityMeta) relationParents.get(i);
            if (rp.tableName.equalsIgnoreCase(table)) {
                return rp;
            }
        }
        return null;
    }

    /**
     * レジストリ内で「このエンティティをリレーション先として参照している」
     * エンティティのテーブル一覧を返す(削除順対策)。自身・親テーブルは除く。
     */
    private List referencingChildTables(EntityMeta entity, String entityTable, List allParents) {
        List out = new ArrayList();
        if (entity == null || entityTable == null || entity.simpleName == null) {
            return out;
        }
        Set skip = new LinkedHashSet();
        skip.add(entityTable.toUpperCase(java.util.Locale.ENGLISH));
        for (int i = 0; i < allParents.size(); i++) {
            skip.add(((String) allParents.get(i)).toUpperCase(java.util.Locale.ENGLISH));
        }
        String targetSimple = entity.simpleName.toLowerCase(java.util.Locale.ENGLISH);
        java.util.Iterator it = entityRegistry.values().iterator();
        while (it.hasNext()) {
            EntityMeta child = (EntityMeta) it.next();
            if (child.tableName == null) {
                continue;
            }
            String up = child.tableName.toUpperCase(java.util.Locale.ENGLISH);
            if (skip.contains(up)) {
                continue;
            }
            for (int i = 0; i < child.relations.size(); i++) {
                com.example.s2daotestgen.model.MetaModel.RelationMeta rel =
                        (com.example.s2daotestgen.model.MetaModel.RelationMeta) child.relations.get(i);
                String tt = nz(rel.targetType);
                int dot = tt.lastIndexOf('.');
                if (dot >= 0) {
                    tt = tt.substring(dot + 1);
                }
                if (tt.toLowerCase(java.util.Locale.ENGLISH).equals(targetSimple)) {
                    skip.add(up);
                    out.add(child.tableName);
                    break;
                }
            }
        }
        return out;
    }

    /**
     * リレーション(_RELNO/@Relation)先として解決できる親エンティティ一覧を返す。
     * レジストリ未設定・解決不能・自テーブルと同一の場合は含めない。
     */
    private List relationParentEntities(EntityMeta entity, String entityTable) {
        List out = new ArrayList();
        if (entity == null) {
            return out;
        }
        Set seen = new LinkedHashSet();
        for (int i = 0; i < entity.relations.size(); i++) {
            com.example.s2daotestgen.model.MetaModel.RelationMeta rel =
                    (com.example.s2daotestgen.model.MetaModel.RelationMeta) entity.relations.get(i);
            EntityMeta target = lookupEntity(rel.targetType);
            if (target == null || target.tableName == null) {
                continue;
            }
            String up = target.tableName.toUpperCase(java.util.Locale.ENGLISH);
            if (entityTable != null && up.equals(entityTable.toUpperCase(java.util.Locale.ENGLISH))) {
                continue;
            }
            if (!seen.contains(up)) {
                seen.add(up);
                out.add(target);
            }
        }
        return out;
    }

    /** エンティティメタに基づき全永続カラムの 1 行を投入するコードを出力する。 */
    private void emitEntityRowSeed(StringBuffer sb, EntityMeta entity, String table,
            Set constrained, String label) {
        List cols = new ArrayList();
        List vals = new ArrayList();
        List notes = new ArrayList();
        for (int i = 0; i < entity.properties.size(); i++) {
            PropertyMeta p = (PropertyMeta) entity.properties.get(i);
            if (!p.persistent) {
                continue;
            }
            cols.add(p.columnName);
            if (constrained.contains(TestValues.canonical(p.columnName))) {
                TestValues.Value v = TestValues.of(p.javaType, p.columnName, TestValues.BASE);
                vals.add(v.expr);
                notes.add(p.columnName + "=" + v.display + " (照合対象:固定値)");
            } else {
                vals.add("ValueFactory.forColumn(\"" + p.javaType + "\", \"" + p.columnName + "\")");
                notes.add(p.columnName + " (埋め草:ValueFactory決定値)");
            }
        }
        sb.append("            // ").append(label).append("\n");
        emitInsert(sb, table, cols, vals, notes);
    }

    private void emitParentSeed(StringBuffer sb, DaoMeta dao, EntityMeta entity, List genMethods,
            String parentTable) {
        Map colType = inferParentColumns(dao, entity, genMethods, parentTable); // colName -> javaType
        if (colType.isEmpty()) {
            return;
        }
        List cols = new ArrayList();
        List vals = new ArrayList();
        List notes = new ArrayList();
        java.util.Iterator it = colType.keySet().iterator();
        while (it.hasNext()) {
            String col = (String) it.next();
            String type = (String) colType.get(col);
            TestValues.Value v = TestValues.of(type, col, TestValues.BASE);
            cols.add(col);
            vals.add(v.expr);
            notes.add(col + "=" + v.display);
        }
        sb.append("            // 親/JOIN先テーブル ").append(parentTable).append(" の行(FK/JOIN 整合用)\n");
        emitInsert(sb, parentTable, cols, vals, notes);
    }

    private void emitInsert(StringBuffer sb, String table, List cols, List vals, List notes) {
        sb.append("            WriteDbUtil.write(conn, new TestDataParam(\"").append(table)
          .append("\",\n");
        sb.append("                new String[] { ");
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("\"").append(cols.get(i)).append("\"");
        }
        sb.append(" },\n");
        sb.append("                new Object[] {\n");
        for (int i = 0; i < vals.size(); i++) {
            sb.append("                    ").append(vals.get(i));
            if (i < vals.size() - 1) {
                sb.append(",");
            }
            sb.append(" // ").append(notes.get(i)).append("\n");
        }
        sb.append("                }));\n");
    }

    private void emitTearDown(StringBuffer sb) {
        sb.append("    protected void tearDown() throws Exception {\n");
        sb.append("        if (ctx != null) {\n");
        sb.append("            ctx.close();\n");
        sb.append("        }\n");
        sb.append("        super.tearDown();\n");
        sb.append("    }\n\n");
    }

    // ================= テストメソッド本体 =================

    private void emitTestMethod(StringBuffer sb, DaoMeta dao, EntityMeta entity,
            String entityTable, MethodMeta m, Set fkCols) {
        String kind = nz(m.methodKind);
        sb.append("    /** ").append(m.name).append(" : ").append(kind)
          .append(" (").append(nz(m.sql != null ? m.sql.resolutionType : "")).append(") */\n");
        sb.append("    public void test").append(cap(m.name)).append("() throws Exception {\n");
        sb.append("        java.sql.Connection conn = ctx.getConnection();\n");
        sb.append("        try {\n");
        sb.append("            EvidenceWriter ev = ctx.newEvidenceWriter();\n");

        if (kind.equals("INSERT")) {
            emitMutation(sb, dao, entity, entityTable, m, "INSERT", fkCols);
        } else if (kind.equals("UPDATE")) {
            emitMutation(sb, dao, entity, entityTable, m, "UPDATE", fkCols);
        } else if (kind.equals("DELETE")) {
            emitMutation(sb, dao, entity, entityTable, m, "DELETE", fkCols);
        } else {
            emitSelect(sb, dao, entity, entityTable, m);
        }

        sb.append("        } finally {\n");
        sb.append("            conn.close();\n");
        sb.append("        }\n");
        sb.append("    }\n\n");
    }

    // ---- SELECT ----

    private void emitSelect(StringBuffer sb, DaoMeta dao, EntityMeta entity, String entityTable,
            MethodMeta m) {
        List argVars = emitArgs(sb, dao, entity, m);
        String ret = resolveReturnType(m.returnType, dao.packageName);
        boolean expectHit = expectHit(m);

        sb.append("            // --- DAO 実行 ---\n");
        String callExpr = "dao." + m.name + "(" + join(argVars) + ")";
        boolean isVoid = ret.equals("void");
        if (isVoid) {
            sb.append("            ").append(callExpr).append(";\n");
        } else {
            sb.append("            ").append(ret).append(" result = ").append(callExpr).append(";\n");
        }

        // 戻り値 assert
        sb.append("            // --- 戻り値 assert ---\n");
        if (!isVoid) {
            emitReturnAssert(sb, m, ret, expectHit);
            sb.append("            ev.writeReturn(\"").append(dao.daoSimpleName).append("\", \"")
              .append(m.name).append("\", ").append(boxResult(ret, "result")).append(");\n");
        } else {
            sb.append("            // 戻り値なし(void)\n");
        }

        // データセット + エビデンス
        emitDatasetEvidence(sb, dao, entity, entityTable, m, null);
    }

    private void emitReturnAssert(StringBuffer sb, MethodMeta m, String ret, boolean expectHit) {
        SqlStructure st = (m.sql != null) ? m.sql.structure : null;
        boolean isCollection = ret.equals("java.util.List") || ret.equals("java.util.Collection")
                || ret.endsWith("[]");
        if (ret.equals("int") || ret.equals("long") || ret.equals("short")) {
            boolean isCount = st != null && containsCount(st.selectColumns);
            if (isCount) {
                sb.append("            assertTrue(\"count は 1 以上\", result >= 1);\n");
            } else {
                sb.append("            // スカラ戻り値(件数以外)は値域を限定せずエビデンスで比較\n");
            }
            return;
        }
        if (ret.equals("double") || ret.equals("float") || ret.equals("boolean")) {
            sb.append("            // プリミティブ戻り値はエビデンスで比較\n");
            return;
        }
        // 参照型
        if (isCollection) {
            if (expectHit) {
                if (ret.endsWith("[]")) {
                    sb.append("            assertNotNull(result);\n");
                    sb.append("            assertTrue(\"1 件以上ヒットするはず\", result.length >= 1);\n");
                } else {
                    sb.append("            assertNotNull(result);\n");
                    sb.append("            assertTrue(\"1 件以上ヒットするはず\", result.size() >= 1);\n");
                }
            } else {
                sb.append("            assertNotNull(result);\n");
            }
        } else {
            // 単一 Bean / String 等
            if (expectHit) {
                sb.append("            assertNotNull(\"該当行が取得できるはず\", result);\n");
            } else {
                sb.append("            // 条件次第で null になりうるためエビデンス出力のみ\n");
            }
        }
    }

    // ---- INSERT / UPDATE / DELETE ----

    private void emitMutation(StringBuffer sb, DaoMeta dao, EntityMeta entity, String entityTable,
            MethodMeta m, String op, Set fkCols) {
        // 対象は単一のエンティティ引数を想定
        ParamMeta beanParam = firstBeanParam(m);
        if (beanParam == null || entity == null) {
            // 想定外: 引数からエンティティを組めない場合は素直に呼ぶだけ
            List argVars = emitArgs(sb, dao, entity, m);
            String ret = resolveReturnType(m.returnType, dao.packageName);
            String callExpr = "dao." + m.name + "(" + join(argVars) + ")";
            if (ret.equals("void")) {
                sb.append("            ").append(callExpr).append(";\n");
            } else {
                sb.append("            ").append(ret).append(" result = ").append(callExpr).append(";\n");
            }
            emitDatasetEvidence(sb, dao, entity, entityTable, m, null);
            return;
        }

        String beanType = resolveType(beanParam.type, dao.packageName);
        String var = safeVar(beanParam.name);
        String pkCol = (entity.primaryKeyColumns.isEmpty()) ? null
                : (String) entity.primaryKeyColumns.get(0);
        PropertyMeta pkProp = propByColumn(entity, pkCol);

        sb.append("            // --- エンティティ組み立て(").append(op).append(") ---\n");
        sb.append("            ").append(beanType).append(" ").append(var).append(" = new ")
          .append(beanType).append("();\n");

        String assertCol = null;
        String assertColAltExpr = null;

        for (int i = 0; i < entity.properties.size(); i++) {
            PropertyMeta p = (PropertyMeta) entity.properties.get(i);
            if (!p.persistent) {
                continue;
            }
            boolean isPk = p.primaryKey;
            boolean isVer = p.versionNo;
            boolean isTs = p.timestamp;
            // JOIN/FK キー列(親テーブルと結合する列)は、更新で値を変えると
            // FK 制約のある実 DB で外部キー違反になるため BASE(親行に一致)を維持する
            boolean isFk = fkCols.contains(TestValues.canonical(p.columnName));
            int variant;
            if (op.equals("INSERT")) {
                variant = isPk ? TestValues.ALT : TestValues.BASE; // 新規PKは既存と別値
            } else {
                // UPDATE/DELETE: PK・楽観ロック列・FK列は既存行/親行に一致させる。他は変更(ALT)
                variant = (isPk || isVer || isTs || isFk) ? TestValues.BASE : TestValues.ALT;
            }
            TestValues.Value v = TestValues.of(p.javaType, p.columnName, variant);
            sb.append("            ").append(var).append(".set").append(cap(p.propertyName))
              .append("(").append(v.expr).append(");");
            sb.append(" // ").append(p.columnName).append("=").append(v.display);
            if (isFk && !op.equals("INSERT")) {
                sb.append(" (JOIN/FKキーのため親行に一致するBASE値を維持)");
            }
            sb.append("\n");

            if (op.equals("UPDATE") && assertCol == null && !isPk && !isVer && !isTs && !isFk) {
                assertCol = p.columnName;
                assertColAltExpr = v.expr;
            }
        }

        // 呼び出し(bean 引数以外のスカラも一応対応)
        List argVars = new ArrayList();
        for (int i = 0; i < m.parameters.size(); i++) {
            ParamMeta p = (ParamMeta) m.parameters.get(i);
            if (p == beanParam) {
                argVars.add(var);
            } else {
                argVars.add(emitScalarArg(sb, dao, entity, m, p, i));
            }
        }
        String ret = resolveReturnType(m.returnType, dao.packageName);
        sb.append("            // --- DAO 実行 ---\n");
        String callExpr = "dao." + m.name + "(" + join(argVars) + ")";
        boolean isVoid = ret.equals("void");
        if (isVoid) {
            sb.append("            ").append(callExpr).append(";\n");
        } else {
            sb.append("            ").append(ret).append(" result = ").append(callExpr).append(";\n");
            if (ret.equals("int") || ret.equals("long")) {
                sb.append("            assertTrue(\"更新/削除/登録 件数は 1 以上\", result >= 1);\n");
            }
            sb.append("            ev.writeReturn(\"").append(dao.daoSimpleName).append("\", \"")
              .append(m.name).append("\", ").append(boxResult(ret, "result")).append(");\n");
        }

        // データセット assert
        String pkUpper = (pkCol != null) ? pkCol.toUpperCase(java.util.Locale.ENGLISH) : null;
        String basePkExpr = (pkProp != null)
                ? TestValues.of(pkProp.javaType, pkProp.columnName, TestValues.BASE).expr : null;
        String altPkExpr = (pkProp != null)
                ? TestValues.of(pkProp.javaType, pkProp.columnName, TestValues.ALT).expr : null;

        MutAssert ma = new MutAssert();
        ma.op = op;
        ma.pkUpper = pkUpper;
        ma.basePkExpr = basePkExpr;
        ma.altPkExpr = altPkExpr;
        ma.assertCol = assertCol;
        ma.assertColAltExpr = assertColAltExpr;
        // S2Dao の自動 UPDATE は versionNo / timestamp を自動更新するため、
        // 値そのものではなく「投入値から変化したこと」のみを assert する
        if (op.equals("UPDATE") && m.sql != null && "AUTO_UPDATE".equals(m.sql.resolutionType)) {
            if (entity.versionNoProperty != null) {
                PropertyMeta vp = propByName(entity, entity.versionNoProperty);
                if (vp != null) {
                    ma.verColUpper = vp.columnName.toUpperCase(java.util.Locale.ENGLISH);
                    ma.verBaseExpr = TestValues.of(vp.javaType, vp.columnName, TestValues.BASE).expr;
                }
            }
            if (entity.timestampProperty != null) {
                PropertyMeta tp = propByName(entity, entity.timestampProperty);
                if (tp != null) {
                    ma.tsColUpper = tp.columnName.toUpperCase(java.util.Locale.ENGLISH);
                    ma.tsBaseExpr = TestValues.of(tp.javaType, tp.columnName, TestValues.BASE).expr;
                }
            }
        }
        emitDatasetEvidence(sb, dao, entity, entityTable, m, ma);
    }

    private static final class MutAssert {
        String op;
        String pkUpper;
        String basePkExpr;
        String altPkExpr;
        String assertCol;
        String assertColAltExpr;
        /** AUTO_UPDATE の楽観ロックカラム(「変化したこと」assert 用)。 */
        String verColUpper;
        String verBaseExpr;
        String tsColUpper;
        String tsBaseExpr;
    }

    // ---- データセット取得 + エビデンス(+ 変更 assert) ----

    private void emitDatasetEvidence(StringBuffer sb, DaoMeta dao, EntityMeta entity,
            String entityTable, MethodMeta m, MutAssert ma) {
        sb.append("            // --- 操作後データセット取得 + エビデンス出力 ---\n");
        List tables = (m.sql != null && m.sql.structure != null)
                ? m.sql.structure.tables : new ArrayList();
        Set done = new LinkedHashSet();
        for (int i = 0; i < tables.size(); i++) {
            String t = (String) tables.get(i);
            String up = t.toUpperCase(java.util.Locale.ENGLISH);
            if (done.contains(up)) {
                continue;
            }
            done.add(up);
            String orderBy = orderByFor(entity, entityTable, t);
            String dsVar = "ds_" + safeVar(t);
            sb.append("            java.util.List ").append(dsVar)
              .append(" = GetDatasetUtil.getDataset(conn, \"").append(t).append("\"")
              .append(orderBy).append(");\n");
            sb.append("            ev.writeDataset(\"").append(dao.daoSimpleName).append("\", \"")
              .append(m.name).append("\", \"").append(t).append("\", ").append(dsVar).append(");\n");

            // 変更 assert は対象テーブル(entityTable)に対してのみ
            if (ma != null && entityTable != null && t.equalsIgnoreCase(entityTable)
                    && ma.pkUpper != null) {
                if (ma.op.equals("INSERT")) {
                    sb.append("            assertNotNull(\"新規行が登録されていること\", GetDatasetUtil.find(")
                      .append(dsVar).append(", \"").append(ma.pkUpper).append("\", ")
                      .append(ma.altPkExpr).append("));\n");
                } else if (ma.op.equals("DELETE")) {
                    sb.append("            assertNull(\"対象行が削除されていること\", GetDatasetUtil.find(")
                      .append(dsVar).append(", \"").append(ma.pkUpper).append("\", ")
                      .append(ma.basePkExpr).append("));\n");
                } else if (ma.op.equals("UPDATE")) {
                    sb.append("            java.util.Map updated = GetDatasetUtil.find(")
                      .append(dsVar).append(", \"").append(ma.pkUpper).append("\", ")
                      .append(ma.basePkExpr).append(");\n");
                    sb.append("            assertNotNull(\"対象行が存在すること\", updated);\n");
                    if (ma.assertCol != null) {
                        sb.append("            assertEquals(\"更新後の値が反映されていること\", EvidenceWriter.normalize(")
                          .append(ma.assertColAltExpr).append("), EvidenceWriter.normalize(updated.get(\"")
                          .append(ma.assertCol.toUpperCase(java.util.Locale.ENGLISH)).append("\")));\n");
                    }
                    if (ma.verColUpper != null) {
                        sb.append("            // versionNo は S2Dao が自動更新するため「変化したこと」のみ確認\n");
                        sb.append("            assertFalse(\"versionNo が投入値から変化していること\", EvidenceWriter.normalize(")
                          .append(ma.verBaseExpr).append(").equals(EvidenceWriter.normalize(updated.get(\"")
                          .append(ma.verColUpper).append("\"))));\n");
                    }
                    if (ma.tsColUpper != null) {
                        sb.append("            // timestamp は S2Dao が自動更新するため「変化したこと」のみ確認\n");
                        sb.append("            assertFalse(\"timestamp が投入値から変化していること\", EvidenceWriter.normalize(")
                          .append(ma.tsBaseExpr).append(").equals(EvidenceWriter.normalize(updated.get(\"")
                          .append(ma.tsColUpper).append("\"))));\n");
                    }
                }
            }
        }
        if (done.isEmpty()) {
            sb.append("            // 対象テーブル不明のためデータセット出力なし\n");
        }
    }

    private String orderByFor(EntityMeta entity, String entityTable, String table) {
        if (entity != null && entityTable != null && table.equalsIgnoreCase(entityTable)
                && !entity.primaryKeyColumns.isEmpty()) {
            StringBuffer b = new StringBuffer(", new String[] { ");
            for (int i = 0; i < entity.primaryKeyColumns.size(); i++) {
                if (i > 0) {
                    b.append(", ");
                }
                b.append("\"").append(entity.primaryKeyColumns.get(i)).append("\"");
            }
            b.append(" }");
            return b.toString();
        }
        return "";
    }

    // ================= 引数生成 =================

    /** SELECT 用: すべての引数変数を宣言し、変数名リストを返す。 */
    private List emitArgs(StringBuffer sb, DaoMeta dao, EntityMeta entity, MethodMeta m) {
        List vars = new ArrayList();
        if (!m.parameters.isEmpty()) {
            sb.append("            // --- 引数準備(投入データにヒットする決定的値) ---\n");
        }
        for (int i = 0; i < m.parameters.size(); i++) {
            ParamMeta p = (ParamMeta) m.parameters.get(i);
            if (isBeanType(p.type)) {
                vars.add(emitDtoArg(sb, dao, entity, m, p));
            } else {
                vars.add(emitScalarArg(sb, dao, entity, m, p, i));
            }
        }
        return vars;
    }

    private String emitScalarArg(StringBuffer sb, DaoMeta dao, EntityMeta entity, MethodMeta m,
            ParamMeta p, int idx) {
        String var = safeVar(p.name);
        String col = whereColumnFor(m, p.name);
        TestValues.Value v = (col != null)
                ? TestValues.of(p.type, col, TestValues.BASE)
                : TestValues.of(p.type, p.name, TestValues.BASE);
        String decl = declType(p.type, dao.packageName);
        String note = (col != null) ? ("投入 " + col + "=" + v.display + " にヒット") : ("決定値 " + v.display);
        sb.append("            ").append(decl).append(" ").append(var).append(" = ")
          .append(v.expr).append("; // ").append(note).append("\n");
        return var;
    }

    private String emitDtoArg(StringBuffer sb, DaoMeta dao, EntityMeta entity, MethodMeta m,
            ParamMeta p) {
        String type = resolveType(p.type, dao.packageName);
        String var = safeVar(p.name);
        sb.append("            ").append(type).append(" ").append(var).append(" = new ")
          .append(type).append("();\n");
        // where で参照される dto プロパティに投入値を設定
        SqlStructure st = (m.sql != null) ? m.sql.structure : null;
        if (st != null) {
            for (int i = 0; i < st.whereBindings.size(); i++) {
                ColumnBinding wb = (ColumnBinding) st.whereBindings.get(i);
                String be = nz(wb.bindExpression);
                int dot = be.indexOf('.');
                if (dot < 0) {
                    continue;
                }
                String root = be.substring(0, dot);
                String prop = be.substring(dot + 1);
                if (!root.equals(p.name)) {
                    continue;
                }
                String propType = dtoPropType(entity, wb.column);
                TestValues.Value v = TestValues.of(propType, wb.column, TestValues.BASE);
                sb.append("            ").append(var).append(".set").append(cap(prop))
                  .append("(").append(v.expr).append("); // ").append(wb.column)
                  .append("=").append(v.display).append(" にヒット\n");
            }
        }
        return var;
    }

    // ================= 判定・解決ヘルパ =================

    private String skipReason(DaoMeta dao, MethodMeta m) {
        String kind = nz(m.methodKind);
        if (kind.equals("PROCEDURE")) {
            return "ストアドプロシージャ(PROCEDURE)は未対応";
        }
        if (m.sql == null) {
            return "SQL が解決できていない";
        }
        if ("UNRESOLVED".equals(m.sql.resolutionType)) {
            return "SQL 未解決(UNRESOLVED)";
        }
        SqlStructure st = m.sql.structure;
        if (st == null || st.tables == null || st.tables.isEmpty()) {
            return "対象テーブルを特定できない(DDL 等・データ組立不能)";
        }
        for (int i = 0; i < m.parameters.size(); i++) {
            ParamMeta p = (ParamMeta) m.parameters.get(i);
            String cat = typeCategory(p.type);
            if (cat.equals("GENERIC") || cat.equals("ARRAY")) {
                return "未対応の引数型: " + p.type;
            }
        }
        return null;
    }

    /** SELECT 系でヒットが保証できるか(件数 assert の可否)。 */
    private boolean expectHit(MethodMeta m) {
        if (!nz(m.methodKind).equals("SELECT")) {
            return false;
        }
        SqlStructure st = (m.sql != null) ? m.sql.structure : null;
        if (st == null) {
            return false;
        }
        for (int i = 0; i < st.whereBindings.size(); i++) {
            ColumnBinding wb = (ColumnBinding) st.whereBindings.get(i);
            if (!"=".equals(wb.operator)) {
                return false;
            }
        }
        // すべてのスカラ引数が '=' の where で束縛されていること
        for (int i = 0; i < m.parameters.size(); i++) {
            ParamMeta p = (ParamMeta) m.parameters.get(i);
            if (isBeanType(p.type)) {
                continue;
            }
            if (whereColumnFor(m, p.name) == null) {
                return false;
            }
        }
        return true;
    }

    private String whereColumnFor(MethodMeta m, String paramName) {
        SqlStructure st = (m.sql != null) ? m.sql.structure : null;
        if (st == null) {
            return null;
        }
        for (int i = 0; i < st.whereBindings.size(); i++) {
            ColumnBinding wb = (ColumnBinding) st.whereBindings.get(i);
            if (!"=".equals(wb.operator)) {
                continue;
            }
            String be = nz(wb.bindExpression);
            if (be.equals(paramName)) {
                return wb.column;
            }
        }
        return null;
    }

    private Set constrainedColumns(DaoMeta dao, List genMethods) {
        Set s = new LinkedHashSet();
        EntityMeta e = dao.entity;
        if (e != null) {
            for (int i = 0; i < e.primaryKeyColumns.size(); i++) {
                s.add(TestValues.canonical((String) e.primaryKeyColumns.get(i)));
            }
            if (e.versionNoProperty != null) {
                PropertyMeta p = propByName(e, e.versionNoProperty);
                if (p != null) {
                    s.add(TestValues.canonical(p.columnName));
                }
            }
            if (e.timestampProperty != null) {
                PropertyMeta p = propByName(e, e.timestampProperty);
                if (p != null) {
                    s.add(TestValues.canonical(p.columnName));
                }
            }
        }
        for (int i = 0; i < genMethods.size(); i++) {
            MethodMeta m = (MethodMeta) genMethods.get(i);
            SqlStructure st = (m.sql != null) ? m.sql.structure : null;
            if (st == null) {
                continue;
            }
            for (int j = 0; j < st.whereBindings.size(); j++) {
                ColumnBinding wb = (ColumnBinding) st.whereBindings.get(j);
                s.add(TestValues.canonical(wb.column));
            }
        }
        return s;
    }

    private List collectTables(List genMethods) {
        List out = new ArrayList();
        Set seen = new LinkedHashSet();
        for (int i = 0; i < genMethods.size(); i++) {
            MethodMeta m = (MethodMeta) genMethods.get(i);
            SqlStructure st = (m.sql != null) ? m.sql.structure : null;
            if (st == null || st.tables == null) {
                continue;
            }
            for (int j = 0; j < st.tables.size(); j++) {
                String t = (String) st.tables.get(j);
                String up = t.toUpperCase(java.util.Locale.ENGLISH);
                if (!seen.contains(up)) {
                    seen.add(up);
                    out.add(t);
                }
            }
        }
        return out;
    }

    /**
     * エンティティテーブル側の JOIN/FK キー列(正準化名)を収集する。
     * <ul>
     *   <li>(a) エンティティのリレーション定義(_RELNO/@Relation)から:
     *       relationKey("CHILD:PARENT" 形式)の子側カラム。relationKey 省略時は
     *       S2Dao の既定規則(リレーション先の主キーと同名の子カラム)に従い、
     *       レジストリで解決したリレーション先エンティティの PK 名と同名の
     *       子プロパティカラムを FK とみなす。</li>
     *   <li>(b) 各メソッドの SQL 中の {@code a.col = b.col} 形式の結合条件のうち、
     *       片側がエンティティテーブル・他側が別テーブルのもの
     *       ({@link #inferParentColumns} と同じ規則。エイリアス解決は行わない)。</li>
     * </ul>
     * これらの列は UPDATE 系テストで値を変えると FK 制約違反になりうるため
     * BASE(親行に一致する値)を維持する。
     */
    private Set fkColumns(EntityMeta entity, List genMethods, String entityTable) {
        Set s = new LinkedHashSet();
        // (a) リレーション定義由来
        if (entity != null) {
            for (int i = 0; i < entity.relations.size(); i++) {
                com.example.s2daotestgen.model.MetaModel.RelationMeta rel =
                        (com.example.s2daotestgen.model.MetaModel.RelationMeta)
                                entity.relations.get(i);
                String key = nz(rel.relationKey);
                if (key.length() > 0) {
                    // "CHILDCOL:PARENTCOL[, ...]"(単一名なら両側同名)
                    String[] pairs = key.split(",");
                    for (int j = 0; j < pairs.length; j++) {
                        String pair = pairs[j].trim();
                        int colon = pair.indexOf(':');
                        String child = (colon >= 0) ? pair.substring(0, colon) : pair;
                        if (child.trim().length() > 0) {
                            s.add(TestValues.canonical(child.trim()));
                        }
                    }
                } else {
                    // relationKey 省略時: リレーション先 PK と同名の子カラム
                    EntityMeta target = lookupEntity(rel.targetType);
                    if (target != null) {
                        for (int j = 0; j < target.primaryKeyColumns.size(); j++) {
                            String pkCanon = TestValues.canonical(
                                    (String) target.primaryKeyColumns.get(j));
                            if (propByColumn(entity, pkCanon) != null) {
                                s.add(pkCanon);
                            }
                        }
                    }
                }
            }
        }
        // (b) SQL の JOIN 条件由来
        if (entityTable == null) {
            return s;
        }
        for (int i = 0; i < genMethods.size(); i++) {
            MethodMeta m = (MethodMeta) genMethods.get(i);
            if (m.sql == null) {
                continue;
            }
            String sql = nz(m.sql.expandedSql);
            if (sql.length() == 0) {
                sql = nz(m.sql.rawSql);
            }
            Matcher mm = JOIN.matcher(sql);
            while (mm.find()) {
                String ta = mm.group(1);
                String ca = mm.group(2);
                String tb = mm.group(3);
                String cb = mm.group(4);
                if (ta.equalsIgnoreCase(entityTable) && !tb.equalsIgnoreCase(entityTable)) {
                    s.add(TestValues.canonical(ca));
                }
                if (tb.equalsIgnoreCase(entityTable) && !ta.equalsIgnoreCase(entityTable)) {
                    s.add(TestValues.canonical(cb));
                }
            }
        }
        return s;
    }

    /** 親テーブルのカラム名→javaType を推定する(JOIN キー + SELECT 参照カラム)。 */
    private Map inferParentColumns(DaoMeta dao, EntityMeta entity, List genMethods,
            String parentTable) {
        Map colType = new LinkedHashMap();
        String pUp = parentTable.toUpperCase(java.util.Locale.ENGLISH);
        for (int i = 0; i < genMethods.size(); i++) {
            MethodMeta m = (MethodMeta) genMethods.get(i);
            if (m.sql == null) {
                continue;
            }
            // JOIN キー
            String sql = nz(m.sql.expandedSql);
            if (sql.length() == 0) {
                sql = nz(m.sql.rawSql);
            }
            Matcher mm = JOIN.matcher(sql);
            while (mm.find()) {
                String ta = mm.group(1);
                String ca = mm.group(2);
                String tb = mm.group(3);
                String cb = mm.group(4);
                if (ta.equalsIgnoreCase(parentTable)) {
                    putCol(colType, ca, joinColType(entity, ca));
                }
                if (tb.equalsIgnoreCase(parentTable)) {
                    putCol(colType, cb, joinColType(entity, cb));
                }
            }
            // SELECT 参照カラム(prefix が親テーブル)
            SqlStructure st = m.sql.structure;
            if (st != null) {
                for (int j = 0; j < st.selectColumns.size(); j++) {
                    String raw = (String) st.selectColumns.get(j);
                    String first = raw.trim();
                    int sp = first.indexOf(' ');
                    if (sp > 0) {
                        first = first.substring(0, sp);
                    }
                    int dot = first.indexOf('.');
                    if (dot < 0) {
                        continue;
                    }
                    String tbl = first.substring(0, dot);
                    String col = first.substring(dot + 1);
                    if (tbl.equalsIgnoreCase(parentTable) && !col.equals("*")) {
                        putCol(colType, col, joinColType(entity, col));
                    }
                }
            }
        }
        return colType;
    }

    private void putCol(Map colType, String col, String type) {
        // 既存(JOIN 由来の数値型)を優先
        if (!colType.containsKey(col)) {
            colType.put(col, type);
        }
    }

    private String joinColType(EntityMeta entity, String col) {
        PropertyMeta p = propByColumn(entity, col);
        if (p != null) {
            return p.javaType;
        }
        // deptno など明らかな数値は int、その他は String
        String canon = TestValues.canonical(col);
        if (canon.endsWith("no") || canon.equals("id") || canon.endsWith("id")) {
            return "int";
        }
        return "String";
    }

    private String dtoPropType(EntityMeta entity, String column) {
        PropertyMeta p = propByColumn(entity, column);
        if (p != null) {
            return p.javaType;
        }
        return "String";
    }

    // ================= 型解決 =================

    /** 引数ローカル宣言用の型(プリミティブはそのまま)。 */
    private String declType(String type, String daoPkg) {
        String t = nz(type).trim();
        if (isPrimitive(t)) {
            return t;
        }
        return resolveType(t, daoPkg);
    }

    /** 参照可能な完全修飾型に解決する(生成コード用)。 */
    private String resolveType(String type, String daoPkg) {
        String t = nz(type).trim();
        if (t.length() == 0) {
            return "java.lang.Object";
        }
        if (isPrimitive(t)) {
            return t;
        }
        int lt = t.indexOf('<');
        if (lt >= 0) {
            t = t.substring(0, lt).trim();
        }
        boolean array = false;
        int arr = t.indexOf('[');
        if (arr >= 0) {
            t = t.substring(0, arr).trim();
            array = true;
        }
        if (isPrimitive(t)) {
            return array ? (t + "[]") : t;
        }
        String resolved = jdkType(t);
        if (resolved == null) {
            // Bean とみなし DAO パッケージに解決
            if (t.indexOf('.') >= 0) {
                resolved = t;
            } else if (daoPkg != null && daoPkg.length() > 0) {
                resolved = daoPkg + "." + t;
            } else {
                resolved = t;
            }
        }
        return array ? (resolved + "[]") : resolved;
    }

    private String resolveReturnType(String returnType, String daoPkg) {
        String t = nz(returnType).trim();
        if (t.length() == 0 || t.equals("void")) {
            return "void";
        }
        return resolveType(t, daoPkg);
    }

    private String jdkType(String simple) {
        if (simple.equals("String")) {
            return "java.lang.String";
        }
        if (simple.equals("Integer") || simple.equals("Long") || simple.equals("Short")
                || simple.equals("Byte") || simple.equals("Float") || simple.equals("Double")
                || simple.equals("Boolean") || simple.equals("Character") || simple.equals("Number")
                || simple.equals("Object")) {
            return "java.lang." + simple;
        }
        if (simple.equals("List") || simple.equals("ArrayList") || simple.equals("Map")
                || simple.equals("HashMap") || simple.equals("Set") || simple.equals("Collection")
                || simple.equals("Iterator")) {
            if (simple.equals("ArrayList")) {
                return "java.util.List";
            }
            if (simple.equals("HashMap")) {
                return "java.util.Map";
            }
            return "java.util." + simple;
        }
        if (simple.equals("Date")) {
            return "java.util.Date";
        }
        if (simple.equals("Timestamp") || simple.equals("Time")) {
            return "java.sql." + simple;
        }
        if (simple.equals("BigDecimal") || simple.equals("BigInteger")) {
            return "java.math." + simple;
        }
        return null;
    }

    private boolean isBeanType(String type) {
        return typeCategory(type).equals("BEAN");
    }

    /** SCALAR / BEAN / GENERIC / ARRAY のいずれかを返す。 */
    private String typeCategory(String type) {
        String t = nz(type).trim();
        if (t.indexOf('<') >= 0) {
            return "GENERIC";
        }
        if (t.indexOf('[') >= 0) {
            return "ARRAY";
        }
        if (isPrimitive(t)) {
            return "SCALAR";
        }
        String simple = t;
        int dot = simple.lastIndexOf('.');
        if (dot >= 0) {
            simple = simple.substring(dot + 1);
        }
        if (simple.equals("String") || simple.equals("Integer") || simple.equals("Long")
                || simple.equals("Short") || simple.equals("Byte") || simple.equals("Float")
                || simple.equals("Double") || simple.equals("Boolean") || simple.equals("Character")
                || simple.equals("Number") || simple.equals("BigDecimal") || simple.equals("BigInteger")
                || simple.equals("Date") || simple.equals("Timestamp") || simple.equals("Time")) {
            return "SCALAR";
        }
        return "BEAN";
    }

    private boolean isPrimitive(String t) {
        return t.equals("int") || t.equals("long") || t.equals("short") || t.equals("byte")
                || t.equals("float") || t.equals("double") || t.equals("boolean") || t.equals("char");
    }

    private String boxResult(String ret, String var) {
        if (ret.equals("int")) {
            return "Integer.valueOf(" + var + ")";
        }
        if (ret.equals("long")) {
            return "Long.valueOf(" + var + ")";
        }
        if (ret.equals("short")) {
            return "Short.valueOf(" + var + ")";
        }
        if (ret.equals("byte")) {
            return "Byte.valueOf(" + var + ")";
        }
        if (ret.equals("float")) {
            return "Float.valueOf(" + var + ")";
        }
        if (ret.equals("double")) {
            return "Double.valueOf(" + var + ")";
        }
        if (ret.equals("boolean")) {
            return "Boolean.valueOf(" + var + ")";
        }
        if (ret.equals("char")) {
            return "Character.valueOf(" + var + ")";
        }
        return var;
    }

    // ================= 汎用 =================

    private ParamMeta firstBeanParam(MethodMeta m) {
        for (int i = 0; i < m.parameters.size(); i++) {
            ParamMeta p = (ParamMeta) m.parameters.get(i);
            if (isBeanType(p.type)) {
                return p;
            }
        }
        return null;
    }

    private PropertyMeta propByColumn(EntityMeta entity, String column) {
        if (entity == null || column == null) {
            return null;
        }
        String canon = TestValues.canonical(column);
        for (int i = 0; i < entity.properties.size(); i++) {
            PropertyMeta p = (PropertyMeta) entity.properties.get(i);
            if (TestValues.canonical(p.columnName).equals(canon)) {
                return p;
            }
        }
        return null;
    }

    private PropertyMeta propByName(EntityMeta entity, String name) {
        if (entity == null || name == null) {
            return null;
        }
        for (int i = 0; i < entity.properties.size(); i++) {
            PropertyMeta p = (PropertyMeta) entity.properties.get(i);
            if (name.equals(p.propertyName)) {
                return p;
            }
        }
        return null;
    }

    private boolean containsCount(List selectColumns) {
        if (selectColumns == null) {
            return false;
        }
        for (int i = 0; i < selectColumns.size(); i++) {
            String c = ((String) selectColumns.get(i)).toLowerCase(java.util.Locale.ENGLISH);
            if (c.indexOf("count(") >= 0) {
                return true;
            }
        }
        return false;
    }

    private String fqDao(DaoMeta dao) {
        if (dao.daoClassName != null && dao.daoClassName.length() > 0) {
            return dao.daoClassName;
        }
        if (dao.packageName != null && dao.packageName.length() > 0) {
            return dao.packageName + "." + dao.daoSimpleName;
        }
        return dao.daoSimpleName;
    }

    private String join(List vars) {
        StringBuffer b = new StringBuffer();
        for (int i = 0; i < vars.size(); i++) {
            if (i > 0) {
                b.append(", ");
            }
            b.append(vars.get(i));
        }
        return b.toString();
    }

    private String cap(String s) {
        if (s == null || s.length() == 0) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private String safeVar(String s) {
        StringBuffer b = new StringBuffer();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_') {
                b.append(c);
            } else {
                b.append('_');
            }
        }
        String v = b.toString();
        if (v.length() == 0 || Character.isDigit(v.charAt(0))) {
            v = "v_" + v;
        }
        return v;
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }
}
