import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import com.example.s2daotestgen.support.EvidenceWriter;
import com.example.s2daotestgen.support.GetDatasetUtil;
import com.example.s2daotestgen.support.TestDataParam;
import com.example.s2daotestgen.support.WriteDbUtil;

/**
 * Oracle 固有セマンティクスの実動作プローブ(H2 Oracle 互換モードで実行)。
 *
 * <p>実 Oracle 11g が本環境で調達不可のため、旧環境サロゲートである
 * H2 MODE=Oracle 上で testsupport(WriteDbUtil / GetDatasetUtil / EvidenceWriter)
 * の挙動を観測し、「実 Oracle ならどうなるか」との差分を明示する。</p>
 *
 * <p>観測項目:</p>
 * <ol>
 *   <li>(a) 空文字列 INSERT: 実 Oracle は '' を NULL として格納する。
 *       H2 Oracle モードがどちらの挙動かを観測し、EvidenceWriter.normalize が
 *       ''(空欄)と NULL(リテラル NULL)を区別して可視化することを確認する。</li>
 *   <li>(b) DATE 列への時刻付き値: 実 Oracle の DATE は時刻成分を保持する。
 *       H2/PostgreSQL の DATE は日付のみ。normalize が java.sql.Date /
 *       java.sql.Timestamp の型揺れを 1 つの書式(yyyy-MM-dd HH:mm:ss)に
 *       吸収することを確認する。</li>
 *   <li>(c) NUMBER 精度: NUMBER(12,2) 等から返る BigDecimal のスケール差
 *       (3001 vs 3001.00)を normalize が正準形へ揃えることを確認する。</li>
 *   <li>(d) NULL バインド: WriteDbUtil の null が setNull(JDBC3 API)経由で
 *       INSERT できることを確認する(Oracle ojdbc の ORA-17004 回避パス)。</li>
 * </ol>
 *
 * <p>Java5 互換構文のみ。失敗時は AssertionError 相当の RuntimeException で
 * 非0終了する。</p>
 */
public final class OracleSemanticsProbe {

    public static void main(String[] args) throws Exception {
        String url = (args.length > 0) ? args[0]
                : "jdbc:h2:mem:semprobe;MODE=Oracle;DB_CLOSE_DELAY=-1";
        String driver = (args.length > 1) ? args[1] : "org.h2.Driver";
        String user = (args.length > 2) ? args[2] : "sa";
        String pass = (args.length > 3) ? args[3] : "";
        Class.forName(driver);
        Connection conn = DriverManager.getConnection(url, user, pass);
        conn.setAutoCommit(true);
        int problems = 0;
        try {
            setup(conn);
            problems += probeEmptyString(conn);
            probeDateWithTime(conn);
            probeNumberPrecision(conn);
            probeNullBinding(conn);
        } finally {
            conn.close();
        }
        System.out.println();
        System.out.println("==== semantics-probe: PASS (url=" + url + ") ====");
        if (problems > 0) {
            // 現状の観測項目はすべて「観測+正規化確認」であり、ここには来ない
            System.exit(1);
        }
    }

    private static void setup(Connection conn) throws Exception {
        // Oracle / H2 Oracle モードでは NUMBER / VARCHAR2、
        // PostgreSQL 等では標準 SQL 型名(NUMERIC / VARCHAR)を使う
        boolean oracleTypes;
        try {
            oracleTypes = conn.getMetaData().getURL().indexOf("postgresql") < 0;
        } catch (Exception e) {
            oracleTypes = true;
        }
        String num = oracleTypes ? "NUMBER" : "NUMERIC";
        String varchar = oracleTypes ? "VARCHAR2" : "VARCHAR";
        Statement st = conn.createStatement();
        try {
            try {
                st.execute("DROP TABLE SEMPROBE");
            } catch (Exception e) {
                // 初回は存在しない: 無視(PostgreSQL は失敗したトランザクションを
                // 引きずらないよう autoCommit=true で実行している)
            }
            st.execute("CREATE TABLE SEMPROBE ("
                    + "ID " + num + "(4) NOT NULL PRIMARY KEY, "
                    + "SVAL " + varchar + "(30), "
                    + "DVAL DATE, "
                    + "TSVAL TIMESTAMP, "
                    + "NVAL " + num + "(12,2), "
                    + "BIGN " + num + "(19))");
        } finally {
            st.close();
        }
    }

