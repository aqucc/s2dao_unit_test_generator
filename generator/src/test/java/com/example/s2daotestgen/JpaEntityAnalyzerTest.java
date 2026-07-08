package com.example.s2daotestgen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.BeforeClass;
import org.junit.Test;

import com.example.s2daotestgen.dao.EntityAnalyzer;
import com.example.s2daotestgen.dao.SourceRepository;
import com.example.s2daotestgen.gen.TestClassGenerator;
import com.example.s2daotestgen.model.MetaModel.EntityMeta;
import com.example.s2daotestgen.model.MetaModel.PropertyMeta;

/**
 * ステップ2: JPA/S2JDBC 風アノテーション(@Entity/@Table/@Column/@Id/@Version/
 * @GeneratedValue/@Transient)のエンティティ解析と、テーブル名逆引き辞書の検証。
 */
public class JpaEntityAnalyzerTest {

    private static final String SRC = "src/test/resources/jpa-entity/java";

    private static SourceRepository repo;
    private static EntityMeta emp;
    private static EntityMeta dept;

    @BeforeClass
    public static void setUp() throws Exception {
        repo = new SourceRepository();
        repo.addSourceRoot(new File(SRC));
        final EntityAnalyzer analyzer = new EntityAnalyzer(repo);
        emp = analyzer.analyze(repo.resolve("fixture.Emp"));
        dept = analyzer.analyze(repo.resolve("fixture.Dept"));
    }

    private static PropertyMeta prop(final EntityMeta e, final String name) {
        for (final PropertyMeta p : e.properties) {
            if (p.propertyName.equals(name)) {
                return p;
            }
        }
        return null;
    }

    @Test
    public void entityIsRecognized() {
        assertTrue(EntityAnalyzer.isEntityLike(repo.resolve("fixture.Emp").decl));
        assertTrue(EntityAnalyzer.isEntityLike(repo.resolve("fixture.Dept").decl));
    }

    @Test
    public void tableNameFromTableAnnotation() {
        assertEquals("EMP", emp.tableName);
        assertEquals("ANNOTATION", emp.tableNameSource);
    }

    @Test
    public void tableNameDefaultsToSimpleName() {
        assertEquals("Dept", dept.tableName);
        assertEquals("DEFAULT_CLASSNAME", dept.tableNameSource);
    }

    @Test
    public void columnNameFromColumnAnnotation() {
        assertEquals("JOB_NAME", prop(emp, "job").columnName);
        assertEquals("ANNOTATION", prop(emp, "job").columnNameSource);
        assertEquals("EMP_ID", prop(emp, "empId").columnName);
    }

    @Test
    public void columnNameDefaultsToPropertyNameWhenNoColumn() {
        // JPA では @Column が無いフィールドも永続対象(カラム名 = プロパティ名)。
        final PropertyMeta ename = prop(emp, "ename");
        assertNotNull(ename);
        assertTrue(ename.persistent);
        assertEquals("ename", ename.columnName);
        assertEquals("DEFAULT_PROPERTYNAME", ename.columnNameSource);
    }

    @Test
    public void primaryKeyFromIdAnnotation() {
        assertTrue(prop(emp, "empId").primaryKey);
        assertEquals("ANNOTATION", emp.primaryKeySource);
        assertEquals("[EMP_ID]", emp.primaryKeyColumns.toString());
    }

    @Test
    public void primaryKeyFromIdOnGetter() {
        // getter アクセス方式の @Id も認識する。
        assertTrue(prop(dept, "deptno").primaryKey);
        assertEquals("ANNOTATION", dept.primaryKeySource);
        assertEquals("[DEPTNO]", dept.primaryKeyColumns.toString());
    }

    @Test
    public void versionFromVersionAnnotation() {
        assertEquals("version", emp.versionNoProperty);
        assertTrue(prop(emp, "version").versionNo);
    }

    @Test
    public void generatedValueMarksProperty() {
        assertTrue(prop(emp, "empId").generated);
        assertFalse(prop(emp, "ename").generated);
    }

    @Test
    public void transientIsNonPersistent() {
        final PropertyMeta temp = prop(emp, "temp");
        assertNotNull(temp);
        assertFalse("@Transient は非永続", temp.persistent);
    }

    @Test
    public void lookupEntityByTableResolvesAcrossCaseVariants() {
        final Map byTable = new LinkedHashMap();
        byTable.put(emp.tableName.toUpperCase(java.util.Locale.ENGLISH), emp);
        byTable.put(dept.tableName.toUpperCase(java.util.Locale.ENGLISH), dept);

        final TestClassGenerator gen = new TestClassGenerator();
        gen.setEntityByTable(byTable);

        assertSame(emp, gen.lookupEntityByTable("EMP"));
        assertSame(emp, gen.lookupEntityByTable("emp"));
        assertSame(emp, gen.lookupEntityByTable("  Emp  "));
        assertSame(dept, gen.lookupEntityByTable("dept"));
        assertNull(gen.lookupEntityByTable("NO_SUCH_TABLE"));
        assertNull(gen.lookupEntityByTable(null));
    }

    @Test
    public void lookupEntityByTableEmptyWhenUnset() {
        final TestClassGenerator gen = new TestClassGenerator();
        assertNull(gen.lookupEntityByTable("EMP"));
    }

    private static void assertSame(final Object expected, final Object actual) {
        assertTrue("同一インスタンスであること", expected == actual);
    }
}
