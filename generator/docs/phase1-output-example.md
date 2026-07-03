# フェーズ1 出力例(メタ情報 JSON)

本書は `s2dao-testgen`(フェーズ1: DAO Java 解析 + 2-way SQL 解析エンジン)の
実出力例をまとめたものである。フェーズ2(テストコード生成)の入力仕様を兼ねる。

## 実行方法

```
# パッケージ(実行可能 fat-jar 生成)
cd generator && mvn -q package

# 解析(定数アノテーション方式のサンプル)
java -jar target/s2dao-testgen.jar analyze \
  --src ../samples/s2dao/s2-dao-examples/src/main/java \
  --sql ../samples/s2dao/s2-dao-examples/src/main/resources \
  --out /tmp/out-s2dao --dbms oracle

# 解析(Tiger アノテーション方式のサンプル)
java -jar target/s2dao-testgen.jar analyze \
  --src ../samples/s2dao-tiger/s2-dao-tiger-examples/src/main/java \
  --sql ../samples/s2dao-tiger/s2-dao-tiger-examples/src/main/resources \
  --out /tmp/out-tiger --dbms oracle
```

`--src`/`--sql` は同一ディレクトリでも可。両方とも再帰走査し、複数 DAO を一括処理する。
DAO 1 つにつき `<DaoName>.meta.json` を 1 つ出力する。

実行結果(定数アノテーション方式、8 DAO):

```
  examples.dao.Employee2Dao         → Employee2Dao.meta.json (methods=2)
  examples.dao.EmployeeAutoDao      → EmployeeAutoDao.meta.json (methods=7)
  examples.dao.TruncateTableDao     → TruncateTableDao.meta.json (methods=3)
  examples.dao.DepartmentManager    → DepartmentManager.meta.json (methods=3)
  examples.dao.EmployeeDao          → EmployeeDao.meta.json (methods=8)
  examples.dao.StoredProcedureTestDao → StoredProcedureTestDao.meta.json (methods=4)
  examples.dao.NoPkTableDao         → NoPkTableDao.meta.json (methods=1)
  examples.dao.DepartmentDao        → DepartmentDao.meta.json (methods=3)
完了: 8 件の DAO メタ JSON を出力しました
```

## 例1: 2-way SQL(`/*BEGIN*/ /*IF*/`)の解析 — `EmployeeDao.getEmployeeByJobDeptno`

入力 `EmployeeDao_getEmployeeByJobDeptno.sql`:

```sql
SELECT * FROM emp
/*BEGIN*/WHERE
  /*IF job != null*/job = /*job*/'CLERK'/*END*/
  /*IF deptno != null*/AND deptno = /*deptno*/20/*END*/
/*END*/
```

出力(メソッド抜粋):

```json
{
  "name": "getEmployeeByJobDeptno",
  "returnType": "List",
  "parameters": [
    { "name": "job", "type": "String" },
    { "name": "deptno", "type": "Integer" }
  ],
  "argNames": [ "job", "deptno" ],
  "methodKind": "SELECT",
  "sql": {
    "resolutionType": "SQL_FILE",
    "sourceFile": ".../EmployeeDao_getEmployeeByJobDeptno.sql",
    "rawSql": "SELECT * FROM emp\r\n/*BEGIN*/WHERE\r\n  /*IF job != null*/job = /*job*/'CLERK'/*END*/\r\n  /*IF deptno != null*/AND deptno = /*deptno*/20/*END*/\r\n/*END*/",
    "twoWay": true,
    "bindVariables": [
      { "expression": "job",    "rootParam": "job",    "propertyPath": "", "kind": "BIND" },
      { "expression": "deptno", "rootParam": "deptno", "propertyPath": "", "kind": "BIND" }
    ],
    "embeddedVariables": [],
    "ifConditions": [ "job != null", "deptno != null" ],
    "hasBegin": true,
    "expandedSql": "SELECT * FROM emp\r\nWHERE\r\n  job = ?\r\n  AND deptno = ?\r\n",
    "expandedBindOrder": [ "job", "deptno" ],
    "structure": {
      "statementType": "SELECT",
      "tables": [ "emp" ],
      "selectColumns": [ "empno","ename","job","mgr","hiredate","sal","comm","deptno","tstamp" ],
      "selectStarExpanded": true,
      "whereBindings": [
        { "column": "job",    "bindExpression": "job",    "operator": "=" },
        { "column": "deptno", "bindExpression": "deptno", "operator": "=" }
      ]
    }
  }
}
```

- 2-way SQL は **ベンダリングした S2Container `SqlParserImpl`** で解析している
  (`generator/src/main/java/org/seasar/extension/sql/`)。IF/BEGIN/バインド変数の
  解釈は S2Dao 実物と一致する。
- `expandedSql` は「代表パラメータで全条件を真」とした場合に実際に DB へ渡る形。
- `structure.selectColumns` は `SELECT *` をエンティティメタ(EMP の永続カラム)で展開した結果。

## 例2: 自動生成 CRUD SQL — `DepartmentDao`

`DepartmentDao` は `insert`/`update`/`delete`(いずれも `Department` を引数に取る)を
定義するのみ。S2Dao 本体 `AbstractAutoStaticCommand` と同一ロジックで SQL を組み立てる。
`Department` は主キー `deptno`、`versionNo`(楽観ロック)を持つ。

