# 検証結果レポート(新旧環境での生成テスト実行・一致検証)

作成日: 2026-07-03
対象: `s2dao ユニットテストコードジェネレーター`(generator / testsupport)が実サンプルから
生成した JUnit3 テストの、**旧環境相当**と**新環境**での実行結果と一致検証。

- 検証対象サンプル
  - 定数アノテーション方式: `verification/samples/s2dao/s2-dao-examples`(パッケージ `examples.dao`)
  - Tiger アノテーション方式: `verification/samples/s2dao-tiger/s2-dao-tiger-examples`(パッケージ `examples.dao.tiger`)
- 生成テスト: `verification/generated/<sample>/`(`gen-all` で生成)
- 再現スクリプト: `verification/run/run-old-env.sh` / `verification/run/run-new-env.sh` / `verification/run/compare/compare.sh`
- 実行ログ: `verification/results/{old-env,new-env}/log/`、エビデンス CSV: `verification/results/{old-env,new-env}/evidence/`

---

## 1. 実行環境の構成

制約の詳細は `docs/CONSTRAINTS.md` を参照。実 Oracle11g / 実 Java5 は本環境で入手不可のため、
等価な代替で「旧環境相当」を構成している。

| 項目 | 旧環境相当(OLD) | 新環境(NEW) |
|---|---|---|
| DB | H2 Database 1.4.199 `MODE=Oracle`(インメモリ) | PostgreSQL 16.13(apt 版) |
| JDBC ドライバ | `org.h2.Driver` | `org.postgresql.Driver` 42.2.27 |
| 接続 URL | `jdbc:h2:mem:s2daogen;MODE=Oracle;DB_CLOSE_DELAY=-1` | `jdbc:postgresql://127.0.0.1:5432/s2daogen` |
| JVM | OpenJDK 8(生成コードは `-source/-target 1.6` でコンパイル) | OpenJDK 8 |
| JUnit | 3.8.2(`junit.framework.TestCase`) | 3.8.2 |
| S2 ランタイム | s2-framework/s2-extension **2.3.23** + s2-dao/s2-dao-tiger **1.0.52**(両環境共通) | 同左 |
| dicon | `verification/run/dicon/s2dao-h2.dicon`, `s2dao-tiger-h2.dicon` | `…-pg.dicon`(差分は DataSource の driver/URL/user/password の 4 行のみ) |
| DDL | `verification/run/ddl/schema.sql`(EMP/DEPT/NOPKTABLE、両 DB 共通) | 同左 |

- **DDL は 1 本を両 DB でそのまま適用**(`NUMERIC` / `VARCHAR` / `DATE` / `TIMESTAMP` / `DROP TABLE IF EXISTS`)。
  H2 Oracle 互換モード・PostgreSQL 双方が同一 DDL を解釈できることを確認。
  - `EMP.TSTAMP`(`Employee.timestamp_COLUMN="tstamp"` = S2Dao 自動タイムスタンプ列)を追加。
  - `DEPT.VERSIONNO`(`Department.versionNo` = S2Dao 楽観ロック列)を追加。
  - `NOPKTABLE`(`NoPkTable` エンティティ、主キー無し)は samples の DDL に無いため dao/entity から起こした。
- `.sql`(明示 SQL / 2-way SQL)は DAO と同一パッケージパスでクラスパスに配置(無いと自動生成 SQL に
  フォールバックするため、`RUNTIME_BUILD.md` の指示どおり配置)。
- dicon には対象 DAO をすべて登録し `S2DaoInterceptor` を織り込む(`EmployeeDao` / `DepartmentDao` /
  `EmployeeAutoDao` / `NoPkTableDao` / `DepartmentManager`、Tiger 側は `EmployeeDao` / `DepartmentDao` /
  `EmployeeAutoDao` / `Employee2Dao`)。命名規約は既定(`DaoNamingConventionImpl`)を使用。

---

## 2. テスト実行結果(両環境)

各サンプルとも旧環境・新環境で完全に同一の結果。JUnit3(`junit.textui.TestRunner`)で実行。