    private static Map rowOf(Connection conn, int id) throws Exception {
        List ds = GetDatasetUtil.getDataset(conn, "SEMPROBE", new String[] { "id" });
        Map row = GetDatasetUtil.find(ds, "ID", Integer.valueOf(id));
        if (row == null) {
            throw new RuntimeException("行が見つかりません: id=" + id);
        }
        return row;
    }

    // ---- (a) 空文字列 = NULL 問題 ----

    private static int probeEmptyString(Connection conn) throws Exception {
        WriteDbUtil.write(conn, new TestDataParam("SEMPROBE",
                new String[] { "id", "sval" },
                new Object[] { Integer.valueOf(1), "" }));
        Object back = rowOf(conn, 1).get("SVAL");
        String norm = EvidenceWriter.normalize(back);
        System.out.println("(a) 空文字列 INSERT -> 読み戻し値 = "
                + (back == null ? "null" : "\"" + back + "\" (" + back.getClass().getName() + ")")
                + " / normalize = " + (norm.length() == 0 ? "(空欄)" : norm));
        if (back == null) {
            System.out.println("    -> この DB は Oracle 同様 '' を NULL として格納した");
        } else {
            System.out.println("    -> この DB は '' を空文字のまま格納した(PostgreSQL 等)。"
                    + "【実 Oracle 11g では NULL になり、エビデンスは 'NULL' と出力される。"
                    + "テストデータに空文字を使うと新旧エビデンスが食い違うため、"
                    + "ValueFactory / TestValues は空文字を生成しない】");
        }
        // normalize が NULL と空文字を別表現にすること(差異が黙って消えない)を確認
        assertEquals("NULL", EvidenceWriter.normalize(null), "normalize(null)");
        assertEquals("", EvidenceWriter.normalize(""), "normalize(\"\")");
        return 0;
    }

    // ---- (b) DATE 列への時刻付き値 ----

    private static void probeDateWithTime(Connection conn) throws Exception {
        java.sql.Timestamp withTime = java.sql.Timestamp.valueOf("2001-01-01 12:34:56");
        WriteDbUtil.write(conn, new TestDataParam("SEMPROBE",
                new String[] { "id", "dval", "tsval" },
                new Object[] { Integer.valueOf(2), withTime, withTime }));
        Map row = rowOf(conn, 2);
        Object d = row.get("DVAL");
        Object ts = row.get("TSVAL");
        String normD = EvidenceWriter.normalize(d);
        System.out.println("(b) DATE 列へ時刻付き値 " + withTime + " を INSERT:");
        System.out.println("    DATE 列   -> " + d.getClass().getName()
                + " / normalize = " + normD);
        System.out.println("    TIMESTAMP 列 -> " + ts.getClass().getName()
                + " / normalize = " + EvidenceWriter.normalize(ts));
        if (normD.endsWith("12:34:56")) {
            System.out.println("    -> この DB は Oracle 同様 DATE 列でも時刻を保持した"
                    + "(H2 Oracle モードの DATE は TIMESTAMP 相当)。"
                    + "【PostgreSQL の DATE は時刻を持たず 00:00:00 になるため、"
                    + "DATE 列に時刻付き値を入れると新旧エビデンスは不一致になる。"
                    + "生成テストデータは DATE 列に java.sql.Date(時刻なし)のみを使う】");
        } else {
            System.out.println("    -> この DB の DATE は時刻を切り捨てた。"
                    + "【実 Oracle の DATE は 12:34:56 を保持する】");
        }
        System.out.println("    【実 Oracle でのドライバ差: ojdbc14(10.2)の getObject(DATE列) は"
                + " java.sql.Date を返し時刻が消える(-Doracle.jdbc.V8Compatible=true で"
                + " Timestamp になり保持)。ojdbc5/6(11g)は java.sql.Timestamp を返し保持。"
                + " EvidenceWriter.normalize は Date/Timestamp とも yyyy-MM-dd HH:mm:ss に"
                + " 揃えるため、時刻なしデータなら戻り型の揺れはエビデンスに現れない】");
        // 型揺れの吸収確認: 同じ壁時計値なら Date/Timestamp どちらでも同一表現になる
        String normDate = EvidenceWriter.normalize(java.sql.Date.valueOf("2001-01-01"));
        String normTs = EvidenceWriter.normalize(java.sql.Timestamp.valueOf("2001-01-01 00:00:00"));
        assertEquals(normDate, normTs, "java.sql.Date と java.sql.Timestamp の正規化一致");
        // TIMESTAMP 列は時刻が保持され、両環境で同一表現になる
        assertEquals("2001-01-01 12:34:56", EvidenceWriter.normalize(ts), "TIMESTAMP 列の時刻保持");
    }

