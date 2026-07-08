package example.s2jdbc.base;

import java.util.List;

/**
 * S2JDBC の Service 共通基底クラス(委譲先)を模した抽象クラス。
 *
 * <p>具象 Service はこのクラスを継承し、基底メソッドへ処理を委譲する。クエリ発行は
 * すべて {@link JdbcManager} 経由(SQL パース・バインドは Seasar2 任せ)。dicon は
 * jdbcManager を注入するだけでよい(コンポーネント自動登録)。</p>
 *
 * <p>抽象クラスのためジェネレーターのテスト対象からは除外される(具象 Service のみ対象)。</p>
 */
public abstract class AbstractS2JdbcService {

    /** dicon から注入される JdbcManager。 */
    protected JdbcManager jdbcManager;

    /** SQL ファイルによる検索(戻り値は結果エンティティのリスト)。 */
    protected List selectListBySqlFile(Class entityClass, String path, Object params) {
        return jdbcManager.selectBySqlFile(entityClass, path, params).getResultList();
    }

    /** SQL ファイルによる更新(件数を返す)。 */
    protected int executeUpdateBySqlFile(String path, Object params) {
        return jdbcManager.updateBySqlFile(path, params);
    }

    public void setJdbcManager(JdbcManager jdbcManager) {
        this.jdbcManager = jdbcManager;
    }
}
