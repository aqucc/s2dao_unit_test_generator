package example;

import java.util.List;
import java.util.Map;

/**
 * メソッド本体の bySql 系呼び出し(引数で SQL 名/パスを明示指定)の解決テスト用フィクスチャ。
 * 具象クラス(末尾 Dao)なので Service 経路(BEAN 無し)として解析される。
 *
 * <p>{@code jdbcManager} 等の型は解析(JavaParser パース)には不要なので未定義でよい。</p>
 */
public class BySqlDao {

    /** 定数渡しパターンで使う SQL 名。 */
    private static final String CONST_NAME = "byConst";

    /** 素の名称リテラル → BySqlDao_plainName.sql (SELECT)。 */
    public List findPlain(Map objobj) {
        return jdbcManager.selectBySqlFile(Object.class, "plainName", objobj).getResultList();
    }

    /** .sql 付きリテラル → BySqlDao_withExt.sql (UPDATE)。 */
    public int updateWithExt(Map objobj) {
        return jdbcManager.updateBySqlFile("withExt.sql", objobj);
    }

    /** 定数渡し → BySqlDao_byConst.sql (DELETE)。 */
    public int deleteByConst(Map objobj) {
        return jdbcManager.updateBySqlFile(CONST_NAME, objobj);
    }

    /** ディレクトリ付きフルベース名 → BySqlDao_fullBase.sql (SELECT)。 */
    public List findFullBase(Map objobj) {
        return jdbcManager.selectBySqlFile(Object.class,
                "sub/dir/BySqlDao_fullBase.sql", objobj).getResultList();
    }

    /** 解決不能な明示名(該当 .sql 無し)→ 従来どおり未解決(スキップ)。 */
    public List findMissing(Map objobj) {
        return jdbcManager.selectBySqlFile(Object.class, "noSuchSqlName", objobj).getResultList();
    }

    /** インライン SQL(SELECT で始まる文字列)→ ファイル名候補にせずスキップ。 */
    public List inlineSql(Map objobj) {
        return jdbcManager.selectBySql(Object.class, "SELECT * FROM DUAL", objobj).getResultList();
    }
}
