package examples.dao.tiger;

import junit.framework.TestCase;
import com.example.s2daotestgen.support.S2TestContext;
import com.example.s2daotestgen.support.TestDataParam;
import com.example.s2daotestgen.support.WriteDbUtil;
import com.example.s2daotestgen.support.GetDatasetUtil;
import com.example.s2daotestgen.support.EvidenceWriter;
import com.example.s2daotestgen.support.ValueFactory;

/**
 * examples.dao.tiger.StoredProcedureTestDao の自動生成 JUnit3 テスト。
 * s2dao-testgen フェーズ2 が生成。Java5 互換構文のみ。
 */
public class StoredProcedureTestDaoTest extends TestCase {

    private S2TestContext ctx;
    private examples.dao.tiger.StoredProcedureTestDao dao;

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

    // TODO: テスト未生成(スキップ) getSalesTax : ストアドプロシージャ(PROCEDURE)は未対応
    // TODO: テスト未生成(スキップ) getSalesTax2 : ストアドプロシージャ(PROCEDURE)は未対応
    // TODO: テスト未生成(スキップ) getSalesTax3 : ストアドプロシージャ(PROCEDURE)は未対応
    // TODO: テスト未生成(スキップ) getSalesTax4 : ストアドプロシージャ(PROCEDURE)は未対応

    /** このDAOには自動生成可能なテストメソッドがありません(上記スキップ参照)。 */
    public void testNoGeneratableMethods() throws Exception {
        assertTrue(true);
    }
}
