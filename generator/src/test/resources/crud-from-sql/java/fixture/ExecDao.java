package fixture;

import java.util.List;
import java.util.Map;

/**
 * CRUD 判定を「メソッド名」ではなく「SQL の中身」で行うことを検証するためのフィクスチャ。
 * S2JDBC の基底クラス委譲のように、メソッド名からは CRUD を読み取れない想定。
 */
public interface ExecDao {

    // 名前は "execute"(insert/update/delete プレフィクスに当たらない → 名前ベースなら SELECT)
    // だが、対応 SQL は UPDATE。SQL 中身優先なら methodKind=UPDATE になるべき。
    int execute(Map arg);

    // 名前は "modify"(名前ベースなら UPDATE)だが、対応 SQL は SELECT。
    // SQL 中身優先なら methodKind=SELECT になるべき。
    List modifySearch(Map arg);
}
