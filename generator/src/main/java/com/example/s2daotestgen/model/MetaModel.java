package com.example.s2daotestgen.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * フェーズ1が出力するメタ情報のデータモデル。
 *
 * <p>
 * dao 1 つにつき 1 つの {@link DaoMeta} が {@code <DaoName>.meta.json} として出力される。
 * フェーズ2(テストコード生成)が必要とする情報(DESIGN.md §3.3 の生成フロー)を全て含む。
 * </p>
 */
public final class MetaModel {

    private MetaModel() {
    }

    /** DAO 単位のメタ情報。 */
    public static final class DaoMeta {
        /** DAO の完全修飾クラス名。 */
        public String daoClassName;
        /** DAO の単純名。 */
        public String daoSimpleName;
        /** パッケージ名。 */
        public String packageName;
        /** アノテーション方式: "CONSTANT"(定数) または "TIGER"。 */
        public String annotationStyle;
        /** DAO ソースファイルの絶対パス。 */
        public String sourceFile;
        /** BEAN が指すエンティティの完全修飾名(未指定/解決不能なら null)。 */
        public String beanClassName;
        /** 解決済みエンティティメタ(見つかった場合のみ)。 */
        public EntityMeta entity;
        /** メソッド一覧。 */
        public List<MethodMeta> methods = new ArrayList<MethodMeta>();
        /** 解析上の注意・警告メッセージ。 */
        public List<String> notes = new ArrayList<String>();
    }

    /** エンティティ(DTO)メタ情報。 */
    public static final class EntityMeta {
        public String className;
        public String simpleName;
        /** テーブル名(TABLE 定数 / @Bean(table=) / 既定=単純名)。 */
        public String tableName;
        /** テーブル名の決定根拠: "ANNOTATION" または "DEFAULT_CLASSNAME"。 */
        public String tableNameSource;
        public List<PropertyMeta> properties = new ArrayList<PropertyMeta>();
        /** リレーション(参照情報)。 */
        public List<RelationMeta> relations = new ArrayList<RelationMeta>();
        /** 主キーのカラム名(順序保持)。 */
        public List<String> primaryKeyColumns = new ArrayList<String>();
        /** バージョン番号(楽観ロック)プロパティ名。存在しなければ null。 */
        public String versionNoProperty;
        /** タイムスタンプ(楽観ロック)プロパティ名。存在しなければ null。 */
        public String timestampProperty;
        /** 主キー決定根拠: "ANNOTATION"(_ID/@Id) / "DDL" / "HEURISTIC_FIRST" / "NONE"。 */
        public String primaryKeySource;
    }

    /** プロパティ(カラム)メタ情報。 */
    public static final class PropertyMeta {
        public String propertyName;
        public String javaType;
        /** 解決済みカラム名(<prop>_COLUMN / @Column / 既定=プロパティ名)。 */
        public String columnName;
        /** カラム名の決定根拠: "ANNOTATION" または "DEFAULT_PROPERTYNAME"。 */
        public String columnNameSource;
        public boolean persistent = true;
        public boolean primaryKey = false;
        public boolean versionNo = false;
        public boolean timestamp = false;
        /** アクセス方法: "FIELD" / "GETTER"。 */
        public String access;
        /**
         * 自動採番対象か(JPA/S2JDBC 風 @GeneratedValue)。S2Dao 経路では常に false。
         * 既定値 false のときは JSON へ出力しない(既存メタ JSON との後方互換のため末尾に追加)。
         */
        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        public boolean generated = false;
    }

    /** リレーション(N:1 等)参照メタ。 */
    public static final class RelationMeta {
        public String propertyName;
        public String targetType;
        public int relationNo;
        public String relationKey;
    }

