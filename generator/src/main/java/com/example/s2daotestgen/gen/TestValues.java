package com.example.s2daotestgen.gen;

import java.util.HashMap;
import java.util.Map;

/**
 * 生成テストへ埋め込む「決定的リテラル値」を Java ソース式として組み立てる。
 *
 * <p>照合(主キー・絞り込み条件・更新対象・期待値 assert)に関わるカラムは、
 * testsupport の {@code ValueFactory}(実行時生成)ではなく本クラスでリテラルを
 * 具体的にソースへ埋め込む。これにより:</p>
 * <ul>
 *   <li>投入データ・引数・期待値が同一の論理値になり、絞り込みが確実にヒットする</li>
 *   <li>人間がレビューできる(値の根拠がコード上に見える)</li>
 * </ul>
 *
 * <p>論理値はカラム名(正準化)から決定的に定まり、対象 Java 型に応じてリテラル表現へ
 * 変換する。同一カラムは型が違っても同じ論理値になる(例: empno は entity では long、
 * 引数では int だが値は同じ 1001)。DDL の桁幅(EMPNO NUMERIC(4) 等)に収まる小さな値を用いる。</p>
 */
public final class TestValues {

    /** 値のバリアント。 */
    public static final int BASE = 0;
    /** 更新後値 / 新規PK など「基準と異なる」値。 */
    public static final int ALT = 1;

    // 【重要】このシード表と canonical()/派生値の規則は、実行時側
    // testsupport の com.example.s2daotestgen.support.ValueFactory と
    // 完全に一致させること(ずれると「投入データに引数がヒットする」保証が壊れる)。
    // 片方を変更する場合は必ず両方を同時に変更する。
    private static final Map SEED_NUM = new HashMap();
    private static final Map SEED_STR = new HashMap();
    static {
        SEED_NUM.put("empno", Long.valueOf(1001));
        SEED_NUM.put("deptno", Long.valueOf(50));
        SEED_NUM.put("mgr", Long.valueOf(7900));
        SEED_NUM.put("sal", Long.valueOf(3000));
        SEED_NUM.put("comm", Long.valueOf(500));
        SEED_NUM.put("versionno", Long.valueOf(0));
        SEED_NUM.put("bbb", Long.valueOf(10));

        SEED_STR.put("ename", "TESTA");
        SEED_STR.put("job", "CLERK");
        SEED_STR.put("dname", "SALES");
        SEED_STR.put("loc", "TOKYO");
        SEED_STR.put("aaa", "A1");
    }

    private TestValues() {
    }

    /** 生成された Java 式と、コメント用の人間可読表現を保持する。 */
    public static final class Value {
        public final String expr;
        public final String display;

        Value(String expr, String display) {
            this.expr = expr;
            this.display = display;
        }
    }

    /** カラム名の正準化(testsupport.ValueFactory と同一規則)。 */
    public static String canonical(String columnName) {
        String c = columnName.toLowerCase(java.util.Locale.ENGLISH).trim();
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
        int arr = t.indexOf('[');
        if (arr >= 0) {
            t = t.substring(0, arr);
        }
        int dot = t.lastIndexOf('.');
        if (dot >= 0) {
            t = t.substring(dot + 1);
        }
        return t.trim();
    }

    static boolean isStringType(String t) {
        return t.equals("String") || t.equals("CharSequence") || t.equals("Object");
    }

    static boolean isDate(String t) {
        return t.equals("Date") || t.equals("Calendar");
    }

    static boolean isTimestamp(String t) {
        return t.equals("Timestamp") || t.equals("Time");
    }

    static boolean isBoolean(String t) {
        return t.equals("boolean") || t.equals("Boolean");
    }

    /**
     * 指定 Java 型・カラム名・バリアントに対するリテラル式を返す。
     */
    public static Value of(String javaType, String columnName, int variant) {
        String canon = canonical(columnName);
        String t = simpleType(javaType);

        if (isTimestamp(t) || (isDate(t) && (t.equals("Timestamp") || t.equals("Time")))) {
            String ts = (variant == ALT) ? "2002-02-02 00:00:00" : "2001-01-01 00:00:00";
            return new Value("java.sql.Timestamp.valueOf(\"" + ts + "\")", ts);
        }
        if (isDate(t)) {
            String d = (variant == ALT) ? "1982-01-23" : "1980-12-17";
            return new Value("java.sql.Date.valueOf(\"" + d + "\")", d);
        }
        if (isBoolean(t)) {
            boolean b = (variant != ALT);
            return new Value("Boolean.valueOf(" + b + ")", String.valueOf(b));
        }
        if (isStringType(t)) {
            String s = seedString(canon);
            if (variant == ALT) {
                s = s + "U";
            }
            return new Value("\"" + escape(s) + "\"", s);
        }
        // 数値系
        long n = seedNumber(canon);
        if (variant == ALT) {
            n = n + 1;
        }
        return renderNumber(t, n);
    }

    private static Value renderNumber(String t, long n) {
        if (t.equals("int") || t.equals("Integer")) {
            return new Value("Integer.valueOf(" + n + ")", String.valueOf(n));
        }
        if (t.equals("long") || t.equals("Long")) {
            return new Value("Long.valueOf(" + n + "L)", String.valueOf(n));
        }
        if (t.equals("short") || t.equals("Short")) {
            return new Value("Short.valueOf((short) " + n + ")", String.valueOf(n));
        }
        if (t.equals("byte") || t.equals("Byte")) {
            return new Value("Byte.valueOf((byte) " + n + ")", String.valueOf(n));
        }
        if (t.equals("float") || t.equals("Float")) {
            return new Value("Float.valueOf(" + n + ".0f)", String.valueOf(n));
        }
        if (t.equals("double") || t.equals("Double")) {
            return new Value("Double.valueOf(" + n + ".0)", String.valueOf(n));
        }
        if (t.equals("BigDecimal") || t.equals("BigInteger") || t.equals("Number")) {
            return new Value("new java.math.BigDecimal(\"" + n + "\")", String.valueOf(n));
        }
        // 不明: 文字列として扱う
        return new Value("\"" + n + "\"", String.valueOf(n));
    }

    private static long seedNumber(String canon) {
        Object v = SEED_NUM.get(canon);
        if (v instanceof Number) {
            return ((Number) v).longValue();
        }
        return 10 + (positiveHash(canon) % 80);
    }

    private static String seedString(String canon) {
        Object v = SEED_STR.get(canon);
        if (v instanceof String) {
            return (String) v;
        }
        String base = canon.toUpperCase(java.util.Locale.ENGLISH);
        if (base.length() > 6) {
            base = base.substring(0, 6);
        }
        return base + (positiveHash(canon) % 10);
    }

    private static int positiveHash(String s) {
        int h = s.hashCode();
        if (h < 0) {
            h = -h;
        }
        if (h < 0) {
            h = 0;
        }
        return h;
    }

    static String escape(String s) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
