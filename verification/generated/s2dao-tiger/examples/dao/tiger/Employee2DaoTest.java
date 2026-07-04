package examples.dao.tiger;

import junit.framework.TestCase;
import com.example.s2daotestgen.support.S2TestContext;
import com.example.s2daotestgen.support.TestDataParam;
import com.example.s2daotestgen.support.WriteDbUtil;
import com.example.s2daotestgen.support.GetDatasetUtil;
import com.example.s2daotestgen.support.EvidenceWriter;
import com.example.s2daotestgen.support.ValueFactory;

/**
 * examples.dao.tiger.Employee2Dao の自動生成 JUnit3 テスト。
 * s2dao-testgen フェーズ2 が生成。Java5 互換構文のみ。
 */
public class Employee2DaoTest extends TestCase {

    private S2TestContext ctx;
    private examples.dao.tiger.Employee2Dao dao;

    protected void setUp() throws Exception {
        super.setUp();
        ctx = new S2TestContext();
        dao = (examples.dao.tiger.Employee2Dao) ctx.getComponent(examples.dao.tiger.Employee2Dao.class);
        java.sql.Connection conn = ctx.getConnection();
        try {
            WriteDbUtil.deleteAll(conn, "EMP");
            WriteDbUtil.deleteAll(conn, "DEPT");
            // 親/リレーション先テーブル DEPT の行(FK/JOIN 整合用)
            WriteDbUtil.write(conn, new TestDataParam("DEPT",
                new String[] { "deptno", "dname", "loc", "versionNo" },
                new Object[] {
                    Integer.valueOf(50), // deptno=50 (照合対象:固定値)
                    ValueFactory.forColumn("java.lang.String", "dname"), // dname (埋め草:ValueFactory決定値)
                    ValueFactory.forColumn("java.lang.String", "loc"), // loc (埋め草:ValueFactory決定値)
                    ValueFactory.forColumn("int", "versionNo") // versionNo (埋め草:ValueFactory決定値)
                }));
            // 対象テーブル EMP の決定的テストデータ
            WriteDbUtil.write(conn, new TestDataParam("EMP",
                new String[] { "empno", "ename", "job", "mgr", "hiredate", "sal", "comm", "deptno", "tstamp" },
                new Object[] {
                    Long.valueOf(1001L), // empno=1001 (照合対象:固定値)
                    ValueFactory.forColumn("java.lang.String", "ename"), // ename (埋め草:ValueFactory決定値)
                    ValueFactory.forColumn("java.lang.String", "job"), // job (埋め草:ValueFactory決定値)
                    ValueFactory.forColumn("Short", "mgr"), // mgr (埋め草:ValueFactory決定値)
                    ValueFactory.forColumn("java.util.Date", "hiredate"), // hiredate (埋め草:ValueFactory決定値)
                    ValueFactory.forColumn("Float", "sal"), // sal (埋め草:ValueFactory決定値)
                    ValueFactory.forColumn("Float", "comm"), // comm (埋め草:ValueFactory決定値)
                    ValueFactory.forColumn("int", "deptno"), // deptno (埋め草:ValueFactory決定値)
                    java.sql.Timestamp.valueOf("2001-01-01 00:00:00") // tstamp=2001-01-01 00:00:00 (照合対象:固定値)
                }));
        } finally {
            conn.close();
        }
    }

    protected void tearDown() throws Exception {
        if (ctx != null) {
            ctx.close();
        }
        super.tearDown();
    }