    /** DAO メソッドメタ情報。 */
    public static final class MethodMeta {
        public String name;
        public String returnType;
        public List<ParamMeta> parameters = new ArrayList<ParamMeta>();
        /** _ARGS / @Arguments で宣言された引数名(なければ空)。 */
        public List<String> argNames = new ArrayList<String>();
        /** _QUERY / @Query。なければ null。 */
        public String query;
        /** 永続化対象外プロパティ(_NO_PERSISTENT_PROPS / @NoPersistentProperty)。 */
        public List<String> noPersistentProps = new ArrayList<String>();
        /** 永続化対象プロパティ(_PERSISTENT_PROPS / @PersistentProperty)。 */
        public List<String> persistentProps = new ArrayList<String>();
        /** メソッド種別: SELECT / INSERT / UPDATE / DELETE / PROCEDURE。 */
        public String methodKind;
        /** ストアドプロシージャ名(_PROCEDURE / @Procedure)。なければ null。 */
        public String procedureName;
        /** 解決された SQL 情報。プロシージャ等で解決不能なら null。 */
        public SqlMeta sql;
    }

    /** メソッド引数メタ。 */
    public static final class ParamMeta {
        public String name;
        public String type;
    }

    /** SQL メタ情報。 */
    public static final class SqlMeta {
        /**
         * SQL 解決方法:
         * MANUAL_ANNOTATION / SQL_FILE / AUTO_INSERT / AUTO_UPDATE / AUTO_DELETE /
         * AUTO_SELECT_BY_ARGS / AUTO_SELECT_BY_DTO / AUTO_SELECT_BY_QUERY /
         * AUTO_DELETE_BY_QUERY / UNRESOLVED。
         */
        public String resolutionType;
        /** SQL ファイルから解決した場合のファイル絶対パス。 */
        public String sourceFile;
        /** 解決された(DB に渡る前の)SQL 文字列。2-way コメントを含みうる。 */
        public String rawSql;
        /** 2-way SQL コメント(IF/BEGIN/bind 等)を含むか。 */
        public boolean twoWay;
        /** バインド変数ノード一覧(出現順)。 */
        public List<BindVarMeta> bindVariables = new ArrayList<BindVarMeta>();
        /** 埋め込み変数(embedded / orderBy)一覧。 */
        public List<String> embeddedVariables = new ArrayList<String>();
        /** IF 条件式(OGNL)一覧。 */
        public List<String> ifConditions = new ArrayList<String>();
        /** BEGIN コメントを含むか。 */
        public boolean hasBegin;
        /**
         * 代表パラメータで全条件を真とした場合の展開 SQL(実際に DB に渡る形)。
         * バインドは "?" で表現される。
         */
        public String expandedSql;
        /** 展開 SQL 中の "?" に対応するバインド式(出現順)。静的 CRUD ではプロパティパス。 */
        public List<String> expandedBindOrder = new ArrayList<String>();
        /** 構造解析結果。 */
        public SqlStructure structure;
    }

    /** バインド変数ノードメタ。 */
    public static final class BindVarMeta {
        /** バインド式(例: "empno", "employee.ename", "dto.job")。 */
        public String expression;
        /** 先頭のパラメータ名(式の最初のセグメント)。 */
        public String rootParam;
        /** プロパティパス(2 セグメント目以降。無ければ空)。 */
        public String propertyPath;
        /** ノード種別: BIND / PAREN_BIND / STATIC(自動生成 CRUD の位置バインド)。 */
        public String kind;
    }

    /** SQL 構造解析結果。 */
    public static final class SqlStructure {
        /** 文種別: SELECT / INSERT / UPDATE / DELETE / OTHER。 */
        public String statementType;
        /** 参照/更新テーブル一覧。 */
        public List<String> tables = new ArrayList<String>();
        /** SELECT 句カラム一覧(* はエンティティメタで展開)。 */
        public List<String> selectColumns = new ArrayList<String>();
        /** SELECT の * をエンティティメタで展開したか。 */
        public boolean selectStarExpanded;
        /** WHERE 句カラム⇔バインド対応。 */
        public List<ColumnBinding> whereBindings = new ArrayList<ColumnBinding>();
    }

    /** WHERE 句のカラムとバインドの対応。 */
    public static final class ColumnBinding {
        public String column;
        /** 対応するバインド式(展開 SQL の ? 順から解決)。不明なら null。 */
        public String bindExpression;
        /** 比較演算子(=, <>, >, < 等)。 */
        public String operator;
    }
}
