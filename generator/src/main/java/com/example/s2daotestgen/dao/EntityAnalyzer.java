package com.example.s2daotestgen.dao;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.s2daotestgen.model.MetaModel.EntityMeta;
import com.example.s2daotestgen.model.MetaModel.PropertyMeta;
import com.example.s2daotestgen.model.MetaModel.RelationMeta;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;

/**
 * エンティティ(DTO)クラスを S2Dao 規約に従って解析する。
 *
 * <p>
 * 定数アノテーション方式(TABLE/&lt;prop&gt;_COLUMN/&lt;prop&gt;_RELNO/&lt;prop&gt;_ID/
 * VERSION_NO_PROPERTY/TIMESTAMP_PROPERTY/NO_PERSISTENT_PROPS)と
 * Tiger アノテーション方式(@Bean/@Column/@Relation/@Id)の双方に対応する。
 * </p>
 */
public final class EntityAnalyzer {

    private final SourceRepository repo;

    public EntityAnalyzer(final SourceRepository repo) {
        this.repo = repo;
    }

    public EntityMeta analyze(final SourceRepository.TypeInfo info) {
        final TypeDeclaration<?> type = info.decl;
        final EntityMeta em = new EntityMeta();
        em.className = info.fqn;
        em.simpleName = info.simpleName;

        final AnnotationExpr bean = AstUtil.getAnnotation(type, "Bean");

        // --- テーブル名 ---
        String table = AstUtil.getStaticStringField(type, "TABLE");
        if (table == null && bean != null) {
            table = AstUtil.getAnnotationStringValue(bean, "table");
            if (table != null && table.isEmpty()) {
                table = null;
            }
        }
        if (table != null) {
            em.tableName = table;
            em.tableNameSource = "ANNOTATION";
        } else {
            em.tableName = info.simpleName;
            em.tableNameSource = "DEFAULT_CLASSNAME";
        }

        // --- 楽観ロックプロパティ名の解決 ---
        String versionName = AstUtil.getStaticStringField(type, "VERSION_NO_PROPERTY");
        String timestampName = AstUtil.getStaticStringField(type, "TIMESTAMP_PROPERTY");
        if (bean != null) {
            if (versionName == null) {
                versionName = AstUtil.getAnnotationStringValue(bean, "versionNoProperty");
            }
            if (timestampName == null) {
                timestampName = AstUtil.getAnnotationStringValue(bean, "timeStampProperty");
            }
        }
        if (versionName == null) {
            versionName = DaoNaming.DEFAULT_VERSION_NO_PROPERTY;
        }
        if (timestampName == null) {
            timestampName = DaoNaming.DEFAULT_TIMESTAMP_PROPERTY;
        }

        // --- NO_PERSISTENT_PROPS ---
        final List<String> noPersistent = new ArrayList<String>();
        final String npp = AstUtil.getStaticStringField(type, "NO_PERSISTENT_PROPS");
        if (npp != null) {
            for (final String s : npp.split("[,\\s]+")) {
                if (!s.isEmpty()) {
                    noPersistent.add(s);
                }
            }
        }
        if (bean != null) {
            final List<String> arr = AstUtil.getAnnotationStringArray(bean, "noPersistentProperty");
            if (arr != null) {
                noPersistent.addAll(arr);
            }
        }

        // --- プロパティ収集(フィールド宣言順) ---
        final Map<String, MethodDeclaration> getters = collectGetters(type);
        final List<FieldEntry> fields = collectFields(type);

        for (final FieldEntry fe : fields) {
            final MethodDeclaration getter = getters.get(fe.name);
            final boolean isProperty = getter != null || fe.isPublic;
            if (!isProperty) {
                continue;
            }
            final String javaType = getter != null
                    ? getter.getType().asString() : fe.type;
            processProperty(em, type, fe.name, javaType,
                    getter != null ? "GETTER" : "FIELD", fe.field, getter,
                    noPersistent, versionName, timestampName);
        }
        // フィールドに現れない getter 由来プロパティ
        for (final Map.Entry<String, MethodDeclaration> e : getters.entrySet()) {
            if (containsProperty(em, e.getKey()) || isRelationName(em, e.getKey())) {
                continue;
            }
            processProperty(em, type, e.getKey(), e.getValue().getType().asString(),
                    "GETTER", null, e.getValue(), noPersistent, versionName,
                    timestampName);
        }

        em.versionNoProperty = containsProperty(em, versionName) ? versionName : null;
        em.timestampProperty = containsProperty(em, timestampName) ? timestampName : null;
        for (final PropertyMeta pm : em.properties) {
            pm.versionNo = pm.propertyName.equals(em.versionNoProperty);
            pm.timestamp = pm.propertyName.equals(em.timestampProperty);
        }

        setupPrimaryKey(em);
        return em;
    }

