package examples.dao.tiger;

import junit.framework.TestCase;
import com.example.s2daotestgen.support.S2TestContext;
import com.example.s2daotestgen.support.TestDataParam;
import com.example.s2daotestgen.support.WriteDbUtil;
import com.example.s2daotestgen.support.GetDatasetUtil;
import com.example.s2daotestgen.support.EvidenceWriter;
import com.example.s2daotestgen.support.ValueFactory;

/**
 * examples.dao.tiger.DepartmentDao の自動生成 JUnit3 テスト。
 * s2dao-testgen フェーズ2 が生成。Java5 互換構文のみ。
 */
public class DepartmentDaoTest extends TestCase {

    private S2TestContext ctx;
    private examples.dao.tiger.DepartmentDao dao;

    protected void setUp() throws Exception {
        super.setUp();
        ctx = new S2TestContext();
        dao = (examples.dao.tiger.DepartmentDao) ctx.getComponent(examples.dao.tiger.DepartmentDao.class);
        java.sql.Connection conn = ctx.getConnection();
        try {
            WriteDbUtil.deleteAll(conn, "EMP"); // 対象テーブルを参照する子テーブル(FK対策で先に削除)
            WriteDbUtil.deleteAll(conn, "DEPT");

            // 対象テーブル DEPT の決定的テストデータ
            WriteDbUtil.write(conn, new TestDataParam("DEPT",
                new String[] { "deptno", "dname", "loc", "versionNo" },
                new Object[] {
                    Integer.valueOf(50), // deptno=50 (照合対象:固定値)
                    ValueFactory.forColumn("java.lang.String", "dname"), // dname (埋め草:ValueFactory決定値)
                    ValueFactory.forColumn("java.lang.String", "loc"), // loc (埋め草:ValueFactory決定値)
                    Integer.valueOf(0) // versionNo=0 (照合対象:固定値)
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

    /** insert : INSERT (AUTO_INSERT) */
    public void testInsert() throws Exception {
        java.sql.Connection conn = ctx.getConnection();
        try {
            EvidenceWriter ev = ctx.newEvidenceWriter();

            // --- エンティティ組み立て(INSERT) ---
            examples.dao.tiger.Department department = new examples.dao.tiger.Department();
            department.setDeptno(Integer.valueOf(51)); // deptno=51
            department.setDname("SALES"); // dname=SALES
            department.setLoc("TOKYO"); // loc=TOKYO
            department.setVersionNo(Integer.valueOf(0)); // versionNo=0

            // --- DAO 実行 ---
            dao.insert(department);

            // --- 操作後データセット取得 + エビデンス出力 ---
            java.util.List ds_DEPT = GetDatasetUtil.getDataset(conn, "DEPT", new String[] { "deptno" });
            ev.writeDataset("DepartmentDao", "insert", "DEPT", ds_DEPT);
            assertNotNull("新規行が登録されていること", GetDatasetUtil.find(ds_DEPT, "DEPTNO", Integer.valueOf(51)));
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
            examples.dao.tiger.Department department = new examples.dao.tiger.Department();
            department.setDeptno(Integer.valueOf(50)); // deptno=50
            department.setDname("SALESU"); // dname=SALESU
            department.setLoc("TOKYOU"); // loc=TOKYOU
            department.setVersionNo(Integer.valueOf(0)); // versionNo=0

            // --- DAO 実行 ---
            dao.update(department);

            // --- 操作後データセット取得 + エビデンス出力 ---
            java.util.List ds_DEPT = GetDatasetUtil.getDataset(conn, "DEPT", new String[] { "deptno" });
            ev.writeDataset("DepartmentDao", "update", "DEPT", ds_DEPT);
            java.util.Map updated = GetDatasetUtil.find(ds_DEPT, "DEPTNO", Integer.valueOf(50));
            assertNotNull("対象行が存在すること", updated);
            assertEquals("更新後の値が反映されていること", EvidenceWriter.normalize("SALESU"), EvidenceWriter.normalize(updated.get("DNAME")));

            // versionNo は S2Dao が自動更新するため「変化したこと」のみ確認
            assertFalse("versionNo が投入値から変化していること", EvidenceWriter.normalize(Integer.valueOf(0)).equals(EvidenceWriter.normalize(updated.get("VERSIONNO"))));
        } finally {
            conn.close();
        }
    }

    /** delete : DELETE (AUTO_DELETE) */
    public void testDelete() throws Exception {
        java.sql.Connection conn = ctx.getConnection();
        try {
            EvidenceWriter ev = ctx.newEvidenceWriter();

            // --- エンティティ組み立て(DELETE) ---
            examples.dao.tiger.Department department = new examples.dao.tiger.Department();
            department.setDeptno(Integer.valueOf(50)); // deptno=50
            department.setDname("SALESU"); // dname=SALESU
            department.setLoc("TOKYOU"); // loc=TOKYOU
            department.setVersionNo(Integer.valueOf(0)); // versionNo=0

            // --- DAO 実行 ---
            dao.delete(department);

            // --- 操作後データセット取得 + エビデンス出力 ---
            java.util.List ds_DEPT = GetDatasetUtil.getDataset(conn, "DEPT", new String[] { "deptno" });
            ev.writeDataset("DepartmentDao", "delete", "DEPT", ds_DEPT);
            assertNull("対象行が削除されていること", GetDatasetUtil.find(ds_DEPT, "DEPTNO", Integer.valueOf(50)));
        } finally {
            conn.close();
        }
    }

}
