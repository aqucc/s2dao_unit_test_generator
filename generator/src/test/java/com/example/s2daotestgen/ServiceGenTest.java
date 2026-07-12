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
 * ステップ3: 発注者の実構造(ServiceBase 継承の具象 Service・BEAN なし・Map 入力)対応の回帰テスト。
 *
 * <p>Service({@code EmpService})は親 {@code ServiceBase} の
 * {@code findByParams(Class, String co, Map)} / {@code updateByParams(String co, Map)} に委譲する。
 * メソッド名は CRUD 語彙に依存しない(findData/registerData/changeData/removeData)。</p>
 *
 * <ul>
 *   <li>具象 Service が対象判定され、抽象基底({@code ServiceBase})/エンティティは誤検出されないこと</li>
 *   <li>methodKind が SQL 中身から決まること(findData=SELECT / changeData=UPDATE /
 *       registerData=INSERT / removeData=DELETE)</li>
 *   <li>戻り値ジェネリクス(List&lt;Emp&gt;)・Map 引数型が解析メタに保持されること</li>
 *   <li>find=Map 入力(WHERE ヒット値)+件数+先頭行 PK assert、
 *       update=Map 入力(WHERE=BASE, SET=ALT)+変化 assert が生成できること</li>
 * </ul>
 */
public class ServiceGenTest {

    private static final String SRC = "../verification/samples/servicebase/src/main/java";
    private static final String SQL = "../verification/samples/servicebase/src/main/resources";
    private static final String ENTITY_FQ = "example.servicebase.entity.Emp";

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
        assertNull("抽象基底クラス ServiceBase は対象外", daos.get("ServiceBase"));
        assertNull("エンティティ(@Entity)は対象外", daos.get("Emp"));
        assertNull("Manager 名のスタブは対象外", daos.get("JdbcManager"));
    }

    @Test
    public void methodKindFromSql() {
        // メソッド名(findData/registerData/...)からは CRUD を判定できず、SQL 中身で決まる。
        final DaoMeta svc = daos.get("EmpService");
        assertEquals("SELECT", AnalysisFixture.method(svc, "findData").methodKind);
        assertEquals("INSERT", AnalysisFixture.method(svc, "registerData").methodKind);
        assertEquals("UPDATE", AnalysisFixture.method(svc, "changeData").methodKind);
        assertEquals("DELETE", AnalysisFixture.method(svc, "removeData").methodKind);
    }

    @Test
    public void genericsAndMapTypesCaptured() {
        final DaoMeta svc = daos.get("EmpService");
        final MethodMeta find = AnalysisFixture.method(svc, "findData");
        assertEquals("戻り値ジェネリクスが保持される", "List<Emp>", find.returnType);
        assertEquals("find も Map 入力", "Map<Object,Object>", find.parameters.get(0).type);
        final MethodMeta upd = AnalysisFixture.method(svc, "changeData");
        final ParamMeta p = upd.parameters.get(0);
        assertEquals("Map ジェネリクス型が保持される", "Map<Object,Object>", p.type);
    }

    @Test
    public void findWithMapBuildsHitMapCountAndPkAssertions() {
        // find も Map 引数。SQL の bindVariables キーに投入データにヒットする BASE 値を詰めた
        // Map を構築して渡し、件数 + 先頭行 PK を assert する(SELECT + Map 経路)。
        assertTrue("対象テーブルへ決定的データ投入",
                generated.indexOf("WriteDbUtil.deleteAll(conn, \"EMP\")") >= 0);
        assertTrue("Map(HashMap)を構築して渡す",
                generated.indexOf("java.util.Map objobj = new java.util.HashMap()") >= 0);
        assertTrue("WHERE 束縛キー(deptno)に投入値 BASE=50 を詰める",
                generated.indexOf("objobj.put(\"deptno\", Integer.valueOf(50))") >= 0);
        assertTrue("/*IF*/ 内のバインドキー(job)も投入値 BASE を詰める",
                generated.indexOf("objobj.put(\"job\", \"CLERK\")") >= 0);
        assertTrue("dao.findData(objobj) を実行", generated.indexOf("dao.findData(objobj)") >= 0);
        assertTrue("件数 assert", generated.indexOf("result.size() >= 1") >= 0);
        assertTrue("先頭要素を結果エンティティにキャストして PK を検証",
                generated.indexOf("(" + ENTITY_FQ + ") result.get(0)") >= 0);
        assertTrue("先頭行 PK が投入値と一致",
                generated.indexOf("row0.getEmpno()") >= 0);
    }

    @Test
    public void changeBuildsMapInputAndAssertsSetColumnChange() {
        // Map 入力(WHERE=BASE, SET=ALT)を組み立て、更新後 SET 列の変化を assert する。
        assertTrue(generated.indexOf("java.util.Map arg = new java.util.HashMap()") >= 0);
        assertTrue("WHERE 束縛キーは投入値(BASE=1001)",
                generated.indexOf("arg.put(\"empno\", Integer.valueOf(1001))") >= 0);
        assertTrue("SET キーは変化値(ALT=3001)",
                generated.indexOf("arg.put(\"sal\", Integer.valueOf(3001))") >= 0);
        assertTrue("UPDATE 後は SET 列(SAL)の反映を assert",
                generated.indexOf("updated.get(\"SAL\")") >= 0);
    }

    @Test
    public void blockCommentsSeparatedByBlankLine() {
        // ブロック先頭コメントの手前に空行が挿入される整形(separateBlockComments)が
        // Service 経路の生成物にも効いていること(コード行の直後にブロックコメントが続く箇所)。
        assertTrue("実行行の直後のブロックコメント前に空行",
                generated.indexOf("\n\n            // --- 戻り値 assert ---") >= 0);
        assertTrue("波括弧直後(EvidenceWriter 宣言)前にも空行整形",
                generated.indexOf("\n\n            // --- 対象テーブル EMP") >= 0);
    }

    @Test
    public void removeAndRegisterAssertions() {
        // removeData=DELETE(行不在)、registerData=INSERT(行存在)を SQL 種別から判定。
        assertTrue("削除後は対象行が無いこと",
                generated.indexOf("assertNull(\"対象行が削除されていること\"") >= 0);
        assertTrue("登録後は対象行があること",
                generated.indexOf("assertNotNull(\"新規行が登録されていること\"") >= 0);
    }
}
