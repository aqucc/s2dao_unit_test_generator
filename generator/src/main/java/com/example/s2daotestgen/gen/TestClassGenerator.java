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

    /** 1 クラス分の生成結果。 */
    public static final class Result {
        public String packageName;
        public String className;
        public String source;
        public int testMethods;
        public int skipped;
    }

    public Result generate(DaoMeta dao, String overridePackage, GenerationReport report) {
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

        StringBuffer sb = new StringBuffer();
        emitHeader(sb, pkg, className, daoFq);
        emitSetUp(sb, dao, daoFq, entity, entityTable, parentTables, genMethods, constrained);
        emitTearDown(sb);

        int testCount = 0;
        for (int i = 0; i < genMethods.size(); i++) {
            MethodMeta m = (MethodMeta) genMethods.get(i);
            emitTestMethod(sb, dao, entity, entityTable, m);
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
            String entityTable, List parentTables, List genMethods, Set constrained) {
        sb.append("    protected void setUp() throws Exception {\n");
        sb.append("        super.setUp();\n");
        sb.append("        ctx = new S2TestContext();\n");
        sb.append("        dao = (").append(daoFq).append(") ctx.getComponent(")
          .append(daoFq).append(".class);\n");

        boolean hasTables = entityTable != null || !parentTables.isEmpty();
        if (!hasTables) {
            sb.append("        // 参照テーブルが特定できないため投入は行わない\n");
            sb.append("    }\n\n");
            return;
        }

        sb.append("        java.sql.Connection conn = ctx.getConnection();\n");
        sb.append("        try {\n");
        // クリーンアップ: 対象テーブル → 親テーブル(子から削除)
        if (entityTable != null) {
            sb.append("            WriteDbUtil.deleteAll(conn, \"").append(entityTable).append("\");\n");
        }
        for (int i = 0; i < parentTables.size(); i++) {
            sb.append("            WriteDbUtil.deleteAll(conn, \"")
              .append(parentTables.get(i)).append("\");\n");
        }
        // 投入: 親テーブル → 対象テーブル(親から投入)
        for (int i = 0; i < parentTables.size(); i++) {
            emitParentSeed(sb, dao, entity, genMethods, (String) parentTables.get(i));
        }
        if (entityTable != null && entity != null) {
            emitEntitySeed(sb, entity, entityTable, constrained);
        }
        sb.append("        } finally {\n");
        sb.append("            conn.close();\n");
        sb.append("        }\n");
        sb.append("    }\n\n");
    }

    private void emitEntitySeed(StringBuffer sb, EntityMeta entity, String table, Set constrained) {
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
        sb.append("            // 対象テーブル ").append(table).append(" の決定的テストデータ\n");
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
            String entityTable, MethodMeta m) {
        String kind = nz(m.methodKind);
        sb.append("    /** ").append(m.name).append(" : ").append(kind)
          .append(" (").append(nz(m.sql != null ? m.sql.resolutionType : "")).append(") */\n");
        sb.append("    public void test").append(cap(m.name)).append("() throws Exception {\n");
        sb.append("        java.sql.Connection conn = ctx.getConnection();\n");
        sb.append("        try {\n");
        sb.append("            EvidenceWriter ev = ctx.newEvidenceWriter();\n");

        if (kind.equals("INSERT")) {
            emitMutation(sb, dao, entity, entityTable, m, "INSERT");
        } else if (kind.equals("UPDATE")) {
            emitMutation(sb, dao, entity, entityTable, m, "UPDATE");
        } else if (kind.equals("DELETE")) {
            emitMutation(sb, dao, entity, entityTable, m, "DELETE");
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
            MethodMeta m, String op) {
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
            int variant;
            if (op.equals("INSERT")) {
                variant = isPk ? TestValues.ALT : TestValues.BASE; // 新規PKは既存と別値
            } else {
                // UPDATE/DELETE: PK・楽観ロック列は既存行に一致させる。他は変更(ALT)
                variant = (isPk || isVer || isTs) ? TestValues.BASE : TestValues.ALT;
            }
            TestValues.Value v = TestValues.of(p.javaType, p.columnName, variant);
            sb.append("            ").append(var).append(".set").append(cap(p.propertyName))
              .append("(").append(v.expr).append(");");
            sb.append(" // ").append(p.columnName).append("=").append(v.display).append("\n");

            if (op.equals("UPDATE") && assertCol == null && !isPk && !isVer && !isTs) {
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
        String pkUpper = (pkCol != null) ? pkCol.toUpperCase() : null;
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
                    ma.verColUpper = vp.columnName.toUpperCase();
                    ma.verBaseExpr = TestValues.of(vp.javaType, vp.columnName, TestValues.BASE).expr;
                }
            }
            if (entity.timestampProperty != null) {
                PropertyMeta tp = propByName(entity, entity.timestampProperty);
                if (tp != null) {
                    ma.tsColUpper = tp.columnName.toUpperCase();
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
            String up = t.toUpperCase();
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
                          .append(ma.assertCol.toUpperCase()).append("\")));\n");
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
                String up = t.toUpperCase();
                if (!seen.contains(up)) {
                    seen.add(up);
                    out.add(t);
                }
            }
        }
        return out;
    }

    /** 親テーブルのカラム名→javaType を推定する(JOIN キー + SELECT 参照カラム)。 */
    private Map inferParentColumns(DaoMeta dao, EntityMeta entity, List genMethods,
            String parentTable) {
        Map colType = new LinkedHashMap();
        String pUp = parentTable.toUpperCase();
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
            String c = ((String) selectColumns.get(i)).toLowerCase();
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
