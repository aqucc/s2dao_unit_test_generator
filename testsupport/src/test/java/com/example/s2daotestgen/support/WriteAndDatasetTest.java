package com.example.s2daotestgen.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

/**
 * WriteDbUtil / GetDatasetUtil を H2 メモリ DB で検証する(JUnit3 形式)。
 */
public class WriteAndDatasetTest extends TestCase {

    private Connection conn;

    protected void setUp() throws Exception {
        super.setUp();
        Class.forName("org.h2.Driver");
        conn = DriverManager.getConnection("jdbc:h2:mem:wadt;DB_CLOSE_DELAY=-1", "sa", "");
        conn.setAutoCommit(true);
        Statement st = conn.createStatement();
        st.execute("DROP TABLE IF EXISTS DEPT");
        st.execute("CREATE TABLE DEPT (DEPTNO NUMERIC(2) NOT NULL PRIMARY KEY, "
                + "DNAME VARCHAR(14), LOC VARCHAR(13), VERSIONNO NUMERIC(8))");
        st.close();
    }

    protected void tearDown() throws Exception {
        if (conn != null) {
            Statement st = conn.createStatement();
            st.execute("DROP TABLE IF EXISTS DEPT");
            st.close();
            conn.close();
        }
        super.tearDown();
    }

    public void testWriteSingleRowAndGetDataset() throws Exception {
        TestDataParam p = new TestDataParam("DEPT",
                new String[] { "deptno", "dname", "loc", "versionNo" },
                new Object[] { Integer.valueOf(50), "SALES", "TOKYO", Integer.valueOf(0) });
        int n = WriteDbUtil.write(conn, p);
        assertEquals(1, n);

        List ds = GetDatasetUtil.getDataset(conn, "DEPT", new String[] { "deptno" });
        assertEquals(1, ds.size());
        Map row = (Map) ds.get(0);
        // カラム名は大文字に正規化されている
        assertTrue(row.containsKey("DEPTNO"));
        assertTrue(row.containsKey("DNAME"));
        assertEquals("SALES", row.get("DNAME"));
    }

    public void testWriteMultipleRowsOrdered() throws Exception {
        WriteDbUtil.writeRows(conn, "DEPT",
                new String[] { "deptno", "dname", "loc", "versionNo" },
                new Object[][] {
                        new Object[] { Integer.valueOf(30), "A", "X", Integer.valueOf(0) },
                        new Object[] { Integer.valueOf(10), "B", "Y", Integer.valueOf(0) },
                        new Object[] { Integer.valueOf(20), "C", "Z", Integer.valueOf(0) } });

        List ds = GetDatasetUtil.getDataset(conn, "DEPT", new String[] { "deptno" });
        assertEquals(3, ds.size());
        // ORDER BY deptno で昇順であること
        assertEquals("10", EvidenceWriter.normalize(((Map) ds.get(0)).get("DEPTNO")));
        assertEquals("20", EvidenceWriter.normalize(((Map) ds.get(1)).get("DEPTNO")));
        assertEquals("30", EvidenceWriter.normalize(((Map) ds.get(2)).get("DEPTNO")));
    }

    public void testDeleteAllAndFind() throws Exception {
        WriteDbUtil.write(conn, new TestDataParam("DEPT",
                new String[] { "deptno", "dname", "loc", "versionNo" },
                new Object[] { Integer.valueOf(50), "SALES", "TOKYO", Integer.valueOf(0) }));
        List ds = GetDatasetUtil.getDataset(conn, "DEPT", new String[] { "deptno" });
        assertNotNull(GetDatasetUtil.find(ds, "DEPTNO", Integer.valueOf(50)));
        assertNull(GetDatasetUtil.find(ds, "DEPTNO", Integer.valueOf(99)));

        int del = WriteDbUtil.deleteAll(conn, "DEPT");
        assertEquals(1, del);
        List ds2 = GetDatasetUtil.getDataset(conn, "DEPT", new String[] { "deptno" });
        assertEquals(0, ds2.size());
    }

    public void testNullStoredAndNormalized() throws Exception {
        WriteDbUtil.write(conn, new TestDataParam("DEPT",
                new String[] { "deptno", "dname", "loc", "versionNo" },
                new Object[] { Integer.valueOf(60), null, "Z", null }));
        List ds = GetDatasetUtil.getDataset(conn, "DEPT", new String[] { "deptno" });
        Map row = (Map) ds.get(0);
        assertEquals("NULL", EvidenceWriter.normalize(row.get("DNAME")));
    }

    /**
     * null 値は setObject(i, null) ではなく setNull(i, Types.NULL) でバインドされる
     * (Oracle ojdbc は型無し setObject(null) を ORA-17004 で拒否するため)。
     * 文字列カラム・数値カラム双方の null が INSERT でき、NULL として読み戻せること。
     */
    public void testNullBindingOnStringAndNumericColumns() throws Exception {
        int n = WriteDbUtil.write(conn, new TestDataParam("DEPT",
                new String[] { "deptno", "dname", "loc", "versionNo" },
                new Object[] { Integer.valueOf(70), null, null, null }));
        assertEquals(1, n);
        List ds = GetDatasetUtil.getDataset(conn, "DEPT", new String[] { "deptno" });
        Map row = GetDatasetUtil.find(ds, "DEPTNO", Integer.valueOf(70));
        assertNotNull(row);
        assertNull(row.get("DNAME"));
        assertNull(row.get("LOC"));
        assertNull(row.get("VERSIONNO"));
        assertEquals("NULL", EvidenceWriter.normalize(row.get("VERSIONNO")));
    }
}
