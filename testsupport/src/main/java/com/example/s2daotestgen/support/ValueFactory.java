package com.example.s2daotestgen.support;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Java 型名 / カラム名から決定的なテスト値を生成する。
 *
 * <p><b>乱数・現在時刻は使用しない。</b>固定のシード値表と、String の安定した
 * {@code hashCode}(JLS で規定され JVM 非依存)から算出する。よって新旧環境で
 * 同一カラムに対しては常に同一の値を返す(エビデンス CSV の突き合わせが成立する)。</p>
 *
 * <p>生成テストの setUp では、主キー・絞り込み条件・更新対象など「照合に関わるカラム」は
 * ジェネレーターが具体リテラルを埋め込む。ここで生成するのは、照合に関与しない
 * 「埋め草カラム」の決定的値である。</p>
 *
 * <p>Java5 互換構文のみ。</p>
 */
public final class ValueFactory {

    private ValueFactory() {
    }

    /** カラム名から既定シード値を引くための表(正準化カラム名 → 値)。 */
    private static final Map SEED = new HashMap();
    static {
        SEED.put("empno", Long.valueOf(1001));
        SEED.put("deptno", Integer.valueOf(50));
        SEED.put("ename", "TESTA");
        SEED.put("job", "CLERK");
        SEED.put("mgr", Short.valueOf((short) 7900));
        SEED.put("sal", new BigDecimal("3000"));
        SEED.put("comm", new BigDecimal("500"));
        SEED.put("dname", "SALES");
        SEED.put("loc", "TOKYO");
        SEED.put("versionno", Integer.valueOf(0));
        SEED.put("aaa", "A1");
        SEED.put("bbb", Integer.valueOf(10));
    }

    private static final java.sql.Date SEED_DATE = java.sql.Date.valueOf("1980-12-17");
    private static final java.sql.Timestamp SEED_TS = java.sql.Timestamp.valueOf("2001-01-01 00:00:00");

    /**
     * Java 型名(単純名/完全修飾いずれも可)とカラム名から決定的な値を返す。
     *
     * @param javaType   例: "java.lang.String", "String", "int", "Integer", "long",
     *                   "Short", "Float", "java.util.Date", "java.sql.Timestamp",
     *                   "java.math.BigDecimal", "boolean"
     * @param columnName カラム名(テーブル接頭辞・別名 {@code _n} は自動で除去して照合)
     */
    public static Object forColumn(String javaType, String columnName) {
        String canon = canonical(columnName);
        Object seeded = SEED.get(canon);
        String t = simpleType(javaType);

        if (isDateType(t)) {
            if (isTimestampType(t)) {
                return SEED_TS;
            }
            return SEED_DATE;
        }
        if (isStringType(t)) {
            if (seeded instanceof String) {
                return seeded;
            }
            return deriveString(canon);
        }
        if (isBoolean(t)) {
            return Boolean.valueOf((positiveHash(canon) % 2) == 0);
        }
        // 数値系
        long base = deriveNumber(canon, seeded);
        return toNumber(t, base);
    }

    /** カラム名の正準化: 小文字化 → 最後の '.' 以降 → 末尾別名 {@code _数字} を除去。 */
    static String canonical(String columnName) {
        String c = columnName.toLowerCase().trim();
        int dot = c.lastIndexOf('.');
        if (dot >= 0) {
            c = c.substring(dot + 1);
        }
        int us = c.lastIndexOf('_');
        if (us > 0 && us < c.length() - 1) {
            boolean allDigit = true;
            for (int i = us + 1; i < c.length(); i++) {
                if (!Character.isDigit(c.charAt(i))) {
                    allDigit = false;
                    break;
                }
            }
            if (allDigit) {
                c = c.substring(0, us);
            }
        }
        return c;
    }

    private static String simpleType(String javaType) {
        if (javaType == null) {
            return "String";
        }
        String t = javaType.trim();
        int lt = t.indexOf('<');
        if (lt >= 0) {
            t = t.substring(0, lt);
        }
        int dot = t.lastIndexOf('.');
        if (dot >= 0) {
            t = t.substring(dot + 1);
        }
        return t;
    }

    private static boolean isStringType(String t) {
        return t.equals("String") || t.equals("CharSequence");
    }

    private static boolean isDateType(String t) {
        return t.equals("Date") || t.equals("Timestamp") || t.equals("Time") || t.equals("Calendar");
    }

    private static boolean isTimestampType(String t) {
        return t.equals("Timestamp") || t.equals("Time");
    }

    private static boolean isBoolean(String t) {
        return t.equals("boolean") || t.equals("Boolean");
    }

    private static long deriveNumber(String canon, Object seeded) {
        if (seeded instanceof Number) {
            return ((Number) seeded).longValue();
        }
        return 10 + (positiveHash(canon) % 80); // 10..89
    }

    private static String deriveString(String canon) {
        String base = canon.toUpperCase();
        if (base.length() > 6) {
            base = base.substring(0, 6);
        }
        return base + (positiveHash(canon) % 10);
    }

    private static Object toNumber(String t, long base) {
        if (t.equals("int") || t.equals("Integer")) {
            return Integer.valueOf((int) base);
        }
        if (t.equals("long") || t.equals("Long")) {
            return Long.valueOf(base);
        }
        if (t.equals("short") || t.equals("Short")) {
            return Short.valueOf((short) base);
        }
        if (t.equals("byte") || t.equals("Byte")) {
            return Byte.valueOf((byte) base);
        }
        if (t.equals("float") || t.equals("Float")) {
            return Float.valueOf((float) base);
        }
        if (t.equals("double") || t.equals("Double")) {
            return Double.valueOf((double) base);
        }
        if (t.equals("BigDecimal") || t.equals("BigInteger") || t.equals("Number")) {
            return new BigDecimal(base);
        }
        // 不明型は文字列扱い
        return String.valueOf(base);
    }

    private static int positiveHash(String s) {
        int h = s.hashCode();
        if (h < 0) {
            h = -h;
        }
        if (h < 0) { // Integer.MIN_VALUE 対策
            h = 0;
        }
        return h;
    }
}
