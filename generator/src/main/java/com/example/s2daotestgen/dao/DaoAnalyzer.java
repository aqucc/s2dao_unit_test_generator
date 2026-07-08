package com.example.s2daotestgen.dao;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.example.s2daotestgen.model.MetaModel.BindVarMeta;
import com.example.s2daotestgen.model.MetaModel.DaoMeta;
import com.example.s2daotestgen.model.MetaModel.EntityMeta;
import com.example.s2daotestgen.model.MetaModel.MethodMeta;
import com.example.s2daotestgen.model.MetaModel.ParamMeta;
import com.example.s2daotestgen.model.MetaModel.SqlMeta;
import com.example.s2daotestgen.sql.AutoSqlBuilder;
import com.example.s2daotestgen.sql.SqlFileIndex;
import com.example.s2daotestgen.sql.SqlStructureAnalyzer;
import com.example.s2daotestgen.sql.TwoWaySqlAnalyzer;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.Expression;

/**
 * DAO インタフェースを S2Dao 規約(定数/Tiger アノテーション)に従って解析し、
 * メソッド→SQL 解決・2-way SQL 解析・構造解析を行って {@link DaoMeta} を構築する。
 *
 * <p>
 * 解決順は S2Dao 本体 {@code DaoMetaDataImpl#setupMethod} と同一:
 * 手書き SQL(_SQL/@Sql)→ SQL ファイル → 自動生成。
 * </p>
 */
public final class DaoAnalyzer {

    private static final Set<String> SIMPLE_TYPES = new HashSet<String>(Arrays.asList(
            "int", "long", "short", "byte", "char", "boolean", "float", "double",
            "Integer", "Long", "Short", "Byte", "Character", "Boolean", "Float",
            "Double", "String", "Number", "BigDecimal", "BigInteger", "Date",
            "Timestamp", "Time", "java.lang.String", "java.util.Date",
            "java.sql.Date", "java.sql.Timestamp", "java.sql.Time",
            "java.math.BigDecimal", "java.math.BigInteger", "Object"));

    private final SourceRepository repo;
    private final SqlFileIndex sqlIndex;
    private final Dialect dialect;
    private final EntityAnalyzer entityAnalyzer;
    private final AutoSqlBuilder autoSql;
    private final TwoWaySqlAnalyzer twoWay = new TwoWaySqlAnalyzer();
    private final SqlStructureAnalyzer structure = new SqlStructureAnalyzer();

    public DaoAnalyzer(final SourceRepository repo, final SqlFileIndex sqlIndex,
            final Dialect dialect) {
        this.repo = repo;
        this.sqlIndex = sqlIndex;
        this.dialect = dialect;
        this.entityAnalyzer = new EntityAnalyzer(repo);
        this.autoSql = new AutoSqlBuilder(dialect);
    }

    public boolean isDao(final SourceRepository.TypeInfo info) {
        final TypeDeclaration<?> type = info.decl;
        if (!(type instanceof ClassOrInterfaceDeclaration)) {
            return false;
        }
        final ClassOrInterfaceDeclaration c = (ClassOrInterfaceDeclaration) type;
        if (!c.isInterface()) {
            return false;
        }
        if (AstUtil.hasAnnotation(type, "S2Dao")) {
            return true;
        }
        if (AstUtil.hasStaticField(type, "BEAN")) {
            return true;
        }
        return info.simpleName.endsWith("Dao");
    }

    public DaoMeta analyze(final SourceRepository.TypeInfo info) {
        final TypeDeclaration<?> type = info.decl;
        final DaoMeta dao = new DaoMeta();
        dao.daoClassName = info.fqn;
        dao.daoSimpleName = info.simpleName;
        dao.packageName = info.packageName;
        dao.sourceFile = info.sourceFile.getAbsolutePath();

        final AnnotationExpr s2dao = AstUtil.getAnnotation(type, "S2Dao");
        dao.annotationStyle = (s2dao != null) ? "TIGER" : "CONSTANT";

        // --- bean クラス解決 ---
        String beanName = AstUtil.getStaticClassField(type, "BEAN");
        if (beanName == null && s2dao != null) {
            beanName = classMemberType(s2dao, "bean");
        }
        EntityMeta entity = null;
        if (beanName != null) {
            final SourceRepository.TypeInfo beanInfo = repo.resolve(beanName);
            if (beanInfo != null) {
                entity = entityAnalyzer.analyze(beanInfo);
                dao.beanClassName = entity.className;
            } else {
                dao.beanClassName = beanName;
                dao.notes.add("bean クラス '" + beanName + "' のソースが見つからないため、"
                        + "エンティティメタと自動生成 SQL は解決できません。");
            }
        }
        dao.entity = entity;

        for (final MethodDeclaration md : type.findAll(MethodDeclaration.class)) {
            // インタフェース直下のメソッドのみ(ネスト型除外)
            if (!md.getParentNode().isPresent()
                    || md.getParentNode().get() != type) {
                continue;
            }
            dao.methods.add(analyzeMethod(dao, type, md, entity));
        }
        return dao;
    }