| サンプル | 環境 | 実行テスト数 | 成功 | 失敗 | エラー |
|---|---|---:|---:|---:|---:|
| 定数(s2dao) | OLD(H2-Oracle) | 21 | 21 | 0 | 0 |
| 定数(s2dao) | NEW(PostgreSQL16) | 21 | 21 | 0 | 0 |
| Tiger(s2dao-tiger) | OLD(H2-Oracle) | 20 | 20 | 0 | 0 |
| Tiger(s2dao-tiger) | NEW(PostgreSQL16) | 20 | 20 | 0 | 0 |

**両環境・両サンプルで全テスト成功。**

### 2.1 生成時にスキップされたメソッド(テスト未生成 — ジェネレーターの対象外)

これらは `gen-all` の生成フェーズで `// TODO`(スキップ)扱いとなり、テストが生成されない。
プレースホルダのみのクラス(実テスト 0 件)は実行対象から除外している(下表「除外」)。

| サンプル | DAO | メソッド | スキップ理由 |
|---|---|---|---|
| 定数 | Employee2Dao | getEmployees / getEmployee | SQL 未解決(BEAN 定数・SQL ファイルとも無し)|
| 定数 | StoredProcedureTestDao | getSalesTax〜4 | ストアドプロシージャは未対応 |
| 定数 | TruncateTableDao | updateDrop / updateCreate / updateTrancate | DDL 文で対象テーブルを組み立て不能 |
| Tiger | EmployeeDao | fetchAllEmployee | 未対応引数型 `FetchHandler<Employee>` |
| Tiger | EmployeeAutoDao | fetchEmployeesBySearchCondition | 未対応引数型 `FetchHandler<Employee>` |
| Tiger | StoredProcedureTestDao | getSalesTax〜4 | ストアドプロシージャは未対応 |

上記 DAO のうち **プレースホルダのみのクラス**(定数 `Employee2DaoTest` / `StoredProcedureTestDaoTest` /
`TruncateTableDaoTest`、Tiger `StoredProcedureTestDaoTest`)は実テストが 0 件のため実行スイートから除外した。

### 2.2 実行時に除外した 1 メソッド(サンプル DAO 側の SQL 欠陥)

| サンプル | テスト | 除外理由 |
|---|---|---|
| 定数 | `EmployeeDaoTest#testGetEmployeeByDeptno` | 下記の通り、**両 DB で同一に失敗する sample 由来の曖昧カラム**のため除外 |

`examples.dao.EmployeeDao#getEmployeeByDeptno` は `getEmployeeByDeptno_QUERY = "deptno = /*deptno*/123"` を
持つ。`Employee` は `Department` への N:1 リレーション(`department_RELNO=0`)を持つため、S2Dao の自動 SELECT は
`EMP LEFT OUTER JOIN DEPT department ON EMP.deptno = department.deptno` を生成し、QUERY 中の**修飾なし `deptno`**
が EMP.DEPTNO と DEPT.DEPTNO の双方に該当して曖昧になる。

- 旧環境(H2 Oracle): `Ambiguous column name "DEPTNO"`(ErrorCode 90059)
- 新環境(PostgreSQL): `ERROR: column reference "deptno" is ambiguous`
- 実 Oracle でも `ORA-00918 column ambiguously defined` となる想定。

**新旧・Oracle/PostgreSQL いずれでも同一に失敗する sample 側の SQL 記述欠陥**であり、DB 方言差でも
ジェネレーターのバグでもない(移行リスクではない)。QUERY 内のカラムを `emp.deptno` と修飾すれば解消する。
再現性のため実行スイートから明示除外し、`RunGeneratedTests` の `Class!method` 記法で記録している。

---

## 3. エビデンス CSV 一致検証

`verification/run/compare/compare.py`(python3)で正規化済み CSV を突き合わせた。

- **比較ファイル数: 75(定数 37 + Tiger 38)**
- **一致: 75 / 不一致: 0 / 片側のみ: 0 → 完全一致(PASS)**
- 除外カラム(S2Dao が自動更新し実行時刻・更新回数で変動しうる列): **`TSTAMP` / `TIMESTAMP` / `VERSIONNO` / `VERSION_NO`**

