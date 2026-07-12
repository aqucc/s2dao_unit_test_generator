import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import junit.framework.TestSuite;
import junit.framework.TestResult;
import junit.framework.TestFailure;
import junit.textui.TestRunner;

/**
 * 生成テスト実行ドライバ(検証インフラ / Java5 互換)。
 *
 * <p>手順:
 * <ol>
 *   <li>-Ds2daotest.config が指す properties から jdbc.* を読み、DDL ファイルを流してスキーマを作成</li>
 *   <li>引数で与えられた JUnit3 テストクラスを 1 つの TestSuite にまとめて junit.textui.TestRunner で実行</li>
 *   <li>成功なら 0、失敗/エラーがあれば 1 で終了</li>
 * </ol>
 *
 * <p>使い方: {@code java -Ds2daotest.config=xxx.properties RunGeneratedTests <ddl.sql> <TestClass...>}
 */
public final class RunGeneratedTests {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: RunGeneratedTests <ddl.sql> <TestClass...>");
            System.exit(2);
        }
        String ddlFile = args[0];

        Properties props = loadConfig();
        setupSchema(props, ddlFile);

        TestSuite suite = new TestSuite("generated");
        for (int i = 1; i < args.length; i++) {
            // 書式: "pkg.ClassName" または "pkg.ClassName!excludeMethod!excludeMethod..."
            //   除外は「両DBで同一に失敗する sample 由来の SQL 欠陥」等を明示除外するため。
            String token = args[i];
            String[] parts = token.split("!");
            Class c = Class.forName(parts[0]);
            List excludes = new ArrayList();
            for (int j = 1; j < parts.length; j++) {
                excludes.add(parts[j]);
            }
            // メソッド順を名前順に固定して実行する(JVM 間で getMethods 順が変わると
            // テスト間の残存データ状態がずれ、新旧エビデンスが不一致になるため)。
            suite.addTest(buildFiltered(c, excludes));
        }

        System.out.println("==== running " + (args.length - 1) + " test class(es), "
                + suite.countTestCases() + " test case(s) ====");
        TestResult result = new TestResult();
        TestRunner runner = new TestRunner();
        result = runner.doRun(suite, false);

        // 失敗/エラーの明細を出す(調査用)
        printProblems("FAILURE", result.failures());
        printProblems("ERROR", result.errors());

        System.out.println("==== RESULT tests=" + suite.countTestCases()
                + " run=" + result.runCount()
                + " failures=" + result.failureCount()
                + " errors=" + result.errorCount() + " ====");
        System.exit(result.wasSuccessful() ? 0 : 1);
    }

    /** 指定クラスの testXxx メソッドのうち excludes を除いたものだけの TestSuite を作る。 */
    private static TestSuite buildFiltered(Class c, List excludes) throws Exception {
        TestSuite s = new TestSuite();
        s.setName(c.getName());
        java.lang.reflect.Method[] ms = c.getMethods();
        List names = new ArrayList();
        for (int i = 0; i < ms.length; i++) {
            java.lang.reflect.Method m = ms[i];
            if (m.getParameterTypes().length != 0) {
                continue;
            }
            String name = m.getName();
            if (!name.startsWith("test")) {
                continue;
            }
            names.add(name);
        }
        java.util.Collections.sort(names); // 実行順を決定的にする
        for (int i = 0; i < names.size(); i++) {
            String name = (String) names.get(i);
            if (excludes.contains(name)) {
                System.out.println("[excluded] " + c.getName() + "#" + name);
                continue;
            }
            junit.framework.TestCase tc = (junit.framework.TestCase) c.newInstance();
            tc.setName(name);
            s.addTest(tc);
        }
        return s;
    }

    private static Properties loadConfig() throws Exception {
        String cfg = System.getProperty("s2daotest.config");
        Properties p = new Properties();
        if (cfg == null) {
            throw new IllegalStateException("-Ds2daotest.config が未指定です");
        }
        InputStream in = new FileInputStream(cfg);
        try {
            p.load(in);
        } finally {
            in.close();
        }
        return p;
    }

    private static void setupSchema(Properties p, String ddlFile) throws Exception {
        String driver = p.getProperty("jdbc.driver");
        String url = p.getProperty("jdbc.url");
        String user = p.getProperty("jdbc.user");
        String pass = p.getProperty("jdbc.password");
        if (driver != null && driver.length() > 0) {
            Class.forName(driver);
        }
        String ddl = readFile(ddlFile);
        List stmts = splitStatements(ddl);

        Connection conn = DriverManager.getConnection(url, user, pass);
        conn.setAutoCommit(true);
        try {
            for (int i = 0; i < stmts.size(); i++) {
                String sql = ((String) stmts.get(i)).trim();
                if (sql.length() == 0) {
                    continue;
                }
                Statement st = conn.createStatement();
                try {
                    st.execute(sql);
                } catch (Exception e) {
                    // DROP TABLE(存在しない)等は無視して継続
                    System.out.println("[ddl-warn] " + firstLine(sql) + " : " + e.getMessage());
                } finally {
                    st.close();
                }
            }
        } finally {
            conn.close();
        }
        System.out.println("==== schema ready (" + url + ") ====");
    }

    private static List splitStatements(String ddl) {
        List out = new ArrayList();
        StringBuffer cur = new StringBuffer();
        String[] lines = ddl.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (trimmed.startsWith("--")) {
                continue; // コメント行
            }
            cur.append(line).append("\n");
            if (trimmed.endsWith(";")) {
                String s = cur.toString();
                s = s.substring(0, s.lastIndexOf(';'));
                out.add(s);
                cur.setLength(0);
            }
        }
        if (cur.toString().trim().length() > 0) {
            out.add(cur.toString());
        }
        return out;
    }

    private static String readFile(String path) throws Exception {
        InputStream in = new FileInputStream(new File(path));
        try {
            byte[] buf = new byte[in.available()];
            int off = 0;
            int r;
            while (off < buf.length && (r = in.read(buf, off, buf.length - off)) > 0) {
                off += r;
            }
            return new String(buf, 0, off, "UTF-8");
        } finally {
            in.close();
        }
    }

    private static String firstLine(String s) {
        int nl = s.indexOf('\n');
        return nl < 0 ? s : s.substring(0, nl);
    }

    private static void printProblems(String label, java.util.Enumeration en) {
        while (en.hasMoreElements()) {
            TestFailure f = (TestFailure) en.nextElement();
            System.out.println("[" + label + "] " + f.failedTest() + " : " + f.exceptionMessage());
        }
    }

    private RunGeneratedTests() {
    }
}
