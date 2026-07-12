package example.servicebase.base;

import java.util.List;
import java.util.Map;

/**
 * オリジナル実装の Service 共通基底クラス {@code ServiceBase} を模した抽象クラス。
 *
 * <p>具象 Service(例 {@code EmpService})はこのクラスを継承し、CRUD 処理を
 * {@link #findByParams} / {@link #updateByParams} に委譲する。基底メソッドは
 * SQL パスを「クラス単純名 + "_" + co(メソッド名) + ".sql"」の規約で組み立て、
 * {@link JdbcManager}(s2jdbcManager 相当)にクエリ実行を委ねる。SQL のパース・
 * バインドは Seasar2 任せ(2-way SQL のバインドコメントがバインドキー)。</p>
 *
 * <p>引数は {@code (Class clazz, String co, Map objobj)}。すなわち find 系も含めて
 * 入力は {@code Map}(キー = SQL の /*key*&#47; に対応)。メソッド名からは CRUD を
 * 判定できないため、ジェネレーターは対応する 2-way SQL の先頭トークンで種別を決める。</p>
 *
 * <p>抽象クラスのためジェネレーターのテスト対象からは除外される(具象 Service のみ対象)。</p>
 */
public abstract class ServiceBase {

    /** dicon から注入される JdbcManager(s2jdbcManager 相当)。 */
    protected JdbcManager jdbcManager;

    /**
     * パラメータ Map による検索。{@code co}(呼び出し元メソッド名)と自クラスの単純名から
     * SQL パス {@code <SimpleName>_<co>.sql} を組み立て、結果エンティティのリストを返す。
     *
     * @param clazz  結果エンティティのクラス
     * @param co     メソッド名(SQL ファイル名の一部)
     * @param objobj バインドパラメータ Map
     */
    protected List findByParams(Class clazz, String co, Map objobj) {
        String path = getClass().getSimpleName() + "_" + co + ".sql";
        return jdbcManager.selectBySqlFile(clazz, path, objobj).getResultList();
    }

    /**
     * パラメータ Map による更新(INSERT/UPDATE/DELETE)。{@code co}(呼び出し元メソッド名)と
     * 自クラスの単純名から SQL パス {@code <SimpleName>_<co>.sql} を組み立て、件数を返す。
     *
     * @param co     メソッド名(SQL ファイル名の一部)
     * @param objobj バインドパラメータ Map
     */
    protected int updateByParams(String co, Map objobj) {
        String path = getClass().getSimpleName() + "_" + co + ".sql";
        return jdbcManager.updateBySqlFile(path, objobj);
    }

    public void setJdbcManager(JdbcManager jdbcManager) {
        this.jdbcManager = jdbcManager;
    }
}
