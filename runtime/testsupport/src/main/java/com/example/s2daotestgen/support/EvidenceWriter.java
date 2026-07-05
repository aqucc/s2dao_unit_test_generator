package com.example.s2daotestgen.support;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * DAO 戻り値・操作後データセットを「正規化 CSV」とコンソールへ出力する。
 *
 * <p>新旧環境(Oracle11g+Java5 / PostgreSQL16+Java8)の結果突き合わせのため、
 * 以下の正規化ルールで出力する:</p>
 * <ul>
 *   <li>カラム名は大文字</li>
 *   <li>数値は末尾ゼロ・不要な小数点を除去した正準形(10.0→10, 10.50→10.5)</li>
 *   <li>日付/タイムスタンプは {@code yyyy-MM-dd HH:mm:ss}(日付のみでも時刻付きで統一)</li>
 *   <li>NULL は空文字ではなくリテラル {@code NULL}</li>
 *   <li>行順は呼び出し側(ORDER BY)が保証する</li>
 *   <li>CSV は UTF-8 / LF 改行</li>
 * </ul>
 *
 * <p>Java5 互換構文のみ。</p>
 */
public final class EvidenceWriter {

    private static final String LF = "\n";

    private final File baseDir;

    /** 出力先ディレクトリを指定して生成する。 */
    public EvidenceWriter(File baseDir) {
        this.baseDir = baseDir;
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
    }

    /**
     * DAO 戻り値を {@code <dao>_<method>_return.csv} として出力する。
     * List / 配列 / Map / Bean / スカラのいずれにも対応する。
     */
    public void writeReturn(String daoSimpleName, String methodName, Object returnValue) {
        String fileName = daoSimpleName + "_" + methodName + "_return.csv";
        List rows = toRows(returnValue);
        writeCsv(fileName, rows);
    }

    /**
     * 操作後データセットを {@code <dao>_<method>_dataset_<table>.csv} として出力する。
     *
     * @param dataset {@link GetDatasetUtil#getDataset} が返す {@code List<Map>}。
     */
    public void writeDataset(String daoSimpleName, String methodName, String table, List dataset) {
        String fileName = daoSimpleName + "_" + methodName + "_dataset_" + table + ".csv";
        List rows = new ArrayList();
        for (int i = 0; i < dataset.size(); i++) {
            rows.add((Map) dataset.get(i));
        }
        writeCsv(fileName, rows);
    }

    // ---- 正規化 ----

    /**
     * 単一値を正規化文字列に変換する。null→"NULL"、数値→正準形、日付→yyyy-MM-dd HH:mm:ss。
     * データセットの内容照合(GetDatasetUtil.find)でも共通利用する。
     */
    public static String normalize(Object v) {
        if (v == null) {
            return "NULL";
        }
        if (v instanceof java.util.Date) {
            // java.sql.Date / java.sql.Time / java.sql.Timestamp / java.util.Date すべてを統一
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return fmt.format((java.util.Date) v);
        }
        if (v instanceof Number) {
            return normalizeNumber((Number) v);
        }
        if (v instanceof byte[]) {
            return toHex((byte[]) v);
        }
        return v.toString();
    }

