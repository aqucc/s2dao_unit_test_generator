package com.example.s2daotestgen;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.junit.BeforeClass;
import org.junit.Test;

import com.example.s2daotestgen.dao.Dialect;
import com.example.s2daotestgen.gen.GenerationReport;
import com.example.s2daotestgen.gen.TestClassGenerator;
import com.example.s2daotestgen.model.MetaModel.DaoMeta;

/**
 * 生成可能なテストメソッドが 0 件の DAO(全メソッドがスキップ対象)では、
 * setUp が S2Container / DB を初期化しない(getComponent を呼ばない)ことを検証する。
 *
 * <p>bean/SQL を解決できない DAO(例: TruncateTableDao=DDL のみ)では getComponent が
 * 実行時に失敗するため、プレースホルダのみのクラスでは初期化しないのが正しい。</p>
 */
public class PlaceholderSetUpTest {

    private static Map<String, DaoMeta> daos;

    @BeforeClass
    public static void setUp() throws Exception {
        daos = AnalysisFixture.analyze(AnalysisFixture.S2DAO_SRC,
                AnalysisFixture.S2DAO_SQL, Dialect.ORACLE);
    }

    @Test
    public void placeholderOnlyClassDoesNotInitContainer() {
        final DaoMeta dao = daos.get("TruncateTableDao"); // 全メソッド DDL でスキップ
        final TestClassGenerator gen = new TestClassGenerator();
        final TestClassGenerator.Result r = gen.generate(dao, null, new GenerationReport());
        assertTrue("生成可能メソッドは 0 のはず", r.testMethods == 0);
        assertFalse("プレースホルダクラスは getComponent を呼ばない",
                r.source.contains("getComponent"));
        assertTrue("プレースホルダのダミーテストを含む",
                r.source.contains("testNoGeneratableMethods"));
    }

    @Test
    public void realDaoStillInitsContainer() {
        final DaoMeta dao = daos.get("DepartmentDao"); // 実テストを持つ
        final TestClassGenerator gen = new TestClassGenerator();
        final TestClassGenerator.Result r = gen.generate(dao, null, new GenerationReport());
        assertTrue("実テストを持つ", r.testMethods > 0);
        assertTrue("実 DAO では getComponent を呼ぶ", r.source.contains("getComponent"));
    }
}
