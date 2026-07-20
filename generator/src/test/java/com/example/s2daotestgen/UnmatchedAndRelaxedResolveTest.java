package com.example.s2daotestgen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.BeforeClass;
import org.junit.Test;

import com.example.s2daotestgen.cli.Main;
import com.example.s2daotestgen.dao.Dialect;
import com.example.s2daotestgen.model.MetaModel.DaoMeta;
import com.example.s2daotestgen.model.MetaModel.MethodMeta;

/**
 * ルール3.5(大文字小文字ゆらぎ吸収)と未対応 .sql 報告(取りこぼしの可視化)の回帰テスト。
 *
 * <ul>
 *   <li>メソッド名のゆらぎ: {@code findData} → {@code FooService_FINDDATA.sql}(SELECT)</li>
 *   <li>bySql 候補のゆらぎ: {@code updateBySqlFile("BonusUpdate")} →
 *       {@code FooService_bonusupdate.sql}(UPDATE)</li>
 *   <li>方言の誤マッチ防止: dbms=oracle で {@code FooService_findHsqlOnly_hsql.sql} のみは
 *       解決されない(UNRESOLVED)</li>
 *   <li>未対応報告: (a)名前ズレ / (b)クラス未検出 / (c)規約外 の 3 区分</li>
 * </ul>
 */
public class UnmatchedAndRelaxedResolveTest {

    private static final String SRC = "src/test/resources/relaxed-unmatched/java";
    private static final String SQL = "src/test/resources/relaxed-unmatched/sql";

    private static Map<String, DaoMeta> stdDaos;

    @BeforeClass
    public static void setUp() throws Exception {
        stdDaos = AnalysisFixture.analyze(SRC, SQL, Dialect.STANDARD);
    }

    private static MethodMeta method(final Map<String, DaoMeta> daos, final String m) {
        final DaoMeta dao = daos.get("FooService");
        assertNotNull("FooService が Service 経路で解析されるはず", dao);
        final MethodMeta mm = AnalysisFixture.method(dao, m);
        assertNotNull("メソッド " + m + " が見つかるはず", mm);
        return mm;
    }

    @Test
    public void relaxedByMethodNameResolves() {
        // findData → FooService_FINDDATA.sql(大小ゆらぎ)を ci 照合で解決
        final MethodMeta m = method(stdDaos, "findData");
        assertEquals("SQL_FILE", m.sql.resolutionType);
        assertEquals("SELECT", m.methodKind);
    }

    @Test
    public void relaxedByBySqlCandidateResolves() {
        // updateBySqlFile("BonusUpdate") → FooService_bonusupdate.sql を ci 照合で解決
        final MethodMeta m = method(stdDaos, "applyBonus");
        assertEquals("SQL_FILE", m.sql.resolutionType);
        assertEquals("UPDATE", m.methodKind);
    }

    @Test
    public void dialectSuffixMismatchNotResolved() throws Exception {
        // dbms=oracle: FooService_findHsqlOnly_hsql.sql しか無い → 解決されない
        final Map<String, DaoMeta> oracle =
                AnalysisFixture.analyze(SRC, SQL, Dialect.ORACLE);
        final MethodMeta m = method(oracle, "findHsqlOnly");
        assertEquals("方言サフィックス違いは解決しない", "UNRESOLVED", m.sql.resolutionType);
    }

    @Test
    public void unmatchedReportClassifiesThreeBuckets() throws Exception {
        final File out = new File("target/relaxed-unmatched-report");
        final List<File> src = new ArrayList<File>();
        src.add(new File(SRC));
        final List<File> sql = new ArrayList<File>();
        sql.add(new File(SQL));
        Main.analyze(src, sql, out, Dialect.STANDARD);

        final File report = new File(out, "unmatched-sql.txt");
        assertTrue("unmatched-sql.txt が出力されるはず", report.exists());
        final String txt = new String(Files.readAllBytes(report.toPath()),
                Charset.forName("UTF-8"));

        final int idxA = txt.indexOf("[名前ズレの疑い]");
        final int idxB = txt.indexOf("[クラス未検出]");
        final int idxC = txt.indexOf("[規約外のファイル名]");
        assertTrue("(a) 名前ズレ区分がある", idxA >= 0);
        assertTrue("(b) クラス未検出区分がある", idxB >= 0);
        assertTrue("(c) 規約外区分がある", idxC >= 0);
        assertTrue("区分の並び (a)<(b)<(c)", idxA < idxB && idxB < idxC);

        // (a) 名前ズレ: FooService_orphan.sql(解析済みクラスだがメソッド未対応)
        assertBetween(txt, "FooService_orphan.sql", idxA, idxB, "(a) 名前ズレ");
        // (b) クラス未検出: Pager_next.sql(解析対象に無いクラス名部)
        assertBetween(txt, "Pager_next.sql", idxB, idxC, "(b) クラス未検出");
        // (c) 規約外: noconvention.sql('_' 無し)
        assertTrue("(c) に noconvention.sql",
                txt.indexOf("noconvention.sql", idxC) >= 0);
    }

    private static void assertBetween(final String txt, final String needle,
            final int from, final int to, final String label) {
        final int pos = txt.indexOf(needle, from);
        assertTrue(label + " に " + needle + " が含まれる", pos >= 0 && pos < to);
    }
}