    private void processProperty(final EntityMeta em, final TypeDeclaration<?> type,
            final String name, final String javaType, final String access,
            final FieldDeclaration field, final MethodDeclaration getter,
            final List<String> noPersistent, final String versionName,
            final String timestampName) {

        // リレーション判定(<prop>_RELNO 定数 / @Relation)
        final boolean relnoConst = AstUtil.hasStaticField(type, name + "_RELNO");
        final AnnotationExpr relAnn = memberAnnotation(field, getter, "Relation");
        if (relnoConst || relAnn != null) {
            final RelationMeta rm = new RelationMeta();
            rm.propertyName = name;
            rm.targetType = javaType;
            if (relnoConst) {
                final String v = AstUtil.getStaticStringField(type, name + "_RELNO");
                rm.relationNo = parseIntFromField(type, name + "_RELNO");
                if (v != null) {
                    // 数値定数以外は 0 のまま
                }
                final String relkeys = AstUtil.getStaticStringField(type, name + "_RELKEYS");
                rm.relationKey = relkeys;
            } else {
                rm.relationNo = AstUtil.getAnnotationIntValue(relAnn, "relationNo", 0);
                rm.relationKey = AstUtil.getAnnotationStringValue(relAnn, "relationKey");
            }
            em.relations.add(rm);
            return;
        }

        final PropertyMeta pm = new PropertyMeta();
        pm.propertyName = name;
        pm.javaType = javaType;
        pm.access = access;

        // カラム名: <prop>_COLUMN 定数 / @Column / 既定=プロパティ名
        String column = AstUtil.getStaticStringField(type, name + "_COLUMN");
        if (column == null) {
            final AnnotationExpr colAnn = memberAnnotation(field, getter, "Column");
            if (colAnn != null) {
                column = AstUtil.getAnnotationStringValue(colAnn, "value");
            }
        }
        if (column != null) {
            pm.columnName = column;
            pm.columnNameSource = "ANNOTATION";
        } else {
            pm.columnName = name;
            pm.columnNameSource = "DEFAULT_PROPERTYNAME";
        }

        // 永続化判定
        boolean persistent = true;
        for (final String n : noPersistent) {
            if (n.equals(name)) {
                persistent = false;
                break;
            }
        }
        pm.persistent = persistent;

        // 主キー判定(<prop>_ID 定数 / @Id)
        final boolean idConst = AstUtil.hasStaticField(type, name + "_ID");
        final AnnotationExpr idAnn = memberAnnotation(field, getter, "Id");
        pm.primaryKey = idConst || idAnn != null;

        em.properties.add(pm);
    }

    private void setupPrimaryKey(final EntityMeta em) {
        final List<PropertyMeta> explicit = new ArrayList<PropertyMeta>();
        for (final PropertyMeta pm : em.properties) {
            if (pm.primaryKey) {
                explicit.add(pm);
            }
        }
        if (!explicit.isEmpty()) {
            em.primaryKeySource = "ANNOTATION";
            for (final PropertyMeta pm : explicit) {
                em.primaryKeyColumns.add(pm.columnName);
            }
            return;
        }
        // ヒューリスティック: DB メタデータが無いため先頭の永続プロパティを主キーとみなす。
        for (final PropertyMeta pm : em.properties) {
            if (pm.persistent) {
                pm.primaryKey = true;
                em.primaryKeyColumns.add(pm.columnName);
                em.primaryKeySource = "HEURISTIC_FIRST";
                return;
            }
        }
        em.primaryKeySource = "NONE";
    }

