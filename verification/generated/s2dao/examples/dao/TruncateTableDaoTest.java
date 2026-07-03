package examples.dao;

import junit.framework.TestCase;
import com.example.s2daotestgen.support.S2TestContext;
import com.example.s2daotestgen.support.TestDataParam;
import com.example.s2daotestgen.support.WriteDbUtil;
import com.example.s2daotestgen.support.GetDatasetUtil;
import com.example.s2daotestgen.support.EvidenceWriter;
import com.example.s2daotestgen.support.ValueFactory;

/**
 * examples.dao.TruncateTableDao の自動生成 JUnit3 テスト。
 * s2dao-testgen フェーズ2 が生成。Java5 互換構文のみ。
 */
public class TruncateTableDaoTest extends TestCase {

    private S2TestContext ctx;
    private examples.dao.TruncateTableDao dao;

    protected void setUp() throws Exception {
        super.setUp();
        // 自動生成可能なテストメソッドが無いため、コンテナ/DB は初期化しない
    }

    protected void tearDown() throws Exception {
        if (ctx != null) {
            ctx.close();
        }
        super.tearDown();
    }

    // TODO: テスト未生成(スキップ) updateDrop : 対象テーブルを特定できない(DDL 等・データ組立不能)
    // TODO: テスト未生成(スキップ) updateCreate : 対象テーブルを特定できない(DDL 等・データ組立不能)
    // TODO: テスト未生成(スキップ) updateTrancate : 対象テーブルを特定できない(DDL 等・データ組立不能)

    /** このDAOには自動生成可能なテストメソッドがありません(上記スキップ参照)。 */
    public void testNoGeneratableMethods() throws Exception {
        assertTrue(true);
    }
}
