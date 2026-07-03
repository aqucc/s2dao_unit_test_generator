package com.example.s2daotestgen.dao;

/**
 * DBMS 方言。SQL ファイル/定数のサフィックス解決と自動 SELECT の FROM 句生成に影響する。
 * S2Dao の {@code Dbms#getSuffix()} と同じサフィックスを用いる。
 */
public enum Dialect {

    STANDARD(""),
    ORACLE("_oracle"),
    POSTGRE("_postgre");

    private final String suffix;

    Dialect(final String suffix) {
        this.suffix = suffix;
    }

    public String getSuffix() {
        return suffix;
    }

    public boolean isOracle() {
        return this == ORACLE;
    }

    public static Dialect fromString(final String s) {
        if (s == null) {
            return STANDARD;
        }
        final String v = s.trim().toLowerCase();
        if (v.equals("oracle")) {
            return ORACLE;
        }
        if (v.equals("postgre") || v.equals("postgres")
                || v.equals("postgresql")) {
            return POSTGRE;
        }
        if (v.equals("standard") || v.isEmpty()) {
            return STANDARD;
        }
        throw new IllegalArgumentException("unknown dbms: " + s);
    }
}
