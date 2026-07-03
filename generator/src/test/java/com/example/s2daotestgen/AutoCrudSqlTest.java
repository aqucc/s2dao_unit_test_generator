package com.example.s2daotestgen;

import static org.junit.Assert.assertEquals;

import java.util.Map;

import org.junit.BeforeClass;
import org.junit.Test;

import com.example.s2daotestgen.dao.Dialect;
import com.example.s2daotestgen.model.MetaModel.DaoMeta;
import com.example.s2daotestgen.model.MetaModel.SqlMeta;

/**
 * 自動生成 INSERT/UPDATE/DELETE SQL の組み立て検証(DepartmentDao)。
 * S2Dao 本体 {@code AbstractAutoStaticCommand} と同一の SQL 文字列になることを確認する。
 */
public class AutoCrudSqlTest {

    private static DaoMeta departmentDao;

    @BeforeClass
    public static void setUp() throws Exception {
        final Map<String, DaoMeta> daos = AnalysisFixture.analyze(
                AnalysisFixture.S2DAO_SRC, AnalysisFixture.S2DAO_SQL, Dialect.ORACLE);
        departmentDao = daos.get("DepartmentDao");
    }

    @Test
    public void autoInsertSql() {
        final SqlMeta sql = AnalysisFixture.method(departmentDao, "insert").sql;
        assertEquals("AUTO_INSERT", sql.resolutionType);
        assertEquals("INSERT INTO DEPT (deptno, dname, loc, versionNo) VALUES (?, ?, ?, ?)",
                sql.rawSql);
        assertEquals("[deptno, dname, loc, versionNo]",
                sql.expandedBindOrder.toString());
        assertEquals("INSERT", sql.structure.statementType);
        assertEquals("[DEPT]", sql.structure.tables.toString());
    }

    @Test
    public void autoUpdateSqlIncludesVersionNoInSetAndWhere() {
        final SqlMeta sql = AnalysisFixture.method(departmentDao, "update").sql;
        assertEquals("AUTO_UPDATE", sql.resolutionType);
        assertEquals("UPDATE DEPT SET dname = ?, loc = ?, versionNo = ? "
                + "WHERE deptno = ? AND versionNo = ?", sql.rawSql);
        // SET(dname,loc,versionNo) + WHERE(deptno,versionNo)
        assertEquals("[dname, loc, versionNo, deptno, versionNo]",
                sql.expandedBindOrder.toString());
        assertEquals(2, sql.structure.whereBindings.size());
        assertEquals("deptno", sql.structure.whereBindings.get(0).column);
        assertEquals("versionNo", sql.structure.whereBindings.get(1).column);
    }

    @Test
    public void autoDeleteSqlIncludesVersionNo() {
        final SqlMeta sql = AnalysisFixture.method(departmentDao, "delete").sql;
        assertEquals("AUTO_DELETE", sql.resolutionType);
        assertEquals("DELETE FROM DEPT WHERE deptno = ? AND versionNo = ?", sql.rawSql);
        assertEquals("[deptno, versionNo]", sql.expandedBindOrder.toString());
        assertEquals("DELETE", sql.structure.statementType);
    }
}
