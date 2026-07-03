package com.example.s2daotestgen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.junit.BeforeClass;
import org.junit.Test;

import com.example.s2daotestgen.dao.Dialect;
import com.example.s2daotestgen.model.MetaModel.BindVarMeta;
import com.example.s2daotestgen.model.MetaModel.DaoMeta;
import com.example.s2daotestgen.model.MetaModel.MethodMeta;
import com.example.s2daotestgen.model.MetaModel.SqlMeta;

/**
 * 定数アノテーション方式 EmployeeDao の解析検証。
 * BEAN / _ARGS / _QUERY / _SQL と getEmployeeByJobDeptno.sql の 2-way 解析を対象とする。
 */
public class EmployeeDaoConstantTest {

    private static Map<String, DaoMeta> daos;

    @BeforeClass
    public static void setUp() throws Exception {
        daos = AnalysisFixture.analyze(AnalysisFixture.S2DAO_SRC,
                AnalysisFixture.S2DAO_SQL, Dialect.ORACLE);
    }

    @Test
    public void beanClassResolvedFromBeanConstant() {
        final DaoMeta dao = daos.get("EmployeeDao");
        assertNotNull(dao);
        assertEquals("CONSTANT", dao.annotationStyle);
        assertEquals("examples.dao.Employee", dao.beanClassName);
        assertNotNull(dao.entity);
        assertEquals("EMP", dao.entity.tableName);
        assertEquals("ANNOTATION", dao.entity.tableNameSource);
    }

    @Test
    public void argsConstantParsed() {
        final DaoMeta dao = daos.get("EmployeeDao");
        assertEquals("[empno]",
                AnalysisFixture.method(dao, "getEmployee").argNames.toString());
        assertEquals("[job, deptno]",
                AnalysisFixture.method(dao, "getEmployeeByJobDeptno").argNames.toString());
    }

    @Test
    public void queryConstantParsed() {
        final MethodMeta m = AnalysisFixture.method(daos.get("EmployeeDao"),
                "getEmployeeByDeptno");
        assertNotNull(m.query);
        assertTrue(m.query.contains("deptno != null"));
        assertEquals("AUTO_SELECT_BY_QUERY", m.sql.resolutionType);
        assertTrue(m.sql.ifConditions.contains("deptno != null"));
    }

    @Test
    public void sqlConstantParsedAsManual() {
        final MethodMeta m = AnalysisFixture.method(daos.get("EmployeeDao"),
                "getAllEmployeeNumbers");
        assertEquals("MANUAL_ANNOTATION", m.sql.resolutionType);
        assertEquals("SELECT empno FROM emp", m.sql.rawSql);
    }

    @Test
    public void beginIfSqlFileAnalyzed() {
        final MethodMeta m = AnalysisFixture.method(daos.get("EmployeeDao"),
                "getEmployeeByJobDeptno");
        final SqlMeta sql = m.sql;
        assertEquals("SQL_FILE", sql.resolutionType);
        assertTrue("BEGIN を検出すること", sql.hasBegin);
        assertTrue(sql.twoWay);
        assertEquals("[job != null, deptno != null]", sql.ifConditions.toString());

        // バインド変数(出現順)
        assertEquals(2, sql.bindVariables.size());
        final BindVarMeta b0 = sql.bindVariables.get(0);
        final BindVarMeta b1 = sql.bindVariables.get(1);
        assertEquals("job", b0.expression);
        assertEquals("BIND", b0.kind);
        assertEquals("deptno", b1.expression);

        // 全条件真の展開 SQL
        assertTrue(sql.expandedSql.contains("job = ?"));
        assertTrue(sql.expandedSql.contains("AND deptno = ?"));
        assertEquals("[job, deptno]", sql.expandedBindOrder.toString());

        // 構造解析
        assertEquals("SELECT", sql.structure.statementType);
        assertTrue(sql.structure.tables.contains("emp"));
        assertTrue("* をエンティティメタで展開", sql.structure.selectStarExpanded);
        assertEquals(2, sql.structure.whereBindings.size());
        assertEquals("job", sql.structure.whereBindings.get(0).column);
        assertEquals("job", sql.structure.whereBindings.get(0).bindExpression);
        assertEquals("deptno", sql.structure.whereBindings.get(1).column);
        assertEquals("deptno", sql.structure.whereBindings.get(1).bindExpression);
    }

    @Test
    public void bindVariableInSqlFileWithLiteral() {
        // EmployeeDao_getEmployee.sql: WHERE empno = /*empno*/7788 AND ...
        final MethodMeta m = AnalysisFixture.method(daos.get("EmployeeDao"),
                "getEmployee");
        assertEquals("SQL_FILE", m.sql.resolutionType);
        assertEquals(1, m.sql.bindVariables.size());
        assertEquals("empno", m.sql.bindVariables.get(0).expression);
        assertTrue(m.sql.expandedSql.contains("empno = ?"));
    }

    @Test
    public void relationCapturedAsMetadata() {
        final DaoMeta dao = daos.get("EmployeeDao");
        assertEquals(1, dao.entity.relations.size());
        assertEquals("department", dao.entity.relations.get(0).propertyName);
        assertEquals(0, dao.entity.relations.get(0).relationNo);
    }
}
