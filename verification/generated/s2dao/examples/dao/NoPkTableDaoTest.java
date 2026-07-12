package examples.dao;

import junit.framework.TestCase;
import com.example.s2daotestgen.support.S2TestContext;
import com.example.s2daotestgen.support.TestDataParam;
import com.example.s2daotestgen.support.WriteDbUtil;
import com.example.s2daotestgen.support.GetDatasetUtil;
import com.example.s2daotestgen.support.EvidenceWriter;
import com.example.s2daotestgen.support.ValueFactory;

/**
 * examples.dao.NoPkTableDao の自動生成 JUnit3 テスト。
 * s2dao-testgen フェーズ2 が生成。Java5 互換構文のみ。
 */
public class NoPkTableDaoTest extends TestCase {

    private S2TestContext ctx;
    private examples.dao.NoPkTableDao dao;

    protected void setUp() throws Exception {
        super.setUp();
        ctx = new S2TestContext();
        dao = (examples.dao.NoPkTableDao) ctx.getComponent(examples.dao.NoPkTableDao.class);
        java.sql.Connection conn = ctx.getConnection();
        try {
            WriteDbUtil.deleteAll(conn, "NoPkTable");

            // 対象テーブル NoPkTable の決定的テストデータ
            WriteDbUtil.write(conn, new TestDataParam("NoPkTable",
                new String[] { "aaa", "bbb" },
                new Object[] {
                    "A1", // aaa=A1 (照合対象:固定値)
                    ValueFactory.forColumn("Integer", "bbb") // bbb (埋め草:ValueFactory決定値)
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

    /** selectAll : SELECT (AUTO_SELECT_BY_ARGS) */
    public void testSelectAll() throws Exception {
        java.sql.Connection conn = ctx.getConnection();
        try {
            EvidenceWriter ev = ctx.newEvidenceWriter();

            // --- DAO 実行 ---
            examples.dao.NoPkTable[] result = dao.selectAll();

            // --- 戻り値 assert ---
            assertNotNull(result);
            assertTrue("1 件以上ヒットするはず", result.length >= 1);
            ev.writeReturn("NoPkTableDao", "selectAll", result);

            // --- 操作後データセット取得 + エビデンス出力 ---
            java.util.List ds_NoPkTable = GetDatasetUtil.getDataset(conn, "NoPkTable", new String[] { "aaa" });
            ev.writeDataset("NoPkTableDao", "selectAll", "NoPkTable", ds_NoPkTable);

        } finally {
            conn.close();
        }
    }

}
