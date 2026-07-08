package com.example.s2daotestgen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Map;

import org.junit.BeforeClass;
import org.junit.Test;

import com.example.s2daotestgen.dao.Dialect;
import com.example.s2daotestgen.model.MetaModel.DaoMeta;
import com.example.s2daotestgen.model.MetaModel.MethodMeta;

/**
 * ステップ1: CRUD 種別を「メソッド名」ではなく「明示 SQL の中身」で決めることの回帰テスト。
 *
 * <ul>
 *   <li>execute(...) : 名前ベースなら SELECT だが SQL は UPDATE → methodKind=UPDATE</li>
 *   <li>modifySearch(...) : 名前ベースなら UPDATE だが SQL は SELECT → methodKind=SELECT</li>
 * </ul>
 *
 * <p>あわせて、SQL 文種別が不定(DROP/TRUNCATE 等 = OTHER)のときは、
 * メソッド名ベースの種別を維持することも既存サンプルで確認する。</p>
 */
public class CrudFromSqlTest {

    private static final String SRC = "src/test/resources/crud-from-sql/java";
    private static final String SQL = "src/test/resources/crud-from-sql/sql";

    private static Map<String, DaoMeta> daos;

    @BeforeClass
    public static void setUp() throws Exception {
        daos = AnalysisFixture.analyze(SRC, SQL, Dialect.ORACLE);
    }

    @Test
    public void selectNamedMethodWithUpdateSqlBecomesUpdate() {
        final DaoMeta dao = daos.get("ExecDao");
        assertNotNull("ExecDao が DAO として認識されるはず", dao);
        final MethodMeta m = AnalysisFixture.method(dao, "execute");
        assertNotNull(m);
        assertEquals("SQL_FILE", m.sql.resolutionType);
        assertEquals("SQL が UPDATE なので methodKind は UPDATE(名前ベースの SELECT を上書き)",
                "UPDATE", m.methodKind);
        assertEquals("UPDATE", m.sql.structure.statementType);
    }

    @Test
    public void updateNamedMethodWithSelectSqlBecomesSelect() {
        final DaoMeta dao = daos.get("ExecDao");
        final MethodMeta m = AnalysisFixture.method(dao, "modifySearch");
        assertNotNull(m);
        assertEquals("SQL が SELECT なので methodKind は SELECT(名前ベースの UPDATE を上書き)",
                "SELECT", m.methodKind);
        // 2-way SQL のバインドキーも従来どおり抽出できること
        assertEquals("SELECT", m.sql.structure.statementType);
    }

    @Test
    public void ddlSqlKeepsNameBasedKind() throws Exception {
        // 既存サンプル TruncateTableDao: updateDrop_SQL = "DROP TABLE ..."(= OTHER)。
        // OTHER は上書きせず、名前ベース(update→UPDATE)を維持する。
        final Map<String, DaoMeta> s = AnalysisFixture.analyze(
                AnalysisFixture.S2DAO_SRC, AnalysisFixture.S2DAO_SQL, Dialect.ORACLE);
        final DaoMeta dao = s.get("TruncateTableDao");
        assertNotNull(dao);
        final MethodMeta m = AnalysisFixture.method(dao, "updateDrop");
        assertNotNull(m);
        assertEquals("DDL(OTHER)は名前ベースの UPDATE を維持", "UPDATE", m.methodKind);
    }
}