    private MethodMeta analyzeMethod(final DaoMeta dao,
            final TypeDeclaration<?> type, final MethodDeclaration md,
            final EntityMeta entity) {
        final MethodMeta mm = new MethodMeta();
        final String name = md.getNameAsString();
        mm.name = name;
        mm.returnType = md.getType().asString();
        for (final Parameter p : md.getParameters()) {
            final ParamMeta pm = new ParamMeta();
            pm.name = p.getNameAsString();
            pm.type = p.getType().asString();
            mm.parameters.add(pm);
        }

        // --- メソッド単位アノテーション ---
        mm.argNames.addAll(readArgNames(type, md, name));
        mm.query = readQuery(type, md, name);
        mm.noPersistentProps.addAll(readProps(type, md, name, "_NO_PERSISTENT_PROPS", "NoPersistentProperty"));
        mm.persistentProps.addAll(readProps(type, md, name, "_PERSISTENT_PROPS", "PersistentProperty"));

        final String procedureName = readProcedure(type, md, name);
        if (procedureName != null) {
            mm.methodKind = "PROCEDURE";
            mm.procedureName = procedureName;
            return mm;
        }

        mm.methodKind = DaoNaming.methodKind(name);

        // --- SQL 解決(DaoMetaDataImpl と同一順) ---
        final String manualSql = readManualSql(type, md, name);
        if (manualSql != null) {
            mm.sql = buildSqlMeta("MANUAL_ANNOTATION", manualSql, null, entity);
            refineKindFromSql(mm);
            return mm;
        }

        // SQL ファイル
        final String base = dao.daoSimpleName + "_" + name;
        File sqlFile = sqlIndex.find(base + dialect.getSuffix());
        if (sqlFile == null) {
            sqlFile = sqlIndex.find(base);
        }
        if (sqlFile != null) {
            try {
                final String content = sqlIndex.read(sqlFile);
                mm.sql = buildSqlMeta("SQL_FILE", content, sqlFile.getAbsolutePath(), entity);
                refineKindFromSql(mm);
            } catch (final IOException e) {
                dao.notes.add("SQL ファイル読込失敗: " + sqlFile + " (" + e.getMessage() + ")");
            }
            return mm;
        }

        // delete + query(ファイル無し)
        if (DaoNaming.isDelete(name) && mm.query != null && entity != null) {
            final AutoSqlBuilder.Result r = autoSql.buildDeleteByQuery(entity, mm.query);
            mm.sql = buildSqlMetaFromResult(r, entity);
            return mm;
        }

        // 自動生成
        if (entity == null) {
            mm.sql = unresolved("bean クラス未解決のため自動生成 SQL を構築できません。");
            return mm;
        }
        mm.sql = buildAutoSql(mm, entity);
        return mm;
    }

    /**
     * 明示 SQL(手書き _SQL/@Sql・SQL ファイル)がある場合は、SQL 文の先頭トークンから
     * 判定した文種別({@code structure.statementType})で {@code methodKind} を上書きする。
     *
     * <p>これによりメソッド名プレフィクスの命名に依存せず「実際に DB が行う操作」で
     * CRUD 種別を決められる(S2JDBC の基底クラス委譲のように、メソッド名から CRUD を
     * 読み取れないケースに対応)。文種別が不定({@code OTHER} や構造未解析)のときは、
     * 既に設定済みのメソッド名ベースの種別を維持する。自動生成 SQL(AUTO_*)は
     * SQL 自体がメソッド名から組み立てられるため、ここでは触らない。</p>
     */
    private void refineKindFromSql(final MethodMeta mm) {
        if (mm.sql == null || mm.sql.structure == null) {
            return;
        }
        final String st = mm.sql.structure.statementType;
        if ("SELECT".equals(st) || "INSERT".equals(st)
                || "UPDATE".equals(st) || "DELETE".equals(st)) {
            mm.methodKind = st;
        }
    }

