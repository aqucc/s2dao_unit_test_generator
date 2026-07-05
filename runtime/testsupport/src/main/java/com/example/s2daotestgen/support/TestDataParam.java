package com.example.s2daotestgen.support;

/**
 * 1 テーブルへ投入する 1 行分のテストデータ。
 *
 * <p>テーブル名・カラム名配列・値配列を保持する単純な値オブジェクト。
 * 値配列の各要素はカラム名配列と同じ添字で対応する。値は JDBC の
 * {@code PreparedStatement.setObject} に渡せる Java オブジェクト
 * (ラッパ型 / String / java.sql.Date / java.sql.Timestamp / null 等)とする。</p>
 *
 * <p>Java5 互換構文のみ。</p>
 */
public final class TestDataParam {

    private final String tableName;
    private final String[] columnNames;
    private final Object[] values;

    public TestDataParam(String tableName, String[] columnNames, Object[] values) {
        if (columnNames.length != values.length) {
            throw new IllegalArgumentException(
                    "カラム数(" + columnNames.length + ")と値数(" + values.length + ")が一致しません: table=" + tableName);
        }
        this.tableName = tableName;
        this.columnNames = columnNames;
        this.values = values;
    }

    public String getTableName() {
        return tableName;
    }

    public String[] getColumnNames() {
        return columnNames;
    }

    public Object[] getValues() {
        return values;
    }
}
