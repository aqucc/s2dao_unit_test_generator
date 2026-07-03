package examples.dao;

import junit.framework.TestCase;
import com.example.s2daotestgen.support.S2TestContext;
import com.example.s2daotestgen.support.TestDataParam;
import com.example.s2daotestgen.support.WriteDbUtil;
import com.example.s2daotestgen.support.GetDatasetUtil;
import com.example.s2daotestgen.support.EvidenceWriter;
import com.example.s2daotestgen.support.ValueFactory;

/**
 * examples.dao.DepartmentManager の自動生成 JUnit3 テスト。
 * s2dao-testgen フェーズ2 が生成。Java5 互換構文のみ。
 */
public class DepartmentManagerTest extends TestCase {

    private S2TestContext ctx;
    private examples.dao.DepartmentManager dao;

    protected void setUp() throws Exception {
        super.setUp();
        ctx = new S2TestContext();
        dao = (examples.dao.DepartmentManager) ctx.getComponent(examples.dao.DepartmentManager.class);
        java.sql.Connection conn = ctx.getConnection();
        try {
            WriteDbUtil.deleteAll(conn, "DEPT");
            // 対象テーブル DEPT の決定的テストデータ
            WriteDbUtil.write(conn, new TestDataParam("DEPT",
                new String[] { "deptno", "dname", "loc", "versionNo" },
                new Object[] {
                    Integer.valueOf(50), // deptno=50 (照合対象:固定値)
                    "SALES", // dname=SALES (照合対象:固定値)
                    "TOKYO", // loc=TOKYO (照合対象:固定値)
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

    /** generate : SELECT (AUTO_SELECT_BY_DTO) */
    public void testGenerate() throws Exception {
        java.sql.Connection conn = ctx.getConnection();
        try {
            EvidenceWriter ev = ctx.newEvidenceWriter();
            // --- 引数準備(投入データにヒットする決定的値) ---
            examples.dao.Department department = new examples.dao.Department();
            // --- DAO 実行 ---
            dao.generate(department);
            // --- 戻り値 assert ---
            // 戻り値なし(void)
            // --- 操作後データセット取得 + エビデンス出力 ---
            java.util.List ds_DEPT = GetDatasetUtil.getDataset(conn, "DEPT", new String[] { "deptno" });
            ev.writeDataset("DepartmentManager", "generate", "DEPT", ds_DEPT);
        } finally {
            conn.close();
        }
    }

    /** change : SELECT (AUTO_SELECT_BY_DTO) */
    public void testChange() throws Exception {
        java.sql.Connection conn = ctx.getConnection();
        try {
            EvidenceWriter ev = ctx.newEvidenceWriter();
            // --- 引数準備(投入データにヒットする決定的値) ---
            examples.dao.Department department = new examples.dao.Department();
            // --- DAO 実行 ---
            dao.change(department);
            // --- 戻り値 assert ---
            // 戻り値なし(void)
            // --- 操作後データセット取得 + エビデンス出力 ---
            java.util.List ds_DEPT = GetDatasetUtil.getDataset(conn, "DEPT", new String[] { "deptno" });
            ev.writeDataset("DepartmentManager", "change", "DEPT", ds_DEPT);
        } finally {
            conn.close();
        }
    }

    /** destory : SELECT (AUTO_SELECT_BY_DTO) */
    public void testDestory() throws Exception {
        java.sql.Connection conn = ctx.getConnection();
        try {
            EvidenceWriter ev = ctx.newEvidenceWriter();
            // --- 引数準備(投入データにヒットする決定的値) ---
            examples.dao.Department department = new examples.dao.Department();
            // --- DAO 実行 ---
            dao.destory(department);
            // --- 戻り値 assert ---
            // 戻り値なし(void)
            // --- 操作後データセット取得 + エビデンス出力 ---
            java.util.List ds_DEPT = GetDatasetUtil.getDataset(conn, "DEPT", new String[] { "deptno" });
            ev.writeDataset("DepartmentManager", "destory", "DEPT", ds_DEPT);
        } finally {
            conn.close();
        }
    }

}
