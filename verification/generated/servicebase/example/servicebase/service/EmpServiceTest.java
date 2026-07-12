package example.servicebase.service;

import junit.framework.TestCase;
import com.example.s2daotestgen.support.S2TestContext;
import com.example.s2daotestgen.support.TestDataParam;
import com.example.s2daotestgen.support.WriteDbUtil;
import com.example.s2daotestgen.support.GetDatasetUtil;
import com.example.s2daotestgen.support.EvidenceWriter;
import com.example.s2daotestgen.support.ValueFactory;

/**
 * example.servicebase.service.EmpService の自動生成 JUnit3 テスト。
 * s2dao-testgen フェーズ2 が生成。Java5 互換構文のみ。
 */
public class EmpServiceTest extends TestCase {

    private S2TestContext ctx;
    private example.servicebase.service.EmpService dao;

    protected void setUp() throws Exception {
        super.setUp();
        ctx = new S2TestContext();
        dao = (example.servicebase.service.EmpService) ctx.getComponent(example.servicebase.service.EmpService.class);
    }

    protected void tearDown() throws Exception {
        if (ctx != null) {
            ctx.close();
        }
        super.tearDown();
    }

    /** findData : SELECT (SQL_FILE, S2JDBC Service) */
    public void testFindData() throws Exception {
        java.sql.Connection conn = ctx.getConnection();
        try {
            EvidenceWriter ev = ctx.newEvidenceWriter();

            // --- 対象テーブル EMP へ決定的データ投入 ---
            WriteDbUtil.deleteAll(conn, "EMP");
            WriteDbUtil.write(conn, new TestDataParam("EMP",
                new String[] { "EMPNO", "ENAME", "JOB", "DEPTNO", "SAL", "VERSION_NO" },
                new Object[] {
                    Integer.valueOf(1001), // EMPNO=1001
                    "TESTA", // ENAME=TESTA
                    "CLERK", // JOB=CLERK
                    Integer.valueOf(50), // DEPTNO=50
                    Integer.valueOf(3000), // SAL=3000
                    Integer.valueOf(34) // VERSION_NO=34
                }));

            // --- 引数準備(投入データにヒットする決定的値) ---
            java.util.Map objobj = new java.util.HashMap();
            objobj.put("deptno", Integer.valueOf(50)); // DEPTNO=50 (WHERE 束縛にヒット)
            objobj.put("job", "CLERK"); // JOB=CLERK (WHERE 束縛にヒット)

            // --- Service 実行 ---
            java.util.List result = dao.findData(objobj);

            // --- 戻り値 assert ---
            assertNotNull(result);
            assertTrue("1 件以上ヒットするはず", result.size() >= 1);
            example.servicebase.entity.Emp row0 = (example.servicebase.entity.Emp) result.get(0);
            assertEquals("先頭行の主キーが投入値と一致", Integer.valueOf(1001), Integer.valueOf(row0.getEmpno()));
            ev.writeReturn("EmpService", "findData", result);

            // --- 操作後データセット取得 + エビデンス出力 ---
            java.util.List ds_EMP = GetDatasetUtil.getDataset(conn, "EMP", new String[] { "EMPNO" });
            ev.writeDataset("EmpService", "findData", "EMP", ds_EMP);

        } finally {
            conn.close();
        }
    }

    /** registerData : INSERT (SQL_FILE, S2JDBC Service) */
    public void testRegisterData() throws Exception {
        java.sql.Connection conn = ctx.getConnection();
        try {
            EvidenceWriter ev = ctx.newEvidenceWriter();

            // --- 対象テーブル EMP の準備(INSERT) ---
            WriteDbUtil.deleteAll(conn, "EMP");

            // --- 入力 Map 構築(SQL の /*key*/ バインドに対応) ---
            java.util.Map arg = new java.util.HashMap();
            arg.put("empno", Integer.valueOf(1001)); // EMPNO=1001
            arg.put("ename", "TESTA"); // ENAME=TESTA
            arg.put("job", "CLERK"); // JOB=CLERK
            arg.put("deptno", Integer.valueOf(50)); // DEPTNO=50
            arg.put("sal", Integer.valueOf(3000)); // SAL=3000
            arg.put("versionNo", Integer.valueOf(34)); // VERSION_NO=34

            // --- Service 実行 ---
            int result = dao.registerData(arg);
            assertTrue("更新/削除/登録 件数は 1 以上", result >= 1);
            ev.writeReturn("EmpService", "registerData", Integer.valueOf(result));

            // --- 操作後データセット取得 + エビデンス出力 ---
            java.util.List ds_EMP = GetDatasetUtil.getDataset(conn, "EMP", new String[] { "EMPNO" });
            ev.writeDataset("EmpService", "registerData", "EMP", ds_EMP);
            assertNotNull("新規行が登録されていること", GetDatasetUtil.find(ds_EMP, "EMPNO", Integer.valueOf(1001)));

        } finally {
            conn.close();
        }
    }

