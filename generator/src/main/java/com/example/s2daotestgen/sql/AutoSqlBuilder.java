package com.example.s2daotestgen.sql;

import java.util.ArrayList;
import java.util.List;

import com.example.s2daotestgen.dao.Dialect;
import com.example.s2daotestgen.model.MetaModel.EntityMeta;
import com.example.s2daotestgen.model.MetaModel.PropertyMeta;

/**
 * S2Dao の自動生成 SQL(INSERT/UPDATE/DELETE/SELECT)を、本体の
 * {@code AbstractAutoStaticCommand} / {@code DaoMetaDataImpl} と同一ロジックで組み立てる。
 *
 * <p>
 * カラム名の大文字化は本来 DB メタデータに由来するため、本ジェネレーターでは
 * S2Dao の既定 {@code DefaultColumnNaming}(プロパティ名をそのまま使用)に忠実に従う。
 * </p>
 */
public final class AutoSqlBuilder {

    /** 自動生成結果。sql と、"?" に対応するバインド用プロパティ名(順序)を保持する。 */
    public static final class Result {
        public final String sql;
        public final List<String> bindProps;
        public final String resolutionType;

        Result(final String sql, final List<String> bindProps,
                final String resolutionType) {
            this.sql = sql;
            this.bindProps = bindProps;
            this.resolutionType = resolutionType;
        }
    }

    private final Dialect dialect;

    public AutoSqlBuilder(final Dialect dialect) {
        this.dialect = dialect;
    }

    // ---------- 永続化プロパティ名の決定(getPersistentPropertyNames 相当) ----------

    public List<String> persistentPropertyNames(final EntityMeta em,
            final List<String> noPersistentProps, final List<String> persistentProps) {
        final List<String> names = new ArrayList<String>();
        if (noPersistentProps != null && !noPersistentProps.isEmpty()) {
            for (final PropertyMeta pt : em.properties) {
                if (pt.persistent && !containsIgnoreCase(noPersistentProps, pt.propertyName)) {
                    names.add(pt.propertyName);
                }
            }
        } else if (persistentProps != null && !persistentProps.isEmpty()) {
            names.addAll(persistentProps);
            for (final PropertyMeta pt : em.properties) {
                if (pt.primaryKey) {
                    names.add(pt.propertyName);
                }
            }
            if (em.versionNoProperty != null) {
                names.add(em.versionNoProperty);
            }
            if (em.timestampProperty != null) {
                names.add(em.timestampProperty);
            }
        }
        if (names.isEmpty()) {
            for (final PropertyMeta pt : em.properties) {
                if (pt.persistent) {
                    names.add(pt.propertyName);
                }
            }
        }
        return names;
    }

    // ---------- INSERT ----------

    public Result buildInsert(final EntityMeta em, final List<String> propertyNames) {
        final List<PropertyMeta> targets = new ArrayList<PropertyMeta>();
        for (final String name : propertyNames) {
            final PropertyMeta pt = findProperty(em, name);
            if (pt == null) {
                continue;
            }
            // isInsertTarget: 主キーは自己生成(assigned=既定)のとき対象。既定は対象。
            targets.add(pt);
        }
        final StringBuilder buf = new StringBuilder();
        buf.append("INSERT INTO ").append(em.tableName).append(" (");
        for (int i = 0; i < targets.size(); i++) {
            if (i > 0) {
                buf.append(", ");
            }
            buf.append(targets.get(i).columnName);
        }
        buf.append(") VALUES (");
        final List<String> binds = new ArrayList<String>();
        for (int i = 0; i < targets.size(); i++) {
            if (i > 0) {
                buf.append(", ");
            }
            buf.append("?");
            binds.add(targets.get(i).propertyName);
        }
        buf.append(")");
        return new Result(buf.toString(), binds, "AUTO_INSERT");
    }

    // ---------- UPDATE ----------

