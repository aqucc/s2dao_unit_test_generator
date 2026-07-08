package example.s2jdbc.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import example.s2jdbc.base.AbstractS2JdbcService;
import example.s2jdbc.entity.Emp;

/**
 * S2JDBC 風の具象 Service。基底クラスに処理を委譲する。
 *
 * <ul>
 *   <li>find 系: 戻り値は {@code List<Emp>}(ジェネリクス付き)。SQL は SELECT。</li>
 *   <li>update 系: 戻り値は void、パラメータは {@code Map<Object,Object>}
 *       (キー = SQL の /*key*&#47; に対応)。SQL は INSERT/UPDATE/DELETE。</li>
 * </ul>
 *
 * <p>メソッド名からは CRUD を判定できない場合もある(基底委譲のため)。ジェネレーターは
 * 対応する 2-way SQL の中身から CRUD 種別を決める。</p>
 */
public class EmpService extends AbstractS2JdbcService {

    /** 部門番号で検索(結果は Emp のリスト)。 */
    public List<Emp> findByDeptno(int deptno) {
        Map<Object, Object> params = new HashMap<Object, Object>();
        params.put("deptno", Integer.valueOf(deptno));
        return selectListBySqlFile(Emp.class, "EmpService_findByDeptno.sql", params);
    }

    /** 給与を更新(void, Map 入力)。 */
    public void updateSalByEmpno(Map<Object, Object> params) {
        executeUpdateBySqlFile("EmpService_updateSalByEmpno.sql", params);
    }

    /** 新規登録(void, Map 入力)。 */
    public void insertEmp(Map<Object, Object> params) {
        executeUpdateBySqlFile("EmpService_insertEmp.sql", params);
    }

    /** 社員番号で削除(void, Map 入力)。 */
    public void deleteByEmpno(Map<Object, Object> params) {
        executeUpdateBySqlFile("EmpService_deleteByEmpno.sql", params);
    }
}
