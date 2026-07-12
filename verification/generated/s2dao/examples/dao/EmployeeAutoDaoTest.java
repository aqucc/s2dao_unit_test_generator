package examples.dao;

import junit.framework.TestCase;
import com.example.s2daotestgen.support.S2TestContext;
import com.example.s2daotestgen.support.TestDataParam;
import com.example.s2daotestgen.support.WriteDbUtil;
import com.example.s2daotestgen.support.GetDatasetUtil;
import com.example.s2daotestgen.support.EvidenceWriter;
import com.example.s2daotestgen.support.ValueFactory;

/**
 * examples.dao.EmployeeAutoDao の自動生成 JUnit3 テスト。
 * s2dao-testgen フェーズ2 が生成。Java5 互換構文のみ。
 */
public class EmployeeAutoDaoTest extends TestCase {

    private S2TestContext ctx;
    private examples.dao.EmployeeAutoDao dao;

    protected void setUp() throws Exception {
        super.setUp();
        ctx = new S2TestContext();
        dao = (examples.dao.EmployeeAutoDao) ctx.getComponent(examples.dao.EmployeeAutoDao.class);
        java.sql.Connection conn = ctx.getConnection();
        try {
            WriteDbUtil.deleteAll(conn, "EMP");
            WriteDbUtil.deleteAll(conn, "DEPT");

            // 親/リレーション先テーブル DEPT の行(FK/JOIN 整合用)
            WriteDbUtil.write(conn, new TestDataParam("DEPT",
                new String[] { "deptno", "dname", "loc", "versionNo" },
                new Object[] {
                    Integer.valueOf(50), // deptno=50 (照合対象:固定値)
                    "SALES", // dname=SALES (照合対象:固定値)
                    ValueFactory.forColumn("java.lang.String", "loc"), // loc (埋め草:ValueFactory決定値)
                    ValueFactory.forColumn("int", "versionNo") // versionNo (埋め草:ValueFactory決定値)
                }));

            // 対象テーブル EMP の決定的テストデータ
            WriteDbUtil.write(conn, new TestDataParam("EMP",
                new String[] { "empno", "ename", "job", "mgr", "hiredate", "sal", "comm", "deptno", "tstamp" },
                new Object[] {
                    Long.valueOf(1001L), // empno=1001 (照合対象:固定値)
                    ValueFactory.forColumn("java.lang.String", "ename"), // ename (埋め草:ValueFactory決定値)
                    "CLERK", // job=CLERK (照合対象:固定値)
                    ValueFactory.forColumn("Short", "mgr"), // mgr (埋め草:ValueFactory決定値)
                    ValueFactory.forColumn("java.util.Date", "hiredate"), // hiredate (埋め草:ValueFactory決定値)
                    ValueFactory.forColumn("Float", "sal"), // sal (埋め草:ValueFactory決定値)
                    ValueFactory.forColumn("Float", "comm"), // comm (埋め草:ValueFactory決定値)
                    Integer.valueOf(50), // deptno=50 (照合対象:固定値)
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

    /** getAllEmployees : SELECT (AUTO_SELECT_BY_ARGS) */
    public void testGetAllEmployees() throws Exception {
        java.sql.Connection conn = ctx.getConnection();
        try {
            EvidenceWriter ev = ctx.newEvidenceWriter();

            // --- DAO 実行 ---
            java.util.List result = dao.getAllEmployees();

            // --- 戻り値 assert ---
            assertNotNull(result);
            assertTrue("1 件以上ヒットするはず", result.size() >= 1);
            ev.writeReturn("EmployeeAutoDao", "getAllEmployees", result);

            // --- 操作後データセット取得 + エビデンス出力 ---
            java.util.List ds_EMP = GetDatasetUtil.getDataset(conn, "EMP", new String[] { "empno" });
            ev.writeDataset("EmployeeAutoDao", "getAllEmployees", "EMP", ds_EMP);
        } finally {
            conn.close();
        }
    }

    /** getEmployeeByJobDeptno : SELECT (AUTO_SELECT_BY_ARGS) */
    public void testGetEmployeeByJobDeptno() throws Exception {
        java.sql.Connection conn = ctx.getConnection();
        try {
            EvidenceWriter ev = ctx.newEvidenceWriter();

            // --- 引数準備(投入データにヒットする決定的値) ---
            java.lang.String job = "CLERK"; // 投入 EMP.job=CLERK にヒット
            java.lang.Integer deptno = Integer.valueOf(50); // 投入 EMP.deptno=50 にヒット

            // --- DAO 実行 ---
            java.util.List result = dao.getEmployeeByJobDeptno(job, deptno);

            // --- 戻り値 assert ---
            assertNotNull(result);
            assertTrue("1 件以上ヒットするはず", result.size() >= 1);
            ev.writeReturn("EmployeeAutoDao", "getEmployeeByJobDeptno", result);

            // --- 操作後データセット取得 + エビデンス出力 ---
            java.util.List ds_EMP = GetDatasetUtil.getDataset(conn, "EMP", new String[] { "empno" });
            ev.writeDataset("EmployeeAutoDao", "getEmployeeByJobDeptno", "EMP", ds_EMP);
        } finally {
            conn.close();
        }
    }

    /** getEmployeeByEmpno : SELECT (AUTO_SELECT_BY_ARGS) */
    public void testGetEmployeeByEmpno() throws Exception {
        java.sql.Connection conn = ctx.getConnection();
        try {
            EvidenceWriter ev = ctx.newEvidenceWriter();

            // --- 引数準備(投入データにヒットする決定的値) ---
            int empno = Integer.valueOf(1001); // 投入 EMP.empno=1001 にヒット

            // --- DAO 実行 ---
            examples.dao.Employee result = dao.getEmployeeByEmpno(empno);

            // --- 戻り値 assert ---
            assertNotNull("該当行が取得できるはず", result);
            ev.writeReturn("EmployeeAutoDao", "getEmployeeByEmpno", result);

            // --- 操作後データセット取得 + エビデンス出力 ---
            java.util.List ds_EMP = GetDatasetUtil.getDataset(conn, "EMP", new String[] { "empno" });
            ev.writeDataset("EmployeeAutoDao", "getEmployeeByEmpno", "EMP", ds_EMP);
        } finally {
            conn.close();
        }
    }

    /** getEmployeesBySal : SELECT (AUTO_SELECT_BY_QUERY) */
    public void testGetEmployeesBySal() throws Exception {
        java.sql.Connection conn = ctx.getConnection();
        try {
            EvidenceWriter ev = ctx.newEvidenceWriter();

            // --- 引数準備(投入データにヒットする決定的値) ---
            float minSal = Float.valueOf(62.0f); // 決定値 62
            float maxSal = Float.valueOf(80.0f); // 決定値 80

            // --- DAO 実行 ---
            java.util.List result = dao.getEmployeesBySal(minSal, maxSal);

            // --- 戻り値 assert ---
            assertNotNull(result);
            ev.writeReturn("EmployeeAutoDao", "getEmployeesBySal", result);

            // --- 操作後データセット取得 + エビデンス出力 ---
            java.util.List ds_EMP = GetDatasetUtil.getDataset(conn, "EMP", new String[] { "empno" });
            ev.writeDataset("EmployeeAutoDao", "getEmployeesBySal", "EMP", ds_EMP);
        } finally {
            conn.close();
        }
    }

    /** getEmployeeByDname : SELECT (AUTO_SELECT_BY_ARGS) */
    public void testGetEmployeeByDname() throws Exception {
        java.sql.Connection conn = ctx.getConnection();
        try {
            EvidenceWriter ev = ctx.newEvidenceWriter();

            // --- 引数準備(投入データにヒットする決定的値) ---
            java.lang.String dname = "SALES"; // 決定値 SALES

            // --- DAO 実行 ---
            java.util.List result = dao.getEmployeeByDname(dname);

            // --- 戻り値 assert ---
            assertNotNull(result);
            ev.writeReturn("EmployeeAutoDao", "getEmployeeByDname", result);

            // --- 操作後データセット取得 + エビデンス出力 ---
            java.util.List ds_EMP = GetDatasetUtil.getDataset(conn, "EMP", new String[] { "empno" });
            ev.writeDataset("EmployeeAutoDao", "getEmployeeByDname", "EMP", ds_EMP);
        } finally {
            conn.close();
        }
    }

    /** getEmployeesBySearchCondition : SELECT (AUTO_SELECT_BY_DTO) */
    public void testGetEmployeesBySearchCondition() throws Exception {
        java.sql.Connection conn = ctx.getConnection();
        try {
            EvidenceWriter ev = ctx.newEvidenceWriter();

            // --- 引数準備(投入データにヒットする決定的値) ---
            examples.dao.EmployeeSearchCondition dto = new examples.dao.EmployeeSearchCondition();
            dto.setJob("CLERK"); // EMP.job=CLERK にヒット

            // --- DAO 実行 ---
            java.util.List result = dao.getEmployeesBySearchCondition(dto);

            // --- 戻り値 assert ---
            assertNotNull(result);
            assertTrue("1 件以上ヒットするはず", result.size() >= 1);
            ev.writeReturn("EmployeeAutoDao", "getEmployeesBySearchCondition", result);

            // --- 操作後データセット取得 + エビデンス出力 ---
            java.util.List ds_EMP = GetDatasetUtil.getDataset(conn, "EMP", new String[] { "empno" });
            ev.writeDataset("EmployeeAutoDao", "getEmployeesBySearchCondition", "EMP", ds_EMP);
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
            examples.dao.Employee employee = new examples.dao.Employee();
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
            dao.update(employee);

            // --- 操作後データセット取得 + エビデンス出力 ---
            java.util.List ds_EMP = GetDatasetUtil.getDataset(conn, "EMP", new String[] { "empno" });
            ev.writeDataset("EmployeeAutoDao", "update", "EMP", ds_EMP);
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