    public Result buildUpdate(final EntityMeta em, final List<String> propertyNames) {
        final List<PropertyMeta> setTargets = new ArrayList<PropertyMeta>();
        for (final String name : propertyNames) {
            final PropertyMeta pt = findProperty(em, name);
            if (pt == null || pt.primaryKey) {
                continue;
            }
            setTargets.add(pt);
        }
        final StringBuilder buf = new StringBuilder();
        final List<String> binds = new ArrayList<String>();
        buf.append("UPDATE ").append(em.tableName).append(" SET ");
        for (int i = 0; i < setTargets.size(); i++) {
            if (i > 0) {
                buf.append(", ");
            }
            buf.append(setTargets.get(i).columnName).append(" = ?");
            binds.add(setTargets.get(i).propertyName);
        }
        appendUpdateWhere(em, buf, binds);
        return new Result(buf.toString(), binds, "AUTO_UPDATE");
    }

    // ---------- DELETE ----------

    public Result buildDelete(final EntityMeta em) {
        final StringBuilder buf = new StringBuilder();
        final List<String> binds = new ArrayList<String>();
        buf.append("DELETE FROM ").append(em.tableName);
        appendUpdateWhere(em, buf, binds);
        return new Result(buf.toString(), binds, "AUTO_DELETE");
    }

    private void appendUpdateWhere(final EntityMeta em, final StringBuilder buf,
            final List<String> binds) {
        buf.append(" WHERE ");
        final List<PropertyMeta> pks = primaryKeyProperties(em);
        for (int i = 0; i < pks.size(); i++) {
            if (i > 0) {
                buf.append(" AND ");
            }
            buf.append(pks.get(i).columnName).append(" = ?");
            binds.add(pks.get(i).propertyName);
        }
        if (em.versionNoProperty != null) {
            final PropertyMeta pt = findProperty(em, em.versionNoProperty);
            if (pt != null) {
                buf.append(" AND ").append(pt.columnName).append(" = ?");
                binds.add(pt.propertyName);
            }
        }
        if (em.timestampProperty != null) {
            final PropertyMeta pt = findProperty(em, em.timestampProperty);
            if (pt != null) {
                buf.append(" AND ").append(pt.columnName).append(" = ?");
                binds.add(pt.propertyName);
            }
        }
    }

    // ---------- SELECT ----------

    /** getAutoSelectList + FROM 句(リレーションは簡略化のため対象外)。 */
    public String autoSelectSql(final EntityMeta em) {
        final StringBuilder buf = new StringBuilder();
        buf.append("SELECT ");
        boolean first = true;
        for (final PropertyMeta pt : em.properties) {
            if (!pt.persistent) {
                continue;
            }
            if (first) {
                first = false;
            } else {
                buf.append(", ");
            }
            buf.append(em.tableName).append(".").append(pt.columnName);
        }
        buf.append(" FROM ").append(em.tableName);
        return buf.toString();
    }

    /** createAutoSelectSql(argNames) 相当。 */
    public Result buildAutoSelectByArgs(final EntityMeta em, final List<String> argNames) {
        final String sql = autoSelectSql(em);
        final StringBuilder buf = new StringBuilder(sql);
        if (!argNames.isEmpty()) {
            boolean began = false;
            if (!(sql.lastIndexOf("WHERE") > 0)) {
                buf.append("/*BEGIN*/ WHERE ");
                began = true;
            }
            for (int i = 0; i < argNames.size(); i++) {
                final String col = convertFullColumnName(em, argNames.get(i));
                buf.append("/*IF ").append(argNames.get(i)).append(" != null*/");
                buf.append(" ");
                if (!began || i != 0) {
                    buf.append("AND ");
                }
                buf.append(col).append(" = /*").append(argNames.get(i)).append("*/null");
                buf.append("/*END*/");
            }
            if (began) {
                buf.append("/*END*/");
            }
        }
        return new Result(buf.toString(), null, "AUTO_SELECT_BY_ARGS");
    }

    /** createSelectDynamicCommand(handler, query) の query 付き自動 SELECT 相当。 */
    public Result buildAutoSelectByQuery(final EntityMeta em, final String query) {
        final String sql = autoSelectSql(em);
        final StringBuilder buf = new StringBuilder();
        buf.append(sql);
        String adjustedQuery = query;
        boolean began = false;
        final boolean whereContained = sql.lastIndexOf("WHERE") > 0;
        if (startsWithOrderBy(query)) {
            buf.append(" ");
        } else if (startsWithBeginComment(query)) {
            buf.append(" ");
            if (whereContained) {
                adjustedQuery = query.replaceFirst("(?i)/\\*BEGIN\\*/\\s*WHERE", "/*BEGIN*/AND");
            }
        } else if (!whereContained) {
            if (startsWithIfComment(query)) {
                buf.append("/*BEGIN*/");
                began = true;
            }
            buf.append(" WHERE ");
        } else {
            if (startsWithIfComment(query)) {
                buf.append("/*BEGIN*/");
                began = true;
            }
            buf.append(" AND ");
        }
        buf.append(adjustedQuery);
        if (began) {
            buf.append("/*END*/");
        }
        return new Result(buf.toString(), null, "AUTO_SELECT_BY_QUERY");
    }

