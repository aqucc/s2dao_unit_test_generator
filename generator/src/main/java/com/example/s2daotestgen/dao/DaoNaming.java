package com.example.s2daotestgen.dao;

/**
 * S2Dao の {@code DaoNamingConventionImpl} 既定値に忠実な命名規約。
 *
 * <p>
 * メソッド名プレフィクスによる CRUD 判定・楽観ロックプロパティ名の既定値を提供する。
 * </p>
 */
public final class DaoNaming {

    public static final String[] INSERT_PREFIXES = { "insert", "create", "add" };

    public static final String[] UPDATE_PREFIXES = { "update", "modify", "store" };

    public static final String[] DELETE_PREFIXES = { "delete", "remove" };

    public static final String[] UNLESS_NULL_SUFFIXES = { "UnlessNull" };

    public static final String[] MODIFIED_ONLY_SUFFIXES = { "ModifiedOnly" };

    public static final String[] DAO_SUFFIXES = { "Dao" };

    public static final String DEFAULT_VERSION_NO_PROPERTY = "versionNo";

    public static final String DEFAULT_TIMESTAMP_PROPERTY = "timestamp";

    private DaoNaming() {
    }

    public static boolean isInsert(final String methodName) {
        return startsWithAny(methodName, INSERT_PREFIXES);
    }

    public static boolean isUpdate(final String methodName) {
        return startsWithAny(methodName, UPDATE_PREFIXES);
    }

    public static boolean isDelete(final String methodName) {
        return startsWithAny(methodName, DELETE_PREFIXES);
    }

    /** insert/update/delete のいずれでもなければ select 扱い(S2Dao と同一)。 */
    public static boolean isSelect(final String methodName) {
        return !isInsert(methodName) && !isUpdate(methodName)
                && !isDelete(methodName);
    }

    public static boolean isUnlessNull(final String methodName) {
        return endsWithAny(methodName, UNLESS_NULL_SUFFIXES);
    }

    public static boolean isModifiedOnly(final String methodName) {
        return endsWithAny(methodName, MODIFIED_ONLY_SUFFIXES);
    }

    public static String methodKind(final String methodName) {
        if (isInsert(methodName)) {
            return "INSERT";
        }
        if (isUpdate(methodName)) {
            return "UPDATE";
        }
        if (isDelete(methodName)) {
            return "DELETE";
        }
        return "SELECT";
    }

    private static boolean startsWithAny(final String name, final String[] prefixes) {
        for (int i = 0; i < prefixes.length; i++) {
            if (name.startsWith(prefixes[i])) {
                return true;
            }
        }
        return false;
    }

    private static boolean endsWithAny(final String name, final String[] suffixes) {
        for (int i = 0; i < suffixes.length; i++) {
            if (name.endsWith(suffixes[i])) {
                return true;
            }
        }
        return false;
    }
}
