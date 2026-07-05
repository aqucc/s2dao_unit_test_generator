package com.example.s2daotestgen.support;

/**
 * DB 方言差の吸収(必要最小限)。
 *
 * <p>
 * 旧環境=Oracle11g / Oracle 互換モードの H2、新環境=PostgreSQL16 の差異のうち、
 * 生成テストの実行(データ投入・データセット取得・正規化)に影響する範囲のみを扱う。
 * DAO 実行そのものは S2Container(dicon)側のドライバ・方言設定に委ねるため、
 * ここでは識別子の扱いなど最小限にとどめる。
 * </p>
 *
 * <p>Java5 互換構文のみ。enum は Java5 で利用可能だが、可読性のため定数インスタンス方式とする。</p>
 */
public final class DbDialect {

    /** Oracle11g 実物。 */
    public static final DbDialect ORACLE = new DbDialect("oracle");
    /** PostgreSQL16。 */
    public static final DbDialect POSTGRE = new DbDialect("postgre");
    /** H2 の Oracle 互換モード(本環境での旧環境相当)。 */
    public static final DbDialect H2_ORACLE = new DbDialect("h2-oracle");

    private final String id;

    private DbDialect(String id) {
        this.id = id;
    }

    /** 方言名を返す。 */
    public String id() {
        return id;
    }

    /**
     * 方言名文字列から {@link DbDialect} を得る。
     * 未知の値・null は {@link #ORACLE}(旧環境相当)を既定とする。
     */
    public static DbDialect fromString(String s) {
        if (s == null) {
            return ORACLE;
        }
        String v = s.trim().toLowerCase(java.util.Locale.ENGLISH);
        if (v.equals("postgre") || v.equals("postgres") || v.equals("postgresql")) {
            return POSTGRE;
        }
        if (v.equals("h2-oracle") || v.equals("h2") || v.equals("h2oracle")) {
            return H2_ORACLE;
        }
        return ORACLE;
    }

    /** Oracle 系(Oracle 実物 / H2 Oracle 互換)かどうか。 */
    public boolean isOracleFamily() {
        return this == ORACLE || this == H2_ORACLE;
    }

    public String toString() {
        return id;
    }
}
