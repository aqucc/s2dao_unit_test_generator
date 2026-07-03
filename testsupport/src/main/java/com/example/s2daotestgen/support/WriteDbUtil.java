package com.example.s2daotestgen.support;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * JDBC 直で INSERT / DELETE を実行するユーティリティ。
 *
 * <p>生成テストの setUp から、対象テーブルのクリーンアップと決定的テストデータの
 * 投入に使用する。複数行投入にも対応する。</p>
 *
 * <p>Java5 互換構文のみ(try-with-resources 不使用)。</p>
 */
public final class WriteDbUtil {

    private WriteDbUtil() {
    }

    /** 1 行を INSERT する。 */
    public static int write(Connection conn, TestDataParam param) throws SQLException {
        return write(conn, new TestDataParam[] { param });
    }

    /**
     * 複数の {@link TestDataParam} を順に INSERT する。
     * 各 param のテーブル・カラムは独立でよい。投入した総行数を返す。
     */
    public static int write(Connection conn, TestDataParam[] params) throws SQLException {
        int total = 0;
        for (int i = 0; i < params.length; i++) {
            total += insertOne(conn, params[i]);
        }
        return total;
    }

    /**
     * 同一テーブル・同一カラム構成で複数行を INSERT する。
     * {@code rows[n]} は columnNames と同じ添字で対応する値配列。
     */
    public static int writeRows(Connection conn, String tableName, String[] columnNames,
            Object[][] rows) throws SQLException {
        int total = 0;
        for (int i = 0; i < rows.length; i++) {
            total += insertOne(conn, new TestDataParam(tableName, columnNames, rows[i]));
        }
        return total;
    }

    private static int insertOne(Connection conn, TestDataParam param) throws SQLException {
        String[] cols = param.getColumnNames();
        Object[] vals = param.getValues();
        StringBuffer sql = new StringBuffer();
        sql.append("INSERT INTO ").append(param.getTableName()).append(" (");
        for (int i = 0; i < cols.length; i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(cols[i]);
        }
        sql.append(") VALUES (");
        for (int i = 0; i < cols.length; i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("?");
        }
        sql.append(")");

        PreparedStatement ps = null;
        try {
            ps = conn.prepareStatement(sql.toString());
            for (int i = 0; i < vals.length; i++) {
                ps.setObject(i + 1, vals[i]);
            }
            return ps.executeUpdate();
        } finally {
            close(ps);
        }
    }

    /** テーブルの全行を DELETE する(クリーンアップ用)。 */
    public static int deleteAll(Connection conn, String tableName) throws SQLException {
        PreparedStatement ps = null;
        try {
            ps = conn.prepareStatement("DELETE FROM " + tableName);
            return ps.executeUpdate();
        } finally {
            close(ps);
        }
    }

    private static void close(PreparedStatement ps) {
        if (ps != null) {
            try {
                ps.close();
            } catch (SQLException e) {
                // クローズ失敗は無視
            }
        }
    }
}
