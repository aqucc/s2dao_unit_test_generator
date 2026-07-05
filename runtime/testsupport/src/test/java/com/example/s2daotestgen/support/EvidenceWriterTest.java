package com.example.s2daotestgen.support;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

/**
 * EvidenceWriter の正規化ルールと CSV 出力を検証する(JUnit3 形式)。
 */
public class EvidenceWriterTest extends TestCase {

    public void testNormalizeNumbers() {
        assertEquals("10", EvidenceWriter.normalize(new BigDecimal("10.0")));
        assertEquals("10.5", EvidenceWriter.normalize(new BigDecimal("10.50")));
        assertEquals("3000", EvidenceWriter.normalize(Float.valueOf(3000.0f)));
        assertEquals("10.5", EvidenceWriter.normalize(Double.valueOf(10.5d)));
        assertEquals("0", EvidenceWriter.normalize(new BigDecimal("0.00")));
        assertEquals("7", EvidenceWriter.normalize(Integer.valueOf(7)));
        assertEquals("1001", EvidenceWriter.normalize(Long.valueOf(1001L)));
    }

    public void testNormalizeNull() {
        assertEquals("NULL", EvidenceWriter.normalize(null));
    }

    public void testNormalizeDateAndTimestamp() {
        assertEquals("1980-12-17 00:00:00",
                EvidenceWriter.normalize(java.sql.Date.valueOf("1980-12-17")));
        assertEquals("2001-01-01 09:30:00",
                EvidenceWriter.normalize(java.sql.Timestamp.valueOf("2001-01-01 09:30:00")));
    }

    public void testWriteDatasetCsvFile() throws Exception {
        File dir = new File(System.getProperty("java.io.tmpdir"),
                "evidence-test-" + System.nanoTime());
        EvidenceWriter ew = new EvidenceWriter(dir);

        List ds = new ArrayList();
        Map r1 = new LinkedHashMap();
        r1.put("DEPTNO", Integer.valueOf(10));
        r1.put("DNAME", "ACCOUNTING");
        r1.put("SAL", new BigDecimal("2500.00"));
        r1.put("LOC", null);
        ds.add(r1);

        ew.writeDataset("DepartmentDao", "insert", "DEPT", ds);

        File f = new File(dir, "DepartmentDao_insert_dataset_DEPT.csv");
        assertTrue("CSV が生成されていること", f.exists());
        String content = readFile(f);
        // ヘッダ + 1 行、LF 改行、正規化済み
        String[] lines = content.split("\n");
        assertEquals("DEPTNO,DNAME,SAL,LOC", lines[0]);
        assertEquals("10,ACCOUNTING,2500,NULL", lines[1]);
        // CR を含まない (LF のみ)
        assertTrue(content.indexOf('\r') < 0);
    }

    public void testWriteReturnScalarAndList() throws Exception {
        File dir = new File(System.getProperty("java.io.tmpdir"),
                "evidence-test-" + System.nanoTime());
        EvidenceWriter ew = new EvidenceWriter(dir);

        ew.writeReturn("EmployeeDao", "getCount", Integer.valueOf(14));
        File f1 = new File(dir, "EmployeeDao_getCount_return.csv");
        assertTrue(f1.exists());
        String c1 = readFile(f1);
        assertEquals("VALUE", c1.split("\n")[0]);
        assertEquals("14", c1.split("\n")[1]);

        List list = new ArrayList();
        list.add(Integer.valueOf(1));
        list.add(Integer.valueOf(2));
        ew.writeReturn("EmployeeDao", "getAllEmployeeNumbers", list);
        File f2 = new File(dir, "EmployeeDao_getAllEmployeeNumbers_return.csv");
        assertTrue(f2.exists());
        String c2 = readFile(f2);
        assertEquals(3, c2.split("\n").length); // header + 2 rows
    }

    public void testWriteReturnBeanViaReflection() throws Exception {
        File dir = new File(System.getProperty("java.io.tmpdir"),
                "evidence-test-" + System.nanoTime());
        EvidenceWriter ew = new EvidenceWriter(dir);

        ew.writeReturn("SampleDao", "get", new SampleBean(7, "SCOTT"));
        File f = new File(dir, "SampleDao_get_return.csv");
        assertTrue(f.exists());
        String content = readFile(f);
        String header = content.split("\n")[0];
        // getter からカラムを抽出(昇順、大文字)
        assertEquals("ID,NAME", header);
        assertEquals("7,SCOTT", content.split("\n")[1]);
    }

    private static String readFile(File f) throws Exception {
        java.io.InputStream in = new java.io.FileInputStream(f);
        try {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int n;
            while ((n = in.read(buf)) >= 0) {
                bo.write(buf, 0, n);
            }
            return new String(bo.toByteArray(), "UTF-8");
        } finally {
            in.close();
        }
    }

    /** リフレクション抽出用の単純 Bean。 */
    public static class SampleBean {
        private final int id;
        private final String name;

        public SampleBean(int id, String name) {
            this.id = id;
            this.name = name;
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }
}