    /** getEmployees : SELECT (AUTO_SELECT_BY_ARGS) */
    public void testGetEmployees() throws Exception {
        java.sql.Connection conn = ctx.getConnection();
        try {
            EvidenceWriter ev = ctx.newEvidenceWriter();
            // --- 引数準備(投入データにヒットする決定的値) ---
            java.lang.String ename = "TESTA"; // 決定値 TESTA
            // --- DAO 実行 ---
            java.util.List result = dao.getEmployees(ename);
            // --- 戻り値 assert ---
            assertNotNull(result);
            ev.writeReturn("Employee2Dao", "getEmployees", result);
            // --- 操作後データセット取得 + エビデンス出力 ---
            java.util.List ds_EMP = GetDatasetUtil.getDataset(conn, "EMP", new String[] { "empno" });
            ev.writeDataset("Employee2Dao", "getEmployees", "EMP", ds_EMP);
        } finally {
            conn.close();
        }
    }

    /** getEmployee : SELECT (AUTO_SELECT_BY_ARGS) */
    public void testGetEmployee() throws Exception {
        java.sql.Connection conn = ctx.getConnection();
        try {
            EvidenceWriter ev = ctx.newEvidenceWriter();
            // --- 引数準備(投入データにヒットする決定的値) ---
            int empno = Integer.valueOf(1001); // 投入 EMP.empno=1001 にヒット
            // --- DAO 実行 ---
            examples.dao.tiger.Employee result = dao.getEmployee(empno);
            // --- 戻り値 assert ---
            assertNotNull("該当行が取得できるはず", result);
            ev.writeReturn("Employee2Dao", "getEmployee", result);
            // --- 操作後データセット取得 + エビデンス出力 ---
            java.util.List ds_EMP = GetDatasetUtil.getDataset(conn, "EMP", new String[] { "empno" });
            ev.writeDataset("Employee2Dao", "getEmployee", "EMP", ds_EMP);
        } finally {
            conn.close();
        }
    }

    /** update : UPDATE (AUTO_UPDATE) */
    public void testUpdate() throws Exception {
        java.sql.Connection conn = ctx.getConnection();
        try {
            EvidenceWriter ev = ctx.newEvidenceWriter();
            // --- エンティティ組み立て(UPDATE) ---
            examples.dao.tiger.Employee employee = new examples.dao.tiger.Employee();
            employee.setEmpno(Long.valueOf(1001L)); // empno=1001
            employee.setEname("TESTAU"); // ename=TESTAU
            employee.setJob("CLERKU"); // job=CLERKU
            employee.setMgr(Short.valueOf((short) 7901)); // mgr=7901
            employee.setHiredate(java.sql.Date.valueOf("1982-01-23")); // hiredate=1982-01-23
            employee.setSal(Float.valueOf(3001.0f)); // sal=3001
            employee.setComm(Float.valueOf(501.0f)); // comm=501
            employee.setDeptno(Integer.valueOf(50)); // deptno=50 (JOIN/FKキーのため親行に一致するBASE値を維持)
            employee.setTimestamp(java.sql.Timestamp.valueOf("2001-01-01 00:00:00")); // tstamp=2001-01-01 00:00:00
            // --- DAO 実行 ---
            int result = dao.update(employee);
            assertTrue("更新/削除/登録 件数は 1 以上", result >= 1);
            ev.writeReturn("Employee2Dao", "update", Integer.valueOf(result));
            // --- 操作後データセット取得 + エビデンス出力 ---
            java.util.List ds_EMP = GetDatasetUtil.getDataset(conn, "EMP", new String[] { "empno" });
            ev.writeDataset("Employee2Dao", "update", "EMP", ds_EMP);
            java.util.Map updated = GetDatasetUtil.find(ds_EMP, "EMPNO", Long.valueOf(1001L));
            assertNotNull("対象行が存在すること", updated);
            assertEquals("更新後の値が反映されていること", EvidenceWriter.normalize("TESTAU"), EvidenceWriter.normalize(updated.get("ENAME")));
            // timestamp は S2Dao が自動更新するため「変化したこと」のみ確認
            assertFalse("timestamp が投入値から変化していること", EvidenceWriter.normalize(java.sql.Timestamp.valueOf("2001-01-01 00:00:00")).equals(EvidenceWriter.normalize(updated.get("TSTAMP"))));
        } finally {
            conn.close();
        }
    }

}
