package com.example.s2daotestgen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.junit.BeforeClass;
import org.junit.Test;

import com.example.s2daotestgen.dao.EntityAnalyzer;
import com.example.s2daotestgen.dao.SourceRepository;
import com.example.s2daotestgen.dao.Dialect;
import com.example.s2daotestgen.gen.GenerationReport;
import com.example.s2daotestgen.gen.TestClassGenerator;
import com.example.s2daotestgen.model.MetaModel.DaoMeta;
import com.example.s2daotestgen.model.MetaModel.EntityMeta;
import com.example.s2daotestgen.model.MetaModel.MethodMeta;
import com.example.s2daotestgen.model.MetaModel.ParamMeta;

/**
 * ステップ3: S2JDBC の Service(具象クラス・BEAN なし・JdbcManager 委譲)対応の回帰テスト。
 *
 * <ul>
 *   <li>具象 Service が対象判定され、抽象基底/エンティティは誤検出されないこと</li>
 *   <li>methodKind が SQL 中身から決まること(find=SELECT / update=UPDATE 等)</li>
 *   <li>戻り値ジェネリクス(List&lt;Emp&gt;)・Map 引数型が解析メタに保持されること</li>
 *   <li>テーブル逆引き辞書(entity.json 補完)で find=強・update=Map 入力の生成ができること</li>
 * </ul>
 */
public class ServiceGenTest {

    private static final String SRC = "../verification/samples/s2jdbc/src/main/java";
    private static final String SQL = "../verification/samples/s2jdbc/src/main/resources";

    private static Map<String, DaoMeta> daos;
    private static String generated;

    @BeforeClass
    public static void setUp() throws Exception {
        daos = AnalysisFixture.analyze(SRC, SQL, Dialect.ORACLE);

        // テーブル逆引き辞書を entity.json 相当(EntityAnalyzer)で構築して生成する。
        final SourceRepository repo = new SourceRepository();
        repo.addSourceRoot(new File(SRC));
        final EntityAnalyzer ea = new EntityAnalyzer(repo);
        final Map<String, Object> registry = new LinkedHashMap<String, Object>();
        final Map<String, Object> byTable = new LinkedHashMap<String, Object>();
        for (final SourceRepository.TypeInfo info : repo.getAllTypes()) {
            if (!EntityAnalyzer.isEntityLike(info.decl)) {
                continue;
            }
            final EntityMeta em = ea.analyze(info);
            registry.put(em.simpleName.toLowerCase(Locale.ENGLISH), em);
            byTable.put(em.tableName.toUpperCase(Locale.ENGLISH), em);
        }
        final TestClassGenerator gen = new TestClassGenerator();
        gen.setEntityRegistry(registry);
        gen.setEntityByTable(byTable);
        final TestClassGenerator.Result r =
                gen.generate(daos.get("EmpService"), null, new GenerationReport());
        generated = r.source;
    }

    @Test
    public void serviceDetectedAndBaseExcluded() {
        assertNotNull("具象 Service が対象判定されるはず", daos.get("EmpService"));
        assertEquals("SERVICE として分類されるはず", "SERVICE", daos.get("EmpService").sourceKind);
        assertNull("抽象基底クラスは対象外", daos.get("AbstractS2JdbcService"));
        assertNull("エンティティ(@Entity)は対象外", daos.get("Emp"));
        assertNull("Manager 名のスタブは対象外", daos.get("JdbcManager"));
    }

    @Test
    public void methodKindFromSql() {
        final DaoMeta svc = daos.get("EmpService");
        assertEquals("SELECT", AnalysisFixture.method(svc, "findByDeptno").methodKind);
        assertEquals("UPDATE", AnalysisFixture.method(svc, "updateSalByEmpno").methodKind);
        assertEquals("INSERT", AnalysisFixture.method(svc, "insertEmp").methodKind);
        assertEquals("DELETE", AnalysisFixture.method(svc, "deleteByEmpno").methodKind);
    }

    @Test
    public void genericsAndMapTypesCaptured() {
        final DaoMeta svc = daos.get("EmpService");
        final MethodMeta find = AnalysisFixture.method(svc, "findByDeptno");
        assertEquals("戻り値ジェネリクスが保持される", "List<Emp>", find.returnType);
        final MethodMeta upd = AnalysisFixture.method(svc, "updateSalByEmpno");
        final ParamMeta p = upd.parameters.get(0);
        assertEquals("Map ジェネリクス型が保持される", "Map<Object,Object>", p.type);
    }

    @Test
    public void findGeneratesStrongAssertions() {
        // 対象テーブルへ投入し、件数と先頭行の主キーを assert する(find=強)。
        assertTrue(generated.indexOf("WriteDbUtil.deleteAll(conn, \"EMP\")") >= 0);
        assertTrue(generated.indexOf("result.size() >= 1") >= 0);
        assertTrue("先頭要素をエンティティにキャストして PK を検証",
                generated.indexOf("(example.s2jdbc.entity.Emp) result.get(0)") >= 0);
        assertTrue(generated.indexOf("row0.getEmpno()") >= 0);
    }

    @Test
    public void updateBuildsMapInputAndAssertsChange() {
        // Map 入力(WHERE=BASE, SET=ALT)を組み立て、更新後 SET 列の変化を assert する。
        assertTrue(generated.indexOf("java.util.Map arg = new java.util.HashMap()") >= 0);
        assertTrue("WHERE 束縛キーは投入値(BASE=1001)",
                generated.indexOf("arg.put(\"empno\", Integer.valueOf(1001))") >= 0);
        assertTrue("SET キーは変化値(ALT=3001)",
                generated.indexOf("arg.put(\"sal\", Integer.valueOf(3001))") >= 0);
        assertTrue(generated.indexOf("updated.get(\"SAL\")") >= 0);
    }

    @Test
    public void deleteAndInsertAssertions() {
        assertTrue("削除後は対象行が無いこと",
                generated.indexOf("assertNull(\"対象行が削除されていること\"") >= 0);
        assertTrue("登録後は対象行があること",
                generated.indexOf("assertNotNull(\"新規行が登録されていること\"") >= 0);
    }
}