CSV は testsupport の `EvidenceWriter` が正規化済み(カラム名大文字、数値は正準形 `10.0→10`、日付は
`yyyy-MM-dd HH:mm:ss`、NULL はリテラル `NULL`、UTF-8/LF)。したがって突き合わせは正規化後の文字列一致で行う。

### 3.1 除外カラムの妥当性(実測)

- `EMP.TSTAMP`(自動タイムスタンプ): `EmployeeAutoDao#update` の操作後データセットで
  OLD=`2026-07-03 22:45:35` / NEW=`2026-07-03 22:45:43` と**実行時刻で変動**。除外が必要。
  - 一方、`EmployeeDao#update` は明示 SQL(`UPDATE emp SET ename=… WHERE empno=…`)で tstamp を触らないため
    OLD/NEW とも `2001-01-01 00:00:00` のまま一致。
- `DEPT.VERSIONNO`(楽観ロック): `DepartmentDao#update` で OLD/NEW とも `0→1` と**決定的**に一致するが、
  方針(自動更新列は除外)に従い比較対象外とした。

### 3.2 一致確認の代表例

- Bean 戻り値(`EmployeeDao#getEmployee` の `Employee`): N:1 の `DEPARTMENT` 関連オブジェクト
  (`"50, SALES, TOKYO, 0"`)や S2Dao 拡張プロパティ `MODIFIEDPROPERTYNAMES`(`[]`)を含め OLD/NEW 完全一致。
- 件数(`getCount` → `1`)、配列戻り値(`getAllEmployeeNumbers`)、INSERT/UPDATE/DELETE 後のデータセットも一致。

---

## 4. ジェネレーター / testsupport への修正

DAO 挙動検証の過程で判明した**ジェネレーターのバグ 1 件のみ**を修正した(他は環境側・検証インフラ側で対処)。

### 4.1 修正: 生成可能メソッド 0 件クラスの setUp がコンテナ/DB を初期化していた

- 対象: `generator/.../gen/TestClassGenerator.java`(`emitSetUp`)
- 症状: 全メソッドがスキップ対象の DAO(例 `TruncateTableDao`=DDL のみ、`Employee2Dao`=BEAN/SQL 解決不能)でも、
  プレースホルダクラスの `setUp` が `ctx.getComponent(...)` を呼んでいた。これらは S2Dao が bean を解決できず、
  `getComponent` が実行時例外となり、ダミーテスト `testNoGeneratableMethods` が**エラー**になっていた。
- 修正: 生成可能メソッドが 0 件の場合、`setUp` で S2Container / DB 接続を初期化しない(`getComponent` を呼ばない)。
- 追加テスト: `generator/src/test/java/com/example/s2daotestgen/PlaceholderSetUpTest.java`
  (プレースホルダクラスは `getComponent` を含まないこと、実 DAO では含むことを検証)。
- `mvn test` 緑を維持(generator 21 / testsupport 14、いずれも成功)。

> 注: 検証インフラ側(`verification/`)で行った「実行メソッド順の固定」「1 メソッドの明示除外」は
> ジェネレーター/テストサポートの改変ではなく、実行ドライバ `RunGeneratedTests` の機能である(§5 参照)。

---

## 5. 発見した知見(実 Oracle→PostgreSQL 移行で役立つもの)

1. **修飾なしカラムと自動リレーション JOIN の相性(最重要)**
   S2Dao はエンティティに N:1 リレーションがあると自動 SELECT に `LEFT OUTER JOIN` を足す。このとき業務 QUERY
   内の**修飾なしカラム**(`deptno` 等)が結合先と衝突して曖昧になる。**Oracle(ORA-00918)/PostgreSQL(ambiguous)/
   H2 いずれでも同一に失敗**する。移行前に QUERY・2-way SQL のカラムはテーブル修飾しておくこと(§2.2)。

