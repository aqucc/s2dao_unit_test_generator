package examples.dao;

import junit.framework.TestCase;
import com.example.s2daotestgen.support.S2TestContext;
import com.example.s2daotestgen.support.TestDataParam;
import com.example.s2daotestgen.support.WriteDbUtil;
import com.example.s2daotestgen.support.GetDatasetUtil;
import com.example.s2daotestgen.support.EvidenceWriter;
import com.example.s2daotestgen.support.ValueFactory;

/**
 * examples.dao.Employee2Dao の自動生成 JUnit3 テスト。
 * s2dao-testgen フェーズ2 が生成。Java5 互換構文のみ。
 */
public class Employee2DaoTest extends TestCase {

    private S2TestContext ctx;
    private examples.dao.Employee2Dao dao;

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

    // TODO: テスト未生成(スキップ) getEmployees : SQL 未解決(UNRESOLVED)
    // TODO: テスト未生成(スキップ) getEmployee : SQL 未解決(UNRESOLVED)

    /** このDAOには自動生成可能なテストメソッドがありません(上記スキップ参照)。 */
    public void testNoGeneratableMethods() throws Exception {
        assertTrue(true);
    }
}
