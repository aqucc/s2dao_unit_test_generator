package examples.dao;

import junit.framework.TestCase;
import com.example.s2daotestgen.support.S2TestContext;
import com.example.s2daotestgen.support.TestDataParam;
import com.example.s2daotestgen.support.WriteDbUtil;
import com.example.s2daotestgen.support.GetDatasetUtil;
import com.example.s2daotestgen.support.EvidenceWriter;
import com.example.s2daotestgen.support.ValueFactory;

/**
 * examples.dao.EmployeeDao の自動生成 JUnit3 テスト。
 * s2dao-testgen フェーズ2 が生成。Java5 互換構文のみ。
 */
public class EmployeeDaoTest extends TestCase {

    private S2TestContext ctx;
    private examples.dao.EmployeeDao dao;

    protected void setUp() throws Exception {
        super.setUp();
        ctx = new S2TestContext();
        dao = (examples.dao.EmployeeDao) ctx.getComponent(examples.dao.EmployeeDao.class);
        java.sql.Connection conn = ctx.getConnection();
        try {
            WriteDbUtil.deleteAll(conn, "EMP");
            WriteDbUtil.deleteAll(conn, "dept");
            // 親/JOIN先テーブル dept の行(FK/JOIN 整合用)
            WriteDbUtil.write(conn, new TestDataParam("dept",
                new String[] { "deptno", "dname", "loc" },
                new Object[] {
                    Integer.valueOf(50), // deptno=50
                    "SALES", // dname=SALES
                    "TOKYO" // loc=TOKYO
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

    /** getAllEmployees : SELECT (SQL_FILE) */
    public void testGetAllEmployees() throws Exception {
        java.sql.Connection conn = ctx.getConnection();
        try {
            EvidenceWriter ev = ctx.newEvidenceWriter();
            // --- DAO 実行 ---
            java.util.List result = dao.getAllEmployees();
            // --- 戻り値 assert ---
            assertNotNull(result);
            assertTrue("1 件以上ヒットするはず", result.size() >= 1);
            ev.writeReturn("EmployeeDao", "getAllEmployees", result);
            // --- 操作後データセット取得 + エビデンス出力 ---
            java.util.List ds_emp = GetDatasetUtil.getDataset(conn, "emp", new String[] { "empno" });
            ev.writeDataset("EmployeeDao", "getAllEmployees", "emp", ds_emp);
            java.util.List ds_dept = GetDatasetUtil.getDataset(conn, "dept");
            ev.writeDataset("EmployeeDao", "getAllEmployees", "dept", ds_dept);
        } finally {
            conn.close();
        }
    }

    /** getEmps : SELECT (SQL_FILE) */
    public void testGetEmps() throws Exception {
        java.sql.Connection conn = ctx.getConnection();
        try {
            EvidenceWriter ev = ctx.newEvidenceWriter();
            // --- 引数準備(投入データにヒットする決定的値) ---
            examples.dao.EmployeeSearchCondition dto = new examples.dao.EmployeeSearchCondition();
            dto.setJob("CLERK"); // job=CLERK にヒット
            // --- DAO 実行 ---
            java.util.List result = dao.getEmps(dto);
            // --- 戻り値 assert ---
            assertNotNull(result);
            assertTrue("1 件以上ヒットするはず", result.size() >= 1);
            ev.writeReturn("EmployeeDao", "getEmps", result);
            // --- 操作後データセット取得 + エビデンス出力 ---
            java.util.List ds_emp = GetDatasetUtil.getDataset(conn, "emp", new String[] { "empno" });
            ev.writeDataset("EmployeeDao", "getEmps", "emp", ds_emp);
        } finally {
            conn.close();
        }
    }

    /** getEmployee : SELECT (SQL_FILE) */
    public void testGetEmployee() throws Exception {
        java.sql.Connection conn = ctx.getConnection();
        try {
            EvidenceWriter ev = ctx.newEvidenceWriter();
            // --- 引数準備(投入データにヒットする決定的値) ---
            int empno = Integer.valueOf(1001); // 投入 empno=1001 にヒット
            // --- DAO 実行 ---
            examples.dao.Employee result = dao.getEmployee(empno);
            // --- 戻り値 assert ---
            assertNotNull("該当行が取得できるはず", result);
            ev.writeReturn("EmployeeDao", "getEmployee", result);
            // --- 操作後データセット取得 + エビデンス出力 ---
            java.util.List ds_emp = GetDatasetUtil.getDataset(conn, "emp", new String[] { "empno" });
            ev.writeDataset("EmployeeDao", "getEmployee", "emp", ds_emp);
            java.util.List ds_dept = GetDatasetUtil.getDataset(conn, "dept");
            ev.writeDataset("EmployeeDao", "getEmployee", "dept", ds_dept);
        } finally {
            conn.close();
        }
    }

    /** getCount : SELECT (SQL_FILE) */
    public void testGetCount() throws Exception {
        java.sql.Connection conn = ctx.getConnection();
        try {
            EvidenceWriter ev = ctx.newEvidenceWriter();
            // --- DAO 実行 ---
            int result = dao.getCount();
            // --- 戻り値 assert ---
            assertTrue("count は 1 以上", result >= 1);
            ev.writeReturn("EmployeeDao", "getCount", Integer.valueOf(result));
            // --- 操作後データセット取得 + エビデンス出力 ---
            java.util.List ds_emp = GetDatasetUtil.getDataset(conn, "emp", new String[] { "empno" });
            ev.writeDataset("EmployeeDao", "getCount", "emp", ds_emp);
        } finally {
            conn.close();
        }
    }

    /** getEmployeeByJobDeptno : SELECT (SQL_FILE) */
    public void testGetEmployeeByJobDeptno() throws Exception {
        java.sql.Connection conn = ctx.getConnection();
        try {
            EvidenceWriter ev = ctx.newEvidenceWriter();
            // --- 引数準備(投入データにヒットする決定的値) ---
            java.lang.String job = "CLERK"; // 投入 job=CLERK にヒット
            java.lang.Integer deptno = Integer.valueOf(50); // 投入 deptno=50 にヒット
            // --- DAO 実行 ---
            java.util.List result = dao.getEmployeeByJobDeptno(job, deptno);
            // --- 戻り値 assert ---
            assertNotNull(result);
            assertTrue("1 件以上ヒットするはず", result.size() >= 1);
            ev.writeReturn("EmployeeDao", "getEmployeeByJobDeptno", result);
            // --- 操作後データセット取得 + エビデンス出力 ---
            java.util.List ds_emp = GetDatasetUtil.getDataset(conn, "emp", new String[] { "empno" });
            ev.writeDataset("EmployeeDao", "getEmployeeByJobDeptno", "emp", ds_emp);
        } finally {
            conn.close();
        }
    }

    /** getEmployeeByDeptno : SELECT (AUTO_SELECT_BY_QUERY) */
    public void testGetEmployeeByDeptno() throws Exception {
        java.sql.Connection conn = ctx.getConnection();
        try {
            EvidenceWriter ev = ctx.newEvidenceWriter();
            // --- 引数準備(投入データにヒットする決定的値) ---
            java.lang.Integer deptno = Integer.valueOf(50); // 投入 deptno=50 にヒット
            // --- DAO 実行 ---
            java.util.List result = dao.getEmployeeByDeptno(deptno);
            // --- 戻り値 assert ---
            assertNotNull(result);
            assertTrue("1 件以上ヒットするはず", result.size() >= 1);
            ev.writeReturn("EmployeeDao", "getEmployeeByDeptno", result);
            // --- 操作後データセット取得 + エビデンス出力 ---
            java.util.List ds_EMP = GetDatasetUtil.getDataset(conn, "EMP", new String[] { "empno" });
            ev.writeDataset("EmployeeDao", "getEmployeeByDeptno", "EMP", ds_EMP);
        } finally {
            conn.close();
        }
    }

    /** update : UPDATE (SQL_FILE) */
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
            employee.setDeptno(Integer.valueOf(51)); // deptno=51
            employee.setTimestamp(java.sql.Timestamp.valueOf("2001-01-01 00:00:00")); // tstamp=2001-01-01 00:00:00
            // --- DAO 実行 ---
            int result = dao.update(employee);
            assertTrue("更新/削除/登録 件数は 1 以上", result >= 1);
            ev.writeReturn("EmployeeDao", "update", Integer.valueOf(result));
            // --- 操作後データセット取得 + エビデンス出力 ---
            java.util.List ds_emp = GetDatasetUtil.getDataset(conn, "emp", new String[] { "empno" });
            ev.writeDataset("EmployeeDao", "update", "emp", ds_emp);
            java.util.Map updated = GetDatasetUtil.find(ds_emp, "EMPNO", Long.valueOf(1001L));
            assertNotNull("対象行が存在すること", updated);
            assertEquals("更新後の値が反映されていること", EvidenceWriter.normalize("TESTAU"), EvidenceWriter.normalize(updated.get("ENAME")));
        } finally {
            conn.close();
        }
    }

    /** getAllEmployeeNumbers : SELECT (MANUAL_ANNOTATION) */
    public void testGetAllEmployeeNumbers() throws Exception {
        java.sql.Connection conn = ctx.getConnection();
        try {
            EvidenceWriter ev = ctx.newEvidenceWriter();
            // --- DAO 実行 ---
            int[] result = dao.getAllEmployeeNumbers();
            // --- 戻り値 assert ---
            assertNotNull(result);
            assertTrue("1 件以上ヒットするはず", result.length >= 1);
            ev.writeReturn("EmployeeDao", "getAllEmployeeNumbers", result);
            // --- 操作後データセット取得 + エビデンス出力 ---
            java.util.List ds_emp = GetDatasetUtil.getDataset(conn, "emp", new String[] { "empno" });
            ev.writeDataset("EmployeeDao", "getAllEmployeeNumbers", "emp", ds_emp);
        } finally {
            conn.close();
        }
    }

}