| メソッド | resolutionType | rawSql |
|---|---|---|
| `insert` | `AUTO_INSERT` | `INSERT INTO DEPT (deptno, dname, loc, versionNo) VALUES (?, ?, ?, ?)` |
| `update` | `AUTO_UPDATE` | `UPDATE DEPT SET dname = ?, loc = ?, versionNo = ? WHERE deptno = ? AND versionNo = ?` |
| `delete` | `AUTO_DELETE` | `DELETE FROM DEPT WHERE deptno = ? AND versionNo = ?` |

`update` の `expandedBindOrder` は `[dname, loc, versionNo, deptno, versionNo]`
(SET 句のプロパティ → WHERE 句の主キー → 楽観ロック versionNo の順)であり、
静的 CRUD の各 `?` に束縛すべき Bean プロパティを保持する。

## メタ JSON スキーマ(概要)

- `daoClassName` / `daoSimpleName` / `packageName` / `annotationStyle`(`CONSTANT`|`TIGER`)
- `beanClassName` / `entity`(テーブル名・プロパティ・カラム・主キー・楽観ロック・リレーション)
- `methods[]`
  - `name` / `returnType` / `parameters[]`
  - `argNames` / `query` / `noPersistentProps` / `persistentProps`
  - `methodKind`(`SELECT`|`INSERT`|`UPDATE`|`DELETE`|`PROCEDURE`)/ `procedureName`
  - `sql`
    - `resolutionType`(`MANUAL_ANNOTATION`|`SQL_FILE`|`AUTO_INSERT`|`AUTO_UPDATE`|
      `AUTO_DELETE`|`AUTO_SELECT_BY_ARGS`|`AUTO_SELECT_BY_DTO`|`AUTO_SELECT_BY_QUERY`|
      `AUTO_DELETE_BY_QUERY`|`UNRESOLVED`)
    - `rawSql` / `twoWay` / `bindVariables[]` / `embeddedVariables[]` / `ifConditions[]` /
      `hasBegin` / `expandedSql` / `expandedBindOrder[]`
    - `structure`(`statementType` / `tables[]` / `selectColumns[]` / `selectStarExpanded` /
      `whereBindings[]`)

## S2Dao 本体との仕様差異・簡略化(重要)

本ジェネレーターは **DB へ接続しない静的解析**である。S2Dao 実物が実行時に
`DatabaseMetaData` から取得する情報は、以下の方針で代替している。

1. **カラム名**: S2Dao 既定の `DefaultColumnNaming`(プロパティ名をそのまま使用)に
   忠実に従う。実物は DB メタデータの実カラム名(Oracle では通常大文字)に置換するため、
   本ツール出力の `versionNo` は実 DB では `VERSIONNO` 等になりうる
   (`<prop>_COLUMN` / `@Column` 指定時はその値を使用)。
2. **主キー**: `<prop>_ID` / `@Id` があればそれを使用(`primaryKeySource=ANNOTATION`)。
   無い場合は DB メタデータが得られないため、**先頭の永続プロパティを主キーとみなす**
   ヒューリスティック(`primaryKeySource=HEURISTIC_FIRST`)を用いる。emp/dept サンプルでは
   `empno`/`deptno` が正しく主キーになる。真に主キーの無いテーブル(`NoPkTable`)でも
   先頭プロパティが主キー扱いになるが、当該 DAO は SELECT のみのため SQL 生成に影響しない。
3. **永続化フラグ**: DB カラムの実在チェックができないため、リレーション以外の全プロパティを
   永続カラムとみなす。実物では DB に存在しないプロパティ(例: `Employee.timestamp`→`tstamp`
   カラムがサンプル DDL に無い)は非永続になるが、本ツールでは永続として扱う。
4. **自動 SELECT のリレーション結合**: リレーション(`_RELNO`/`@Relation`)は参照メタ情報
   として保持するのみで、自動 SELECT の `LEFT OUTER JOIN`(Oracle は `(+)`)は生成しない。
   FROM 句は対象テーブル単体とする。実 DAO の `.sql` に JOIN が書かれている場合は
   そのまま解析・展開する(サンプルの `getAllEmployees` 等)。
5. **埋め込み変数の展開**: `/*$var*/` や `orderBy` は実行時の値に依存するため、
   `expandedSql` では空文字として扱い、`embeddedVariables` に記録する。
6. **IN バインド(`ParenBindVariableNode`)**: `expandedSql` では代表として単一要素
   `(?)` を仮定する。
7. **手書き `@Sql` の DBMS 選択**: `@Sql(dbms=...)` は指定 DBMS が現方言と一致する場合
   のみ採用する(複数 `@Sqls` の網羅選択は未対応。サンプルには複数指定は無い)。
8. **`org.seasar.framework.*` の互換シム**: ベンダリングした 2-way SQL パーサが依存する
   `StringUtil`/`BeanDesc`/`OgnlUtil`/`CaseInsensitiveMap`/`SRuntimeException`/`Logger`
   は、Maven Central 未配布のため自前の互換シムに置き換えている。挙動同一性は
   `ShimConformanceTest`(実パーサ + `SqlContextImpl.accept()` の評価結果と、本ツールの
   全条件真展開の一致)で担保している。