    /** createAutoSelectSqlByDto(dtoClass) の近似実装。 */
    public Result buildAutoSelectByDto(final EntityMeta bean, final EntityMeta dto,
            final String query) {
        final String sql = autoSelectSql(bean);
        final StringBuilder buf = new StringBuilder(sql);
        boolean began = false;
        if (!(sql.lastIndexOf("WHERE") > 0)) {
            buf.append("/*BEGIN*/ WHERE ");
            began = true;
        }
        int i = 0;
        if (dto != null) {
            for (final PropertyMeta dpt : dto.properties) {
                final String aliasName = dpt.columnName;
                final PropertyMeta beanPt = findByColumnIgnoreCase(bean, aliasName);
                if (beanPt == null || !beanPt.persistent) {
                    i++;
                    continue;
                }
                final String columnName = bean.tableName + "." + beanPt.columnName;
                final String propertyName = "dto." + dpt.propertyName;
                buf.append("/*IF ").append(propertyName).append(" != null*/");
                buf.append(" ");
                if (!began || i != 0) {
                    buf.append("AND ");
                }
                buf.append(columnName).append(" = /*").append(propertyName).append("*/null");
                buf.append("/*END*/");
                i++;
            }
        }
        if (began) {
            buf.append("/*END*/");
        }
        String out = buf.toString();
        if (query != null) {
            out = out + " " + query;
        }
        return new Result(out, null, "AUTO_SELECT_BY_DTO");
    }

    /** DELETE 系で .sql が無く query がある場合(setupMethodBySqlFile の delete 分岐)。 */
    public Result buildDeleteByQuery(final EntityMeta em, final String query) {
        final String trimmed = query.trim();
        final String sql;
        if (trimmed.toUpperCase().startsWith("WHERE")) {
            sql = "DELETE FROM " + em.tableName + " " + query;
        } else {
            sql = "DELETE FROM " + em.tableName + " WHERE " + query;
        }
        return new Result(sql, null, "AUTO_DELETE_BY_QUERY");
    }

    // ---------- helper ----------

    public String convertFullColumnName(final EntityMeta em, final String name) {
        final PropertyMeta pt = findProperty(em, name);
        if (pt != null) {
            return em.tableName + "." + pt.columnName;
        }
        return name;
    }

    private PropertyMeta findProperty(final EntityMeta em, final String name) {
        for (final PropertyMeta pt : em.properties) {
            if (pt.propertyName.equalsIgnoreCase(name)) {
                return pt;
            }
        }
        return null;
    }

    private PropertyMeta findByColumnIgnoreCase(final EntityMeta em, final String column) {
        for (final PropertyMeta pt : em.properties) {
            if (pt.columnName.equalsIgnoreCase(column)) {
                return pt;
            }
        }
        return null;
    }

    private List<PropertyMeta> primaryKeyProperties(final EntityMeta em) {
        final List<PropertyMeta> out = new ArrayList<PropertyMeta>();
        for (final PropertyMeta pt : em.properties) {
            if (pt.primaryKey) {
                out.add(pt);
            }
        }
        return out;
    }

    private static boolean containsIgnoreCase(final List<String> list, final String s) {
        for (final String e : list) {
            if (e.equalsIgnoreCase(s)) {
                return true;
            }
        }
        return false;
    }

    private static boolean startsWithOrderBy(final String query) {
        return query != null && query.matches("(?is)^\\s*(/\\*[^*]+\\*/)*order by.*");
    }

    private static boolean startsWithBeginComment(final String query) {
        return query != null && query.matches("(?is)^/\\*BEGIN\\*/\\s*WHERE .+");
    }

    private static boolean startsWithIfComment(final String query) {
        return query != null && query.matches("(?is)^/\\*IF .+");
    }
}
