package com.example.s2daotestgen.support;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 操作後テーブルの内容を取得するユーティリティ。
 *
 * <p>{@code SELECT * FROM <table> ORDER BY <pk...>} を実行し、
 * 各行を {@code Map<String,Object>}(カラム名は大文字に正規化・出現順保持)として
 * {@code List} で返す。行順は ORDER BY で保証する(新旧環境の突き合わせのため)。</p>
 *
 * <p>Java5 互換構文のみ。</p>
 */
public final class GetDatasetUtil {

    private GetDatasetUtil() {
    }

    /** ORDER BY 無しで全行取得する。 */
    public static List getDataset(Connection conn, String tableName) throws SQLException {
        return getDataset(conn, tableName, null);
    }

    /**
     * {@code SELECT * FROM tableName [ORDER BY orderByColumns]} を実行して結果を返す。
     *
     * @param orderByColumns ORDER BY するカラム(通常は主キー)。null/空なら ORDER BY 無し。
     * @return 各行を表す {@code Map<String,Object>} の {@code List}。カラム名は大文字。
     */
    public static List getDataset(Connection conn, String tableName, String[] orderByColumns)
            throws SQLException {
        StringBuffer sql = new StringBuffer();
        sql.append("SELECT * FROM ").append(tableName);
        if (orderByColumns != null && orderByColumns.length > 0) {
            sql.append(" ORDER BY ");
            for (int i = 0; i < orderByColumns.length; i++) {
                if (i > 0) {
                    sql.append(", ");
                }
                sql.append(orderByColumns[i]);
            }
        }

        List result = new ArrayList();
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(sql.toString());
            rs = ps.executeQuery();
            ResultSetMetaData md = rs.getMetaData();
            int cols = md.getColumnCount();
            while (rs.next()) {
                Map row = new LinkedHashMap();
                for (int i = 1; i <= cols; i++) {
                    String name = md.getColumnLabel(i);
                    if (name == null) {
                        name = md.getColumnName(i);
                    }
                    row.put(name.toUpperCase(java.util.Locale.ENGLISH), rs.getObject(i));
                }
                result.add(row);
            }
            return result;
        } finally {
            close(rs);
            close(ps);
        }
    }

    /**
     * データセットから、指定カラムが指定値(正規化して比較)に一致する最初の行を返す。
     * 見つからなければ null。カラム名は大文字で照合する。
     * DB 実装差(Integer/BigDecimal/Long 等)を吸収するため {@link EvidenceWriter#normalize} で比較する。
     */
    public static Map find(List dataset, String column, Object expected) {
        String col = column.toUpperCase(java.util.Locale.ENGLISH);
        String want = EvidenceWriter.normalize(expected);
        for (int i = 0; i < dataset.size(); i++) {
            Map row = (Map) dataset.get(i);
            if (want.equals(EvidenceWriter.normalize(row.get(col)))) {
                return row;
            }
        }
        return null;
    }

    /** 指定カラムが指定値に一致する行数を返す(正規化比較)。 */
    public static int count(List dataset, String column, Object expected) {
        String col = column.toUpperCase(java.util.Locale.ENGLISH);
        String want = EvidenceWriter.normalize(expected);
        int n = 0;
        for (int i = 0; i < dataset.size(); i++) {
            Map row = (Map) dataset.get(i);
            if (want.equals(EvidenceWriter.normalize(row.get(col)))) {
                n++;
            }
        }
        return n;
    }

    private static void close(ResultSet rs) {
        if (rs != null) {
            try {
                rs.close();
            } catch (SQLException e) {
                // 無視
            }
        }
    }

    private static void close(PreparedStatement ps) {
        if (ps != null) {
            try {
                ps.close();
            } catch (SQLException e) {
                // 無視
            }
        }
    }
}