    // ---- (c) NUMBER 精度と BigDecimal 正規化 ----

    private static void probeNumberPrecision(Connection conn) throws Exception {
        WriteDbUtil.write(conn, new TestDataParam("SEMPROBE",
                new String[] { "id", "nval", "bign" },
                new Object[] { Integer.valueOf(3), new BigDecimal("3001"),
                        Long.valueOf(1234567890123456789L) }));
        WriteDbUtil.write(conn, new TestDataParam("SEMPROBE",
                new String[] { "id", "nval" },
                new Object[] { Integer.valueOf(4), new BigDecimal("0.50") }));
        Map r3 = rowOf(conn, 3);
        Map r4 = rowOf(conn, 4);
        Object n3 = r3.get("NVAL");
        Object b3 = r3.get("BIGN");
        Object n4 = r4.get("NVAL");
        System.out.println("(c) NUMBER(12,2) へ 3001 を INSERT -> "
                + n3.getClass().getName() + " " + n3 + " / normalize = " + EvidenceWriter.normalize(n3));
        System.out.println("    NUMBER(12,2) へ 0.50 を INSERT -> "
                + n4.getClass().getName() + " " + n4 + " / normalize = " + EvidenceWriter.normalize(n4));
        System.out.println("    NUMBER(19) へ 1234567890123456789 を INSERT -> "
                + b3.getClass().getName() + " " + b3 + " / normalize = " + EvidenceWriter.normalize(b3));
        System.out.println("    -> 実 Oracle の getObject は NUMBER を常に BigDecimal で返す"
                + "(スケールは格納値依存: 3001 or 3001.00)。normalize が末尾ゼロを除去して"
                + "正準形へ揃えるため、Integer/Long/BigDecimal の実装差はエビデンスに現れない");
        // どの DB でも同一の正準形になること
        assertEquals("3001", EvidenceWriter.normalize(n3), "NUMBER 整数値の正準形");
        assertEquals("0.5", EvidenceWriter.normalize(n4), "NUMBER 小数値の正準形");
        assertEquals("1234567890123456789", EvidenceWriter.normalize(b3), "NUMBER(19) 大整数の正準形");
        // Oracle が返しうるスケール付き BigDecimal と、他 DB の整数型が同一表現になること
        assertEquals(EvidenceWriter.normalize(new BigDecimal("3001.00")),
                EvidenceWriter.normalize(Integer.valueOf(3001)), "BigDecimal(3001.00) vs Integer(3001)");
        assertEquals(EvidenceWriter.normalize(new BigDecimal("0.500")),
                EvidenceWriter.normalize(Double.valueOf(0.5)), "BigDecimal(0.500) vs Double(0.5)");
    }

    // ---- (d) NULL バインド(ORA-17004 回避パス) ----

    private static void probeNullBinding(Connection conn) throws Exception {
        WriteDbUtil.write(conn, new TestDataParam("SEMPROBE",
                new String[] { "id", "sval", "nval", "dval" },
                new Object[] { Integer.valueOf(5), null, null, null }));
        Map row = rowOf(conn, 5);
        System.out.println("(d) null 値 3 カラム(VARCHAR2/NUMBER/DATE)を setNull で INSERT -> "
                + "SVAL=" + EvidenceWriter.normalize(row.get("SVAL"))
                + " NVAL=" + EvidenceWriter.normalize(row.get("NVAL"))
                + " DVAL=" + EvidenceWriter.normalize(row.get("DVAL")));
        assertEquals("NULL", EvidenceWriter.normalize(row.get("SVAL")), "SVAL null");
        assertEquals("NULL", EvidenceWriter.normalize(row.get("NVAL")), "NVAL null");
        assertEquals("NULL", EvidenceWriter.normalize(row.get("DVAL")), "DVAL null");
        System.out.println("    -> WriteDbUtil は null を setNull(i, Types.NULL) でバインドする"
                + "(Oracle ojdbc は型無し setObject(i, null) を ORA-17004 で拒否するため)");
    }

    private static void assertEquals(String expected, String actual, String what) {
        if (!expected.equals(actual)) {
            throw new RuntimeException("プローブ失敗: " + what
                    + " expected=<" + expected + "> actual=<" + actual + ">");
        }
    }

    private OracleSemanticsProbe() {
    }
}