    // ---- AST helper ----

    private static final class FieldEntry {
        String name;
        String type;
        boolean isPublic;
        FieldDeclaration field;
    }

    private List<FieldEntry> collectFields(final TypeDeclaration<?> type) {
        final List<FieldEntry> out = new ArrayList<FieldEntry>();
        for (final BodyDeclaration<?> member : type.getMembers()) {
            if (!(member instanceof FieldDeclaration)) {
                continue;
            }
            final FieldDeclaration fd = (FieldDeclaration) member;
            if (fd.isStatic()) {
                continue;
            }
            for (final VariableDeclarator v : fd.getVariables()) {
                final FieldEntry fe = new FieldEntry();
                fe.name = v.getNameAsString();
                fe.type = v.getType().asString();
                fe.isPublic = fd.isPublic();
                fe.field = fd;
                out.add(fe);
            }
        }
        return out;
    }

    private Map<String, MethodDeclaration> collectGetters(final TypeDeclaration<?> type) {
        final Map<String, MethodDeclaration> out = new LinkedHashMap<String, MethodDeclaration>();
        for (final BodyDeclaration<?> member : type.getMembers()) {
            if (!(member instanceof MethodDeclaration)) {
                continue;
            }
            final MethodDeclaration md = (MethodDeclaration) member;
            if (md.isStatic() || md.getParameters().size() != 0) {
                continue;
            }
            final String mn = md.getNameAsString();
            String prop = null;
            if (mn.startsWith("get") && mn.length() > 3
                    && !md.getType().isVoidType()) {
                prop = decapitalize(mn.substring(3));
            } else if (mn.startsWith("is") && mn.length() > 2) {
                prop = decapitalize(mn.substring(2));
            }
            if (prop == null || prop.equals("class")) {
                continue;
            }
            out.put(prop, md);
        }
        return out;
    }

    private AnnotationExpr memberAnnotation(final FieldDeclaration field,
            final MethodDeclaration getter, final String annName) {
        if (getter != null) {
            final AnnotationExpr a = AstUtil.getAnnotation(getter, annName);
            if (a != null) {
                return a;
            }
        }
        if (field != null) {
            return AstUtil.getAnnotation(field, annName);
        }
        return null;
    }

    private int parseIntFromField(final TypeDeclaration<?> type, final String fieldName) {
        for (final BodyDeclaration<?> member : type.getMembers()) {
            if (!(member instanceof FieldDeclaration)) {
                continue;
            }
            final FieldDeclaration fd = (FieldDeclaration) member;
            for (final VariableDeclarator v : fd.getVariables()) {
                if (v.getNameAsString().equals(fieldName)
                        && v.getInitializer().isPresent()
                        && v.getInitializer().get().isIntegerLiteralExpr()) {
                    return v.getInitializer().get().asIntegerLiteralExpr()
                            .asNumber().intValue();
                }
            }
        }
        return 0;
    }

    private boolean containsProperty(final EntityMeta em, final String name) {
        for (final PropertyMeta pm : em.properties) {
            if (pm.propertyName.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private boolean isRelationName(final EntityMeta em, final String name) {
        for (final RelationMeta rm : em.relations) {
            if (rm.propertyName.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static String decapitalize(final String name) {
        if (name.length() >= 2 && Character.isUpperCase(name.charAt(0))
                && Character.isUpperCase(name.charAt(1))) {
            return name;
        }
        final char[] chars = name.toCharArray();
        chars[0] = Character.toLowerCase(chars[0]);
        return new String(chars);
    }
}
