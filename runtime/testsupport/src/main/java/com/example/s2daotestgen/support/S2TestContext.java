package com.example.s2daotestgen.support;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * 生成テストの実行コンテキスト。
 *
 * <p>設定はプロパティファイルで受け取る。ファイルはシステムプロパティ
 * {@code s2daotest.config} で明示指定でき、無指定時は {@code s2daotest.properties} を
 * クラスパス → カレントディレクトリの順に探索する。</p>
 *
 * <p>キー(すべて任意。DAO 実行しないテストでは jdbc 系のみで足りる):</p>
 * <ul>
 *   <li>{@code jdbc.url} / {@code jdbc.driver} / {@code jdbc.user} / {@code jdbc.password}
 *       … 素の JDBC 接続(データ投入・データセット取得用。<b>DAO と同一 DB</b>を指すこと)</li>
 *   <li>{@code dialect} … oracle / postgre / h2-oracle</li>
 *   <li>{@code dicon} … S2Container の設定パス(例: dao-oracle.dicon)</li>
 *   <li>{@code evidence.dir} … エビデンス CSV 出力先(既定 ./evidence。
 *       システムプロパティ {@code s2daotest.evidence.dir} が優先)</li>
 * </ul>
 *
 * <p>Seasar2 クラスへのコンパイル時依存を持たないため、S2Container の生成・初期化・
 * コンポーネント取得は<b>リフレクション</b>で行う(実行時に classpath へ seasar jar を置く前提)。</p>
 *
 * <p>S2Container の生成(dicon パース+AOP 織り込み)は高コストのため、
 * <b>dicon パス単位で JVM プロセス内に静的キャッシュ</b>して全テストで共有する
 * (大量の DAO テストを一括実行する際の起動コスト削減)。破棄は JVM 終了時の
 * shutdown hook で行い、{@link #close()} はこのインスタンスの参照を手放すのみ。</p>
 *
 * <p>Java5 互換構文のみ。</p>
 */
public final class S2TestContext {

    /** dicon パス → 初期化済み S2Container(プロセス内共有)。 */
    private static final java.util.Map CONTAINERS = new java.util.HashMap();
    private static boolean shutdownHookRegistered = false;

    private final Properties props;
    private final DbDialect dialect;

    private Object container; // org.seasar.framework.container.S2Container (リフレクション)

    public S2TestContext() {
        this(loadProperties());
    }

    public S2TestContext(Properties props) {
        this.props = props;
        this.dialect = DbDialect.fromString(props.getProperty("dialect"));
    }

    // ---- プロパティ読み込み ----

    private static Properties loadProperties() {
        Properties p = new Properties();
        String cfg = System.getProperty("s2daotest.config");
        InputStream in = null;
        try {
            if (cfg != null && cfg.length() > 0) {
                in = new FileInputStream(cfg);
            } else {
                ClassLoader cl = Thread.currentThread().getContextClassLoader();
                if (cl != null) {
                    in = cl.getResourceAsStream("s2daotest.properties");
                }
                if (in == null) {
                    File f = new File("s2daotest.properties");
                    if (f.exists()) {
                        in = new FileInputStream(f);
                    }
                }
            }
            if (in != null) {
                p.load(in);
            }
        } catch (IOException e) {
            throw new RuntimeException("s2daotest 設定の読み込みに失敗しました", e);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    // 無視
                }
            }
        }
        return p;
    }

    // ---- 素の JDBC 接続 ----

    /**
     * データ投入・データセット取得用の素の JDBC 接続を返す。呼び出し側で close する。
     * autoCommit=true(投入が即時に DAO 側から見えるように)。
     */
    public Connection getConnection() throws SQLException {
        String driver = props.getProperty("jdbc.driver");
        String url = props.getProperty("jdbc.url");
        String user = props.getProperty("jdbc.user");
        String password = props.getProperty("jdbc.password");
        if (url == null) {
            throw new IllegalStateException("jdbc.url が設定されていません(s2daotest.properties)");
        }
        if (driver != null && driver.length() > 0) {
            try {
                Class.forName(driver);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("JDBC ドライバが見つかりません: " + driver, e);
            }
        }
        Connection conn = DriverManager.getConnection(url, user, password);
        conn.setAutoCommit(true);
        return conn;
    }

    // ---- S2Container(リフレクション) ----

    /**
     * dicon から S2Container を生成・初期化し、指定 DAO クラスのコンポーネントを取得する。
     * S2Dao の DAO はインタフェースであり、その {@code Class} をキーに getComponent する。
     */
    public Object getComponent(Class componentKey) {
        Object c = container();
        try {
            Method getComponent = c.getClass().getMethod("getComponent", new Class[] { Object.class });
            return getComponent.invoke(c, new Object[] { componentKey });
        } catch (Exception e) {
            throw new RuntimeException("コンポーネント取得に失敗しました: " + componentKey, e);
        }
    }

    private Object container() {
        if (container != null) {
            return container;
        }
        String dicon = props.getProperty("dicon");
        if (dicon == null || dicon.length() == 0) {
            throw new IllegalStateException("dicon が設定されていません(s2daotest.properties)");
        }
        synchronized (CONTAINERS) {
            Object cached = CONTAINERS.get(dicon);
            if (cached != null) {
                container = cached;
                return container;
            }
            try {
                Class factory = Class.forName(
                        "org.seasar.framework.container.factory.S2ContainerFactory");
                Method create = factory.getMethod("create", new Class[] { String.class });
                Object c = create.invoke(null, new Object[] { dicon });
                Method init = c.getClass().getMethod("init", new Class[0]);
                init.invoke(c, new Object[0]);
                CONTAINERS.put(dicon, c);
                registerShutdownHook();
                container = c;
                return container;
            } catch (Exception e) {
                throw new RuntimeException(
                        "S2Container の生成に失敗しました(seasar jar が classpath にあるか確認): dicon=" + dicon, e);
            }
        }
    }

    /** JVM 終了時に共有コンテナを destroy する(初回生成時に一度だけ登録)。 */
    private static void registerShutdownHook() {
        if (shutdownHookRegistered) {
            return;
        }
        shutdownHookRegistered = true;
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                synchronized (CONTAINERS) {
                    java.util.Iterator it = CONTAINERS.values().iterator();
                    while (it.hasNext()) {
                        Object c = it.next();
                        try {
                            Method destroy = c.getClass().getMethod("destroy", new Class[0]);
                            destroy.invoke(c, new Object[0]);
                        } catch (Exception e) {
                            // 破棄失敗は無視
                        }
                    }
                    CONTAINERS.clear();
                }
            }
        });
    }

    // ---- その他 ----

    public DbDialect getDialect() {
        return dialect;
    }

    /** エビデンス出力先(システムプロパティ優先、次にプロパティ、既定 ./evidence)。 */
    public EvidenceWriter newEvidenceWriter() {
        String dir = System.getProperty("s2daotest.evidence.dir");
        if (dir == null || dir.length() == 0) {
            dir = props.getProperty("evidence.dir");
        }
        if (dir == null || dir.length() == 0) {
            dir = "./evidence";
        }
        return new EvidenceWriter(new File(dir));
    }

    /**
     * このインスタンスのコンテナ参照を手放す。
     * コンテナ本体はプロセス内で共有・再利用されるため、ここでは destroy しない
     * (destroy は JVM 終了時の shutdown hook で行う)。
     */
    public void close() {
        container = null;
    }
}