    private static String normalizeNumber(Number n) {
        BigDecimal bd;
        if (n instanceof BigDecimal) {
            bd = (BigDecimal) n;
        } else if (n instanceof Double || n instanceof Float) {
            double d = n.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                // BigDecimal に変換できない特殊値はそのまま文字列化
                return n.toString();
            }
            bd = new BigDecimal(n.toString());
        } else {
            // 整数系はそのまま
            return n.toString();
        }
        if (bd.signum() == 0) {
            return "0";
        }
        bd = bd.stripTrailingZeros();
        return bd.toPlainString();
    }

    private static String toHex(byte[] b) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < b.length; i++) {
            int x = b[i] & 0xff;
            if (x < 16) {
                sb.append('0');
            }
            sb.append(Integer.toHexString(x));
        }
        return sb.toString().toUpperCase(java.util.Locale.ENGLISH);
    }

    // ---- 戻り値 → 行(Map)列への変換 ----

    private List toRows(Object value) {
        List rows = new ArrayList();
        if (value == null) {
            java.util.Map m = new java.util.LinkedHashMap();
            m.put("VALUE", null);
            rows.add(m);
            return rows;
        }
        if (value instanceof Collection) {
            Iterator it = ((Collection) value).iterator();
            while (it.hasNext()) {
                rows.add(toRow(it.next()));
            }
            return rows;
        }
        if (value.getClass().isArray()) {
            int len = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < len; i++) {
                rows.add(toRow(java.lang.reflect.Array.get(value, i)));
            }
            return rows;
        }
        rows.add(toRow(value));
        return rows;
    }

    private java.util.Map toRow(Object element) {
        java.util.Map row = new java.util.LinkedHashMap();
        if (element == null) {
            row.put("VALUE", null);
            return row;
        }
        if (element instanceof Map) {
            Map m = (Map) element;
            Iterator it = m.keySet().iterator();
            while (it.hasNext()) {
                Object k = it.next();
                row.put(String.valueOf(k).toUpperCase(java.util.Locale.ENGLISH), m.get(k));
            }
            return row;
        }
        if (isScalar(element)) {
            row.put("VALUE", element);
            return row;
        }
        // JavaBean: getter を反射で抽出(getClass 除外)、カラム名昇順
        Set names = new TreeSet();
        Method[] methods = element.getClass().getMethods();
        for (int i = 0; i < methods.length; i++) {
            Method m = methods[i];
            String name = beanProperty(m);
            if (name != null) {
                names.add(name);
            }
        }
        Iterator it = names.iterator();
        while (it.hasNext()) {
            String prop = (String) it.next();
            row.put(prop.toUpperCase(java.util.Locale.ENGLISH), invokeGetter(element, prop));
        }
        if (row.isEmpty()) {
            row.put("VALUE", element);
        }
        return row;
    }

    private static boolean isScalar(Object o) {
        return o instanceof Number || o instanceof String || o instanceof Boolean
                || o instanceof Character || o instanceof java.util.Date || o instanceof byte[];
    }

    private static String beanProperty(Method m) {
        if (m.getParameterTypes().length != 0) {
            return null;
        }
        String n = m.getName();
        if (n.equals("getClass")) {
            return null;
        }
        if (n.startsWith("get") && n.length() > 3) {
            return decapitalize(n.substring(3));
        }
        if (n.startsWith("is") && n.length() > 2
                && (m.getReturnType() == Boolean.TYPE || m.getReturnType() == Boolean.class)) {
            return decapitalize(n.substring(2));
        }
        return null;
    }

    private static String decapitalize(String s) {
        if (s.length() == 0) {
            return s;
        }
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private Object invokeGetter(Object bean, String prop) {
        String cap = Character.toUpperCase(prop.charAt(0)) + prop.substring(1);
        Method m = findMethod(bean.getClass(), "get" + cap);
        if (m == null) {
            m = findMethod(bean.getClass(), "is" + cap);
        }
        if (m == null) {
            return null;
        }
        try {
            return m.invoke(bean, (Object[]) null);
        } catch (Exception e) {
            return "<" + e.getClass().getName() + ">";
        }
    }

    private static Method findMethod(Class c, String name) {
        Method[] ms = c.getMethods();
        for (int i = 0; i < ms.length; i++) {
            if (ms[i].getName().equals(name) && ms[i].getParameterTypes().length == 0) {
                return ms[i];
            }
        }
        return null;
    }

    // ---- CSV 出力 + コンソール ----

    private void writeCsv(String fileName, List rows) {
        // ヘッダ = 全行のキー和集合(出現順)
        Set header = new LinkedHashSet();
        for (int i = 0; i < rows.size(); i++) {
            Map row = (Map) rows.get(i);
            header.addAll(row.keySet());
        }
        List cols = new ArrayList(header);

        StringBuffer csv = new StringBuffer();
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0) {
                csv.append(",");
            }
            csv.append(escape((String) cols.get(i)));
        }
        csv.append(LF);
        for (int r = 0; r < rows.size(); r++) {
            Map row = (Map) rows.get(r);
            for (int i = 0; i < cols.size(); i++) {
                if (i > 0) {
                    csv.append(",");
                }
                csv.append(escape(normalize(row.get(cols.get(i)))));
            }
            csv.append(LF);
        }

        String content = csv.toString();
        // コンソール出力(見出し付き)
        System.out.println("---- evidence: " + fileName + " ----");
        System.out.print(content);

        // ファイル出力(UTF-8 / LF)
        File out = new File(baseDir, fileName);
        Writer w = null;
        try {
            w = new OutputStreamWriter(new FileOutputStream(out), "UTF-8");
            w.write(content);
        } catch (IOException e) {
            System.err.println("エビデンス出力失敗: " + out.getAbsolutePath() + " : " + e.getMessage());
        } finally {
            if (w != null) {
                try {
                    w.close();
                } catch (IOException e) {
                    // 無視
                }
            }
        }
    }

    private static String escape(String s) {
        if (s == null) {
            s = "NULL";
        }
        boolean needQuote = s.indexOf(',') >= 0 || s.indexOf('"') >= 0
                || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0;
        if (!needQuote) {
            return s;
        }
        StringBuffer sb = new StringBuffer();
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') {
                sb.append('"');
            }
            sb.append(c);
        }
        sb.append('"');
        return sb.toString();
    }
}