    /** changeData : UPDATE (SQL_FILE, S2JDBC Service) */
    public void testChangeData() throws Exception {
        java.sql.Connection conn = ctx.getConnection();
        try {
            EvidenceWriter ev = ctx.newEvidenceWriter();

            // --- 対象テーブル EMP の準備(UPDATE) ---
            WriteDbUtil.deleteAll(conn, "EMP");
            WriteDbUtil.write(conn, new TestDataParam("EMP",
                new String[] { "EMPNO", "ENAME", "JOB", "DEPTNO", "SAL", "VERSION_NO" },
                new Object[] {
                    Integer.valueOf(1001), // EMPNO=1001
                    "TESTA", // ENAME=TESTA
                    "CLERK", // JOB=CLERK
                    Integer.valueOf(50), // DEPTNO=50
                    Integer.valueOf(3000), // SAL=3000
                    Integer.valueOf(34) // VERSION_NO=34
                }));

            // --- 入力 Map 構築(SQL の /*key*/ バインドに対応) ---
            java.util.Map arg = new java.util.HashMap();
            arg.put("sal", Integer.valueOf(3001)); // SAL=3001
            arg.put("ename", "TESTAU"); // ENAME=TESTAU
            arg.put("empno", Integer.valueOf(1001)); // EMPNO=1001 (WHERE 束縛)

            // --- Service 実行 ---
            int result = dao.changeData(arg);
            assertTrue("更新/削除/登録 件数は 1 以上", result >= 1);
            ev.writeReturn("EmpService", "changeData", Integer.valueOf(result));

            // --- 操作後データセット取得 + エビデンス出力 ---
            java.util.List ds_EMP = GetDatasetUtil.getDataset(conn, "EMP", new String[] { "EMPNO" });
            ev.writeDataset("EmpService", "changeData", "EMP", ds_EMP);
            java.util.Map updated = GetDatasetUtil.find(ds_EMP, "EMPNO", Integer.valueOf(1001));
            assertNotNull("対象行が存在すること", updated);
            assertEquals("更新後の値が反映されていること", EvidenceWriter.normalize(Integer.valueOf(3001)), EvidenceWriter.normalize(updated.get("SAL")));

        } finally {
            conn.close();
        }
    }

    /** removeData : DELETE (SQL_FILE, S2JDBC Service) */
    public void testRemoveData() throws Exception {
        java.sql.Connection conn = ctx.getConnection();
        try {
            EvidenceWriter ev = ctx.newEvidenceWriter();

            // --- 対象テーブル EMP の準備(DELETE) ---
            WriteDbUtil.deleteAll(conn, "EMP");
            WriteDbUtil.write(conn, new TestDataParam("EMP",
                new String[] { "EMPNO", "ENAME", "JOB", "DEPTNO", "SAL", "VERSION_NO" },
                new Object[] {
                    Integer.valueOf(1001), // EMPNO=1001
                    "TESTA", // ENAME=TESTA
                    "CLERK", // JOB=CLERK
                    Integer.valueOf(50), // DEPTNO=50
                    Integer.valueOf(3000), // SAL=3000
                    Integer.valueOf(34) // VERSION_NO=34
                }));

            // --- 入力 Map 構築(SQL の /*key*/ バインドに対応) ---
            java.util.Map arg = new java.util.HashMap();
            arg.put("empno", Integer.valueOf(1001)); // EMPNO=1001 (WHERE 束縛)

            // --- Service 実行 ---
            int result = dao.removeData(arg);
            assertTrue("更新/削除/登録 件数は 1 以上", result >= 1);
            ev.writeReturn("EmpService", "removeData", Integer.valueOf(result));

            // --- 操作後データセット取得 + エビデンス出力 ---
            java.util.List ds_EMP = GetDatasetUtil.getDataset(conn, "EMP", new String[] { "EMPNO" });
            ev.writeDataset("EmpService", "removeData", "EMP", ds_EMP);
            assertNull("対象行が削除されていること", GetDatasetUtil.find(ds_EMP, "EMPNO", Integer.valueOf(1001)));

        } finally {
            conn.close();
        }
    }

}
