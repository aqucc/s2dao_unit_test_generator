package com.example.s2daotestgen.support;

import junit.framework.TestCase;

/**
 * ValueFactory の決定性・型対応を検証する(JUnit3 形式)。
 */
public class ValueFactoryTest extends TestCase {

    public void testDeterministic() {
        Object a = ValueFactory.forColumn("java.lang.String", "ename");
        Object b = ValueFactory.forColumn("java.lang.String", "ename");
        assertEquals(a, b);
        // 既知シード
        assertEquals("TESTA", a);
    }

    public void testTypeMapping() {
        assertTrue(ValueFactory.forColumn("int", "deptno") instanceof Integer);
        assertTrue(ValueFactory.forColumn("Integer", "unknownIntCol") instanceof Integer);
        assertTrue(ValueFactory.forColumn("long", "empno") instanceof Long);
        assertTrue(ValueFactory.forColumn("Short", "mgr") instanceof Short);
        assertTrue(ValueFactory.forColumn("Float", "sal") instanceof Float);
        assertTrue(ValueFactory.forColumn("java.sql.Timestamp", "tstamp") instanceof java.sql.Timestamp);
        assertTrue(ValueFactory.forColumn("java.util.Date", "hiredate") instanceof java.util.Date);
        assertTrue(ValueFactory.forColumn("boolean", "flag") instanceof Boolean);
    }

    public void testCanonicalStripsPrefixAndAlias() {
        // emp.dname_0 → dname
        assertEquals("dname", ValueFactory.canonical("emp.dname_0"));
        assertEquals("deptno", ValueFactory.canonical("DEPT.DEPTNO"));
        assertEquals("job", ValueFactory.canonical("job"));
    }

    public void testUnknownColumnStillDeterministic() {
        Object a = ValueFactory.forColumn("int", "someRandomColumn");
        Object b = ValueFactory.forColumn("int", "someRandomColumn");
        assertEquals(a, b);
        int v = ((Integer) a).intValue();
        assertTrue(v >= 10 && v <= 99);
    }
}
