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
 * SQL 解決の新ルール(3): メソッド本体の bySql 系呼び出しの文字列引数から SQL ファイルを
 * 解決するロジックの回帰テスト。
 *
 * <p>候補文字列の形(素の名称 / {@code .sql} 付き / 定数渡し / ディレクトリ付きフルベース名)を
 * それぞれ検証し、解決不能な明示名・インライン SQL は従来どおり SQL_FILE にならない
 * (= スキップ相当)ことを確認する。CRUD 種別は解決した SQL の先頭トークンで決まる。</p>
 */
public class BySqlExplicitResolveTest {

    private static final String SRC = "src/test/resources/bysql-explicit/java";
    private static final String SQL = "src/test/resources/bysql-explicit/sql";

    private static Map<String, DaoMeta> daos;

    @BeforeClass
    public static void setUp() throws Exception {
        daos = AnalysisFixture.analyze(SRC, SQL, Dialect.STANDARD);
    }

    private static MethodMeta method(final String name) {
        final DaoMeta dao = daos.get("BySqlDao");
        assertNotNull("BySqlDao が Service 経路で解析されるはず", dao);
        final MethodMeta m = AnalysisFixture.method(dao, name);
        assertNotNull("メソッド " + name + " が見つかるはず", m);
        return m;
    }

    @Test
    public void plainNameResolves() {
        // "plainName" → クラス単純名_plainName.sql
        final MethodMeta m = method("findPlain");
        assertEquals("SQL_FILE", m.sql.resolutionType);
        assertEquals("SELECT", m.methodKind);
    }

    @Test
    public void dotSqlLiteralResolves() {
        // "withExt.sql"(.sql 付きリテラル)→ 末尾 .sql を落として クラス単純名_withExt.sql
        final MethodMeta m = method("updateWithExt");
        assertEquals("SQL_FILE", m.sql.resolutionType);
        assertEquals("UPDATE", m.methodKind);
    }

    @Test
    public void constantResolves() {
        // CONST_NAME="byConst"(static final String 定数渡し)→ クラス単純名_byConst.sql
        final MethodMeta m = method("deleteByConst");
        assertEquals("SQL_FILE", m.sql.resolutionType);
        assertEquals("DELETE", m.methodKind);
    }

    @Test
    public void fullBaseNameWithDirResolves() {
        // "sub/dir/BySqlDao_fullBase.sql" → ディレクトリ部・.sql 除去後は既に
        // クラス単純名_ で始まるフルベース名。そのまま BySqlDao_fullBase.sql を解決。
        final MethodMeta m = method("findFullBase");
        assertEquals("SQL_FILE", m.sql.resolutionType);
        assertEquals("SELECT", m.methodKind);
    }

    @Test
    public void unresolvableExplicitNameSkipped() {
        // "noSuchSqlName" は該当ファイルが無い → SQL_FILE にならず従来どおり未解決。
        final MethodMeta m = method("findMissing");
        assertEquals("解決不能な明示名は SQL_FILE にしない", "UNRESOLVED", m.sql.resolutionType);
    }

    @Test
    public void inlineSqlNotTreatedAsFileName() {
        // "SELECT * FROM DUAL" はインライン SQL → ファイル名候補にせず、未解決のまま。
        final MethodMeta m = method("inlineSql");
        assertEquals("インライン SQL はファイル名候補にしない", "UNRESOLVED", m.sql.resolutionType);
    }
}
