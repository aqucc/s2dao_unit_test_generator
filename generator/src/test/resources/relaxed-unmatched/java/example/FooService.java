package example;

import java.util.List;
import java.util.Map;

/**
 * 大文字小文字ゆらぎ吸収(ルール3.5)と未対応 .sql 報告のテスト用フィクスチャ。
 * 具象クラス(末尾 Service)なので Service 経路(BEAN 無し)として解析される。
 *
 * <p>{@code jdbcManager} 等の型は解析(JavaParser パース)には不要なので未定義でよい。</p>
 */
public class FooService {

    /**
     * メソッド名 findData に対し、規約名の大小がゆらいだ FooService_FINDDATA.sql (SELECT) を
     * ルール3.5 のメソッド名照合(ci)で解決する。
     */
    public List findData(Map param) {
        return jdbcManager.selectBySqlFile(Object.class, "findData", param).getResultList();
    }

    /**
     * 方言サフィックスの誤マッチ防止。dbms=oracle 解析では、_hsql サフィックス付きの
     * FooService_findHsqlOnly_hsql.sql は「現方言(oracle)のサフィックス」でも素名でも
     * 一致しないため解決されない(自動生成へフォールバック → UNRESOLVED)。
     */
    public List findHsqlOnly(Map param) {
        return jdbcManager.selectBySqlFile(Object.class, "findHsqlOnly", param).getResultList();
    }

    /**
     * bySql 候補名のゆらぎ吸収。updateBySqlFile("BonusUpdate", ...) に対し
     * FooService_bonusupdate.sql (UPDATE) をルール3.5 の bySql 候補照合(ci)で解決する。
     */
    public int applyBonus(Map param) {
        return jdbcManager.updateBySqlFile("BonusUpdate", param);
    }
}
