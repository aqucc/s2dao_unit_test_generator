package com.example.s2daotestgen;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.junit.BeforeClass;
import org.junit.Test;

import com.example.s2daotestgen.dao.Dialect;
import com.example.s2daotestgen.gen.GenerationReport;
import com.example.s2daotestgen.gen.TestClassGenerator;
import com.example.s2daotestgen.model.MetaModel.DaoMeta;

/**
 * 上級レビュー指摘の回帰テスト:
 * UPDATE 系テストメソッドで JOIN/FK キー列(emp.deptno = dept.deptno の deptno)は
 * ALT(更新値)にせず BASE(親行に一致する値)を維持すること。
 * FK 制約のある実 DB で外部キー違反にならないための保証。
 */
public class FkColumnUpdateTest {

    private static Map<String, DaoMeta> daos;

    @BeforeClass
    public static void setUp() throws Exception {
        daos = AnalysisFixture.analyze(AnalysisFixture.S2DAO_SRC,
                AnalysisFixture.S2DAO_SQL, Dialect.ORACLE);
    }

    /** 全 DAO のエンティティを集めたレジストリ(CLI の generate と同じ構成)。 */
    private static java.util.Map<String, Object> registry() {
        final java.util.Map<String, Object> reg =
                new java.util.LinkedHashMap<String, Object>();
        for (final DaoMeta d : daos.values()) {
            if (d.entity != null && d.entity.simpleName != null) {
                reg.put(d.entity.simpleName.toLowerCase(java.util.Locale.ENGLISH), d.entity);
            }
        }
        return reg;
    }

    @Test
    public void updateKeepsFkColumnAtBaseValue() {
        // EmployeeDao は getAllEmployees の SQL に emp.deptno = dept.deptno の JOIN を持つ
        final DaoMeta dao = daos.get("EmployeeDao");
        assertNotNull(dao);
        final TestClassGenerator gen = new TestClassGenerator();
        final TestClassGenerator.Result r =
                gen.generate(dao, null, new GenerationReport());

        final String update = methodBody(r.source, "testUpdate");
        assertNotNull("testUpdate が生成されているはず", update);
        // FK 列 deptno は BASE(50) を維持し、ALT(51) に変更しないこと
        assertTrue("deptno は BASE 値(50)を維持するはず: \n" + update,
                update.contains("setDeptno(Integer.valueOf(50))")
                        || update.contains("setDeptno(Short.valueOf((short) 50))")
                        || update.contains("setDeptno(Long.valueOf(50L))"));
        assertFalse("deptno を ALT 値(51)へ変更してはならない: \n" + update,
                update.contains("setDeptno(Integer.valueOf(51))")
                        || update.contains("setDeptno(Short.valueOf((short) 51))")
                        || update.contains("setDeptno(Long.valueOf(51L))"));
        // FK でない通常カラム(ename)は引き続き ALT(TESTAU)で更新されること
        assertTrue("ename は ALT 値で更新するはず: \n" + update,
                update.contains("setEname(\"TESTAU\")"));
    }

    @Test
    public void relationBasedFkDetectionWorksWithoutSqlJoin() {
        // EmployeeAutoDao の SQL は emp 単体で JOIN を持たないが、
        // エンティティ Employee のリレーション定義(_RELNO)+レジストリから
        // deptno を FK と判定し、UPDATE で BASE(50) を維持すること。
        final DaoMeta dao = daos.get("EmployeeAutoDao");
        assertNotNull(dao);
        final TestClassGenerator gen = new TestClassGenerator();
        gen.setEntityRegistry(registry());
        final TestClassGenerator.Result r =
                gen.generate(dao, null, new GenerationReport());

        final String update = methodBody(r.source, "testUpdate");
        assertNotNull("testUpdate が生成されているはず", update);
        assertFalse("リレーション由来の FK 列 deptno を ALT 値へ変更してはならない: \n" + update,
                update.contains("setDeptno(Integer.valueOf(51))"));
        assertTrue("deptno は BASE 値(50)を維持するはず: \n" + update,
                update.contains("setDeptno(Integer.valueOf(50))"));
        // リレーション先 DEPT の親行が setUp で投入されること(FK 整合)
        assertTrue("setUp で リレーション先 DEPT の親行を投入するはず: \n" + r.source,
                r.source.indexOf("WriteDbUtil.deleteAll(conn, \"DEPT\")") >= 0
                        || r.source.indexOf("TestDataParam(\"DEPT\"") >= 0);
    }

    @Test
    public void parentTableCleanupDeletesReferencingChildrenFirst() {
        // DepartmentDao(親側)の setUp では、DEPT を参照する子テーブル(EMP)を
        // 先に削除してから DEPT を削除すること(実 FK 環境での削除順対策)。
        final DaoMeta dao = daos.get("DepartmentDao");
        assertNotNull(dao);
        final TestClassGenerator gen = new TestClassGenerator();
        gen.setEntityRegistry(registry());
        final TestClassGenerator.Result r =
                gen.generate(dao, null, new GenerationReport());

        final int empDel = r.source.indexOf("WriteDbUtil.deleteAll(conn, \"EMP\")");
        final int deptDel = r.source.indexOf("WriteDbUtil.deleteAll(conn, \"DEPT\")");
        assertTrue("子テーブル EMP の削除が生成されるはず: \n" + r.source, empDel >= 0);
        assertTrue("DEPT の削除が生成されるはず", deptDel >= 0);
        assertTrue("EMP(子)→ DEPT(親)の順で削除するはず", empDel < deptDel);
    }

    /** 生成ソースから指定テストメソッドの本文(次のメソッドまで)を切り出す。 */
    private static String methodBody(final String source, final String methodName) {
        final int start = source.indexOf("public void " + methodName + "()");
        if (start < 0) {
            return null;
        }
        int end = source.indexOf("\n    public void test", start + 1);
        if (end < 0) {
            end = source.length();
        }
        return source.substring(start, end);
    }
}