    private SqlMeta buildAutoSql(final MethodMeta mm, final EntityMeta entity) {
        final String name = mm.name;
        if (DaoNaming.isInsert(name)) {
            final List<String> props = autoSql.persistentPropertyNames(entity,
                    mm.noPersistentProps, mm.persistentProps);
            return buildSqlMetaFromResult(autoSql.buildInsert(entity, props), entity);
        }
        if (DaoNaming.isUpdate(name)) {
            final List<String> props = autoSql.persistentPropertyNames(entity,
                    mm.noPersistentProps, mm.persistentProps);
            return buildSqlMetaFromResult(autoSql.buildUpdate(entity, props), entity);
        }
        if (DaoNaming.isDelete(name)) {
            return buildSqlMetaFromResult(autoSql.buildDelete(entity), entity);
        }
        // SELECT
        if (mm.query != null && !startsWithOrderBy(mm.query)) {
            return buildSqlMetaFromResult(autoSql.buildAutoSelectByQuery(entity, mm.query), entity);
        }
        if (isAutoSelectByDto(mm)) {
            final EntityMeta dto = resolveDto(mm.parameters.get(0).type);
            return buildSqlMetaFromResult(autoSql.buildAutoSelectByDto(entity, dto, mm.query), entity);
        }
        return buildSqlMetaFromResult(autoSql.buildAutoSelectByArgs(entity, mm.argNames), entity);
    }

    private boolean isAutoSelectByDto(final MethodMeta mm) {
        if (!mm.argNames.isEmpty()) {
            return false;
        }
        if (mm.parameters.size() == 1) {
            return !isSimpleType(mm.parameters.get(0).type);
        }
        return false;
    }

    private EntityMeta resolveDto(final String typeName) {
        final SourceRepository.TypeInfo info = repo.resolve(typeName);
        if (info == null) {
            return null;
        }
        return entityAnalyzer.analyze(info);
    }

    // ---------- SqlMeta 構築 ----------

    /** 手書き/ファイル由来の 2-way SQL からメタを構築。 */
    private SqlMeta buildSqlMeta(final String resolutionType, final String rawSql,
            final String sourceFile, final EntityMeta entity) {
        final SqlMeta sm = new SqlMeta();
        sm.resolutionType = resolutionType;
        sm.rawSql = rawSql;
        sm.sourceFile = sourceFile;
        final TwoWaySqlAnalyzer.Result r = twoWay.analyze(rawSql);
        sm.bindVariables.addAll(r.bindVariables);
        sm.embeddedVariables.addAll(r.embeddedVariables);
        sm.ifConditions.addAll(r.ifConditions);
        sm.hasBegin = r.hasBegin;
        sm.twoWay = r.twoWay;
        sm.expandedSql = r.expandedSql;
        sm.expandedBindOrder.addAll(r.expandedBindOrder);
        sm.structure = structure.analyze(sm.expandedSql, entity, sm.expandedBindOrder);
        return sm;
    }

    /** 自動生成結果からメタを構築。静的 CRUD は位置バインドを保持する。 */
    private SqlMeta buildSqlMetaFromResult(final AutoSqlBuilder.Result r,
            final EntityMeta entity) {
        final SqlMeta sm = new SqlMeta();
        sm.resolutionType = r.resolutionType;
        sm.rawSql = r.sql;
        if (r.bindProps != null) {
            // 静的 CRUD: "?" は位置バインド。2-way コメントは無い。
            sm.twoWay = false;
            sm.expandedSql = r.sql;
            sm.expandedBindOrder.addAll(r.bindProps);
            for (final String prop : r.bindProps) {
                final BindVarMeta b = new BindVarMeta();
                b.expression = prop;
                b.rootParam = prop;
                b.propertyPath = "";
                b.kind = "STATIC";
                sm.bindVariables.add(b);
            }
        } else {
            // 自動生成された 2-way SQL(SELECT 系)
            final TwoWaySqlAnalyzer.Result tr = twoWay.analyze(r.sql);
            sm.bindVariables.addAll(tr.bindVariables);
            sm.embeddedVariables.addAll(tr.embeddedVariables);
            sm.ifConditions.addAll(tr.ifConditions);
            sm.hasBegin = tr.hasBegin;
            sm.twoWay = tr.twoWay;
            sm.expandedSql = tr.expandedSql;
            sm.expandedBindOrder.addAll(tr.expandedBindOrder);
        }
        sm.structure = structure.analyze(sm.expandedSql, entity, sm.expandedBindOrder);
        return sm;
    }