2. **JUnit3 のメソッド実行順は JVM 間で不定 → 残存データ経由で結果が揺れる**
   `Class.getMethods()` の順序は保証されず、旧環境と新環境の別 JVM 実行で順序が変わりうる。共有テーブル
   (本件 `DEPT`)を後続テストが**シードせず参照**すると、直前テストの残存状態が環境間でずれ、エビデンスが
   不一致になった(`EmployeeAutoDao` の関連オブジェクト・`getEmployeeByDname` の件数)。
   対策として実行ドライバ `RunGeneratedTests` で**テストメソッドを名前順に固定実行**し、両環境で残存状態を
   一致させた。移行検証では「決定的な実行順序」と「テストごとの完全なデータ準備」を徹底すること。

3. **自動タイムスタンプ列は実行時刻で必ず変動する**
   S2Dao 自動 UPDATE は `TIMESTAMP` 型プロパティ(本件 `EMP.TSTAMP`)を実行時刻で更新する。新旧比較では
   この列を除外必須。逆に**楽観ロック列(`versionNo`)は決定的**(`0→1`)で環境間一致する(§3.1)。

4. **数値・型の正規化で方言差を吸収できる**
   `NUMERIC` 列は H2/PostgreSQL とも `BigDecimal` で返る。`sal`/`comm` は `Float`。`EvidenceWriter` の正準化
   (末尾ゼロ除去 `10.0→10`)により、DB 実装差(スケール・型ラップの差)を吸収して比較できた。

5. **同一 DDL・同一 dicon(4 行差)で両環境を賄える**
   Oracle 方言寄りの 1 本の DDL がそのまま H2 Oracle モードと PostgreSQL 16 で通り、dicon の差分は DataSource の
   `driverClassName`/`URL`/`user`/`password` の 4 行のみ。実移行でもドライバ/接続定義の差し替えで足りる見込み。

6. **Tiger アノテーションは定数版と同じ `AnnotationReaderFactoryImpl` で解釈できる**
   `org.seasar.dao.impl.AnnotationReaderFactoryImpl` が JDK5+ で `@S2Dao`/`@Bean`/`@Column`/`@Arguments`/`@Sql`
   を自動判別するため、両サンプルで dicon の S2Dao コンポーネント構成を共通化できた。

---

## 6. 環境制約による検証上の限界

- 実 Oracle11g・実 Java5 VM は本環境で入手不可(`docs/CONSTRAINTS.md`)。旧環境は **H2 Oracle 互換モード +
  `-source/-target 1.6`(Java5 互換構文)+ JUnit3.8** で代替した。Oracle 固有関数・`ROWNUM` 細部・`NUMBER` 端数など
  Oracle 固有挙動の完全一致は本検証の範囲外(標準 CRUD・2-way SQL のバインド/結果セットは検証済み)。
- PostgreSQL は要件 16.8 に対し apt の 16.13(マイナー差、SQL 挙動に影響なしと判断)。
- ストアドプロシージャ・DDL 実行メソッド・`FetchHandler` 引数・BEAN/SQL 解決不能 DAO はジェネレーター未対応で
  テスト未生成(§2.1)。これらは本ツールの対象外として記録する。

---

## 7. 再現手順

```bash
# 0) 生成(JDK21 で可)
cd generator && mvn -q package && cd ..
java -jar generator/target/s2dao-testgen.jar gen-all \
  --src verification/samples/s2dao/s2-dao-examples/src/main/java \
  --sql verification/samples/s2dao/s2-dao-examples/src/main/resources \
  --out verification/generated/s2dao --dbms oracle
java -jar generator/target/s2dao-testgen.jar gen-all \
  --src verification/samples/s2dao-tiger/s2-dao-tiger-examples/src/main/java \
  --sql verification/samples/s2dao-tiger/s2-dao-tiger-examples/src/main/resources \
  --out verification/generated/s2dao-tiger --dbms oracle

# 1) 旧環境相当(H2 Oracle モード)
bash verification/run/run-old-env.sh

# 2) 新環境(PostgreSQL16 / 事前に verification/setup/pg-setup.sh)
bash verification/run/run-new-env.sh

# 3) 一致検証(JUnit結果 + エビデンス CSV)
bash verification/run/compare/compare.sh
```
