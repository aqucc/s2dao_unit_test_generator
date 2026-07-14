package example.servicebase.service;

import java.util.List;
import java.util.Map;

import example.servicebase.base.ServiceBase;
import example.servicebase.entity.Emp;

/**
 * 発注者の実構造に合わせた具象 Service。S2Dao の Dao とほぼ同等のメソッド群を持ち、
 * 親クラス {@link ServiceBase} に処理を委譲する。
 *
 * <ul>
 *   <li>メソッド名は <b>CRUD 語彙に依存しない</b>(findData/registerData/changeData/removeData)。
 *       メソッド名からは CRUD を判定できないことを実証するための命名。</li>
 *   <li>find 系も含めて入力は {@code Map<Object,Object>}(キー = SQL の /*key*&#47; に対応)。</li>
 *   <li>各メソッドは {@code findByParams(Emp.class, "メソッド名", objobj)} /
 *       {@code updateByParams("メソッド名", objobj)} を呼ぶだけ。SQL は
 *       {@code EmpService_<メソッド名>.sql} で解決される。</li>
 * </ul>
 *
 * <p>ジェネレーターは対応する 2-way SQL の先頭トークン(SELECT/INSERT/UPDATE/DELETE)で
 * CRUD 種別を決める。</p>
 */
public class EmpService extends ServiceBase {

    /** {@code cancelBonus2} が使う SQL 名(定数渡しパターン)。→ EmpService_cancelBonus.sql。 */
    private static final String CANCEL_SQL = "cancelBonus";

    /** 検索(SQL は SELECT)。結果は Emp のリスト。 */
    public List<Emp> findData(Map<Object, Object> objobj) {
        return findByParams(Emp.class, "findData", objobj);
    }

    /**
     * 明示名(<b>素の名称</b>)で SQL を指定する検索。基底の {@code findByParams} を
     * 経由せず {@code selectBySqlFile(Emp.class, "specialQuery", objobj)} を直接呼ぶ。
     * → SQL ファイル {@code EmpService_specialQuery.sql}(SELECT)。
     */
    public List<Emp> searchSpecial(Map<Object, Object> objobj) {
        return selectBySqlFile(Emp.class, "specialQuery", objobj);
    }

    /**
     * 明示名(<b>.sql 付きリテラル</b>)で SQL を指定する更新。
     * {@code updateBySqlFile("bonusUpdate.sql", objobj)} を直接呼ぶ。
     * → SQL ファイル {@code EmpService_bonusUpdate.sql}(UPDATE)。件数を返す。
     */
    public int applyBonus(Map<Object, Object> objobj) {
        return updateBySqlFile("bonusUpdate.sql", objobj);
    }

    /**
     * 明示名(<b>定数渡し</b>)で SQL を指定する更新。
     * {@code updateBySqlFile(CANCEL_SQL, objobj)}(CANCEL_SQL="cancelBonus")を直接呼ぶ。
     * → SQL ファイル {@code EmpService_cancelBonus.sql}(UPDATE)。件数を返す。
     */
    public int cancelBonus2(Map<Object, Object> objobj) {
        return updateBySqlFile(CANCEL_SQL, objobj);
    }

    /** 登録(SQL は INSERT)。件数を返す。 */
    public int registerData(Map<Object, Object> objobj) {
        return updateByParams("registerData", objobj);
    }

    /** 変更(SQL は UPDATE)。件数を返す。 */
    public int changeData(Map<Object, Object> objobj) {
        return updateByParams("changeData", objobj);
    }

    /** 削除(SQL は DELETE)。件数を返す。 */
    public int removeData(Map<Object, Object> objobj) {
        return updateByParams("removeData", objobj);
    }
}
