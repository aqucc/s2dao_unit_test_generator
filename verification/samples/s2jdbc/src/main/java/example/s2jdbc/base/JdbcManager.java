package example.s2jdbc.base;

import java.util.List;

/**
 * S2JDBC の {@code org.seasar.extension.jdbc.JdbcManager} を模した最小スタブ。
 *
 * <p>フィクスチャを S2JDBC ランタイム jar 無しでコンパイル可能にするための自己完結
 * インタフェース。実行(実 DB クエリ発行)は利用者環境の実 S2JDBC で行う想定。
 * SQL のパース・バインドは Seasar2 任せ(2-way SQL のバインドコメントがバインドキー)。</p>
 */
public interface JdbcManager {

    /** SQL ファイルによる検索を開始する(結果は {@link SqlFileSelect#getResultList()})。 */
    SqlFileSelect selectBySqlFile(Class entityClass, String path, Object params);

    /** SQL ファイルによる更新(INSERT/UPDATE/DELETE)を実行し、件数を返す。 */
    int updateBySqlFile(String path, Object params);

    /** {@link #selectBySqlFile} の結果ハンドル。 */
    interface SqlFileSelect {
        List getResultList();
    }
}
