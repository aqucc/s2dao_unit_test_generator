package com.example.s2daotestgen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.junit.BeforeClass;
import org.junit.Test;

import com.example.s2daotestgen.dao.Dialect;
import com.example.s2daotestgen.model.MetaModel.DaoMeta;
import com.example.s2daotestgen.model.MetaModel.MethodMeta;
import com.example.s2daotestgen.model.MetaModel.PropertyMeta;

/**
 * Tiger アノテーション方式(@S2Dao/@Bean/@Column/@Arguments/@Sql)の解析検証。
 */
public class TigerDaoTest {

    private static Map<String, DaoMeta> daos;

    @BeforeClass
    public static void setUp() throws Exception {
        daos = AnalysisFixture.analyze(AnalysisFixture.TIGER_SRC,
                AnalysisFixture.TIGER_SQL, Dialect.ORACLE);
    }

    @Test
    public void s2daoAnnotationResolvesBean() {
        final DaoMeta dao = daos.get("EmployeeDao");
        assertNotNull(dao);
        assertEquals("TIGER", dao.annotationStyle);
        assertEquals("examples.dao.tiger.Employee", dao.beanClassName);
        assertEquals("EMP", dao.entity.tableName);
    }

    @Test
    public void columnAnnotationOnGetterResolved() {
        final DaoMeta dao = daos.get("EmployeeDao");
        PropertyMeta timestamp = null;
        for (final PropertyMeta pm : dao.entity.properties) {
            if (pm.propertyName.equals("timestamp")) {
                timestamp = pm;
            }
        }
        assertNotNull(timestamp);
        assertEquals("tstamp", timestamp.columnName);
        assertEquals("ANNOTATION", timestamp.columnNameSource);
        assertEquals("timestamp", dao.entity.timestampProperty);
    }

    @Test
    public void argumentsAnnotationParsed() {
        final DaoMeta dao = daos.get("EmployeeDao");
        assertEquals("[empno]",
                AnalysisFixture.method(dao, "getEmployee").argNames.toString());
        assertEquals("[job, deptno]",
                AnalysisFixture.method(dao, "getEmployeeByJobDeptno").argNames.toString());
    }

    @Test
    public void sqlAnnotationResolvedAsManual() {
        final MethodMeta m = AnalysisFixture.method(daos.get("EmployeeDao"), "getCount");
        assertEquals("MANUAL_ANNOTATION", m.sql.resolutionType);
        assertEquals("SELECT count(*) FROM emp", m.sql.rawSql);
    }

    @Test
    public void tigerBeginIfSqlFileAnalyzed() {
        final MethodMeta m = AnalysisFixture.method(daos.get("EmployeeDao"),
                "getEmployeeByJobDeptno");
        assertEquals("SQL_FILE", m.sql.resolutionType);
        assertTrue(m.sql.hasBegin);
        assertEquals("[job != null, deptno != null]", m.sql.ifConditions.toString());
        assertEquals("[job, deptno]", m.sql.expandedBindOrder.toString());
    }

    @Test
    public void tigerAutoInsertUpdateDelete() {
        final DaoMeta dao = daos.get("DepartmentDao");
        assertEquals("INSERT INTO DEPT (deptno, dname, loc, versionNo) VALUES (?, ?, ?, ?)",
                AnalysisFixture.method(dao, "insert").sql.rawSql);
        assertEquals("UPDATE DEPT SET dname = ?, loc = ?, versionNo = ? "
                + "WHERE deptno = ? AND versionNo = ?",
                AnalysisFixture.method(dao, "update").sql.rawSql);
        assertEquals("DELETE FROM DEPT WHERE deptno = ? AND versionNo = ?",
                AnalysisFixture.method(dao, "delete").sql.rawSql);
    }
}