    private SqlMeta unresolved(final String note) {
        final SqlMeta sm = new SqlMeta();
        sm.resolutionType = "UNRESOLVED";
        sm.rawSql = null;
        sm.ifConditions.add(note);
        return sm;
    }

    // ---------- アノテーション読み取り ----------

    private List<String> readArgNames(final TypeDeclaration<?> type,
            final MethodDeclaration md, final String name) {
        // 定数: <m>_ARGS を " ," で分割
        final String argsConst = AstUtil.getStaticStringField(type, name + "_ARGS");
        if (argsConst != null) {
            return splitBy(argsConst, " ,");
        }
        // Tiger: @Arguments
        final List<String> tiger = AstUtil.getAnnotationStringArray(md, "Arguments");
        if (tiger != null) {
            return tiger;
        }
        return new ArrayList<String>();
    }

    private String readQuery(final TypeDeclaration<?> type,
            final MethodDeclaration md, final String name) {
        final String q = AstUtil.getStaticStringField(type, name + "_QUERY");
        if (q != null) {
            return q;
        }
        return AstUtil.getAnnotationStringValue(md, "Query");
    }

    private List<String> readProps(final TypeDeclaration<?> type,
            final MethodDeclaration md, final String name, final String suffix,
            final String tigerAnn) {
        final String c = AstUtil.getStaticStringField(type, name + suffix);
        if (c != null) {
            return splitBy(c, ", ");
        }
        final List<String> tiger = AstUtil.getAnnotationStringArray(md, tigerAnn);
        if (tiger != null) {
            return tiger;
        }
        return new ArrayList<String>();
    }

    private String readProcedure(final TypeDeclaration<?> type,
            final MethodDeclaration md, final String name) {
        String p = AstUtil.getStaticStringField(type, name + "_PROCEDURE_CALL");
        if (p != null) {
            return p;
        }
        p = AstUtil.getStaticStringField(type, name + "_PROCEDURE");
        if (p != null) {
            return p;
        }
        final String callAnn = AstUtil.getAnnotationStringValue(md, "ProcedureCall");
        if (callAnn != null) {
            return callAnn;
        }
        return AstUtil.getAnnotationStringValue(md, "Procedure");
    }

    private String readManualSql(final TypeDeclaration<?> type,
            final MethodDeclaration md, final String name) {
        // 定数: <m><dbmsSuffix>_SQL → <m>_SQL
        String sql = AstUtil.getStaticStringField(type, name + dialect.getSuffix() + "_SQL");
        if (sql != null) {
            return sql;
        }
        sql = AstUtil.getStaticStringField(type, name + "_SQL");
        if (sql != null) {
            return sql;
        }
        // Tiger: @Sql(value=..., dbms=...)
        final AnnotationExpr sqlAnn = AstUtil.getAnnotation(md, "Sql");
        if (sqlAnn != null) {
            final String dbms = AstUtil.getAnnotationStringValue(sqlAnn, "dbms");
            if (dbms == null || dbms.isEmpty()
                    || dbms.equalsIgnoreCase(dialectName())) {
                return AstUtil.getAnnotationStringValue(sqlAnn, "value");
            }
        }
        return null;
    }

    // ---------- helper ----------

    private String classMemberType(final AnnotationExpr ann, final String member) {
        final Expression e = AstUtil.getAnnotationMember(ann, member);
        if (e instanceof ClassExpr) {
            return ((ClassExpr) e).getType().asString();
        }
        return null;
    }

    private boolean isSimpleType(final String typeName) {
        String t = typeName;
        final int lt = t.indexOf('<');
        if (lt >= 0) {
            t = t.substring(0, lt);
        }
        return SIMPLE_TYPES.contains(t);
    }

    private String dialectName() {
        switch (dialect) {
            case ORACLE:
                return "oracle";
            case POSTGRE:
                return "postgre";
            default:
                return "";
        }
    }

    private static boolean startsWithOrderBy(final String query) {
        return query != null && query.matches("(?is)^\\s*(/\\*[^*]+\\*/)*order by.*");
    }

    private List<String> splitBy(final String s, final String delims) {
        final List<String> out = new ArrayList<String>();
        final java.util.StringTokenizer st = new java.util.StringTokenizer(s, delims);
        while (st.hasMoreTokens()) {
            out.add(st.nextToken());
        }
        return out;
    }
}
