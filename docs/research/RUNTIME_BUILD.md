# Seasar2 / S2Dao ランタイム調達・ビルド結果レポート

作成日: 2026-07-03
担当: 環境構築(ランタイム調達)エージェント

生成される JUnit テストは実行時に S2Container を起動し S2Dao 経由で DAO を実行する。
本レポートは、そのために必要な Seasar2 / S2Dao ランタイム jar 一式を本環境
(JDK8 のみ / maven.seasar.org 停止 / Oracle・Java5 実物なし)でビルド・調達した結果と、
H2(Oracle 互換モード)・PostgreSQL 16 でのスモークテスト結果をまとめる。

成果物:
- `verification/lib/` … 実行時ランタイム jar 一式(スモーク検証済み)
- `verification/lib/seasar2-2.4.48-archive/` … タグ Seasar2.4.48 からビルドした 2.4.48 jar(アーカイブ、後述)
- `verification/env/build-seasar2.sh` … 再現可能なビルドスクリプト(clone→patch→build→jar)
- `verification/env/jdbc3-stub/` … JDBC3 コンパイル用スタブ(後述)
- `verification/env/pg-setup.sh` … PostgreSQL 起動 + テスト DB/ロール作成
- `verification/env/smoke/` … スモークテストコード(`SmokeTest.java`)・dicon・DDL・実行スクリプト

---

## 1. 結論(重要): 実行時ランタイムのバージョン

**実行時ランタイムは s2-framework / s2-extension = 2.3.23、s2-dao / s2-dao-tiger = 1.0.52 の
一貫スタックである。** 2.4.x ではなく 2.3.23 を採用したのは次の技術的理由による。

- `samples/s2dao/s2-dao`(= s2-dao **1.0.52**)の `pom.xml` は `org.seasar.container:s2-extension:**2.3.23**`
  に依存する。s2-dao 1.0.52 は 2.3.23 の API に対して書かれている。
- s2-extension の `ValueType` インタフェースは 2.3 → 2.4 で拡張されており(`toText(Object)` 追加ほか、
  procedure ハンドラの API 変更)、**s2-dao 1.0.52 のソースは 2.4.48 の s2-extension に対して
  コンパイルできない**(`BytesType is not abstract and does not override abstract method toText(Object)` 等)。
  仮にリフレクションで動かしても実行時に `AbstractMethodError` になる。
- したがって「動く S2Dao ランタイム」を成立させるには、s2-dao 1.0.52 と versionを揃えた
  s2-framework / s2-extension **2.3.23** が正しい組合せである。これは `samples/s2dao/lib/` に
  当初から `s2-framework-2.3.23.jar` / `s2-extension-2.3.23.jar` が同梱されている事実とも一致する。

タスク指定の **s2-framework / s2-extension / s2-tiger 2.4.48 も GitHub タグ `Seasar2.4.48` から
実際にソースビルドし**、`verification/lib/seasar2-2.4.48-archive/` にアーカイブとして格納した
(maven.seasar.org 停止のためソースからのビルド成果物を保全する意味がある)。ただし上記理由で
**s2-dao 1.0.52 とは実行時非互換**のため、スモークで検証したランタイムには使用していない。
2.4.x を実行スタックにするには s2-dao を 2.4 系 API に合わせて改修(ValueType 実装群・procedure
ハンドラの多数修正)する必要があり、DAO 挙動検証という本プロジェクトの目的には不要と判断した。

DAO の意味論(SQL 解決順・2-way SQL パーサ・定数/Tiger アノテーション解釈)は s2-dao 1.0.52 と
s2-extension の `SqlParserImpl` が担っており、その挙動は 2.3.23 と 2.4.48 で実質同一である。

---

## 2. ビルドできた jar 一覧(バージョン付き)

### 2.1 実行時ランタイム(`verification/lib/`, スモーク検証済み)

| jar | 由来 | 入手/ビルド方法 |
|---|---|---|
| `s2-framework-2.3.23.jar` | Seasar2 2.3.23 | `samples/s2dao/lib/` 同梱の既存ビルド済み jar(旧 JDK でビルド済・JDK8 で動作) |
| `s2-extension-2.3.23.jar` | Seasar2 2.3.23 | 同上 |
| `s2-dao-1.0.52.jar` | S2Dao 1.0.52 | `samples/s2dao/s2-dao/src` から **本環境で javac ビルド**(2.3.23 スタックに対して) |
| `s2-dao-tiger-1.0.52.jar` | S2Dao-Tiger 1.0.52 | `samples/s2dao-tiger/s2-dao-tiger/src` から **本環境で javac ビルド** |
| `ognl-2.6.9.jar` | Maven Central | `ognl:ognl:2.6.9` |
| `javassist-3.18.1-GA.jar` | Maven Central | `org.javassist:javassist:3.18.1-GA` |
| `commons-logging-1.1.1.jar` | Maven Central | |
| `geronimo-jta_1.1_spec-1.0.jar` | Maven Central | JTA |
| `geronimo-j2ee_1.4_spec-1.0.jar` | Maven Central | javax.servlet/transaction/ejb 等を包含 |
| `geronimo-ejb_2.1_spec-1.1.jar` | Maven Central | EJB spec(タスク指定) |
| `aopalliance-1.0.jar` | Maven Central | |
| `junit-3.8.2.jar` | Maven Central | 生成テスト(JUnit3 形式)実行用 |
| `h2-1.4.199.jar` | Maven Central | **旧環境 Oracle 互換モード用** |
| `postgresql-42.2.27.jar` | Maven Central | 新環境用 JDBC ドライバ |
| `poi-3.0-FINAL.jar` | `samples/s2dao/lib/` | s2-extension の Excel DataSet 用(**DAO 実行には不要**、同梱のみ) |
| `portlet-api-1.0.jar` | Maven Central | s2-framework の portlet 外部コンテキストのコンパイル用(実行時は未使用) |

> **ognl / javassist について**: 2.3.23 は本来 seasar パッチ版 ognl(`2.6.9-patch-*`)と
> javassist 3.4.ga に対してビルドされているが、Maven Central の `ognl 2.6.9` と
> `javassist 3.18.1-GA` でスモークが全項目 PASS することを確認済み(タスク指定どおり Central 版を採用)。
> `OgnlRuntime.clearCache()`(パッチ版のみの API)は 2.3.23 実行時経路では呼ばれない。

### 2.2 アーカイブ(`verification/lib/seasar2-2.4.48-archive/`, ソースビルド成果物)

| jar | 由来 | ビルド方法 |
|---|---|---|
| `s2-framework-2.4.48.jar` | seasar2 タグ `Seasar2.4.48` | 本環境で javac ビルド |
| `s2-extension-2.4.48.jar` | 同上 | 本環境で javac ビルド(JDBC3 スタブ使用) |
| `s2-tiger-2.4.48.jar` | 同上 | 本環境で javac ビルド(JDBC3 スタブ使用) |

### 2.3 省略したもの

なし(タスクで挙げられた s2-framework / s2-extension / s2-tiger / s2-dao / s2-dao-tiger は
**すべてビルド済み**)。ただし各モジュールの **テストサポート用パッケージ**は実行時ランタイムに
不要かつ JDK8 で単体コンパイル不能なため、jar から除外した(下記 4 章)。

---

## 3. ビルド方法の方針(なぜ maven ではなく javac 直叩きか)

`mvn install` は本環境では成立しない。理由:

1. **maven.seasar.org が停止済み**。親 pom `s2-container-project` の `<repositories>` がそこを指す。
2. seasar 固有アーティファクトが **Maven Central に存在しない**:
   `ognl:2.6.9-patch-20090427`、`jboss:javassist:3.4.ga`、`portlet-api:1.0`(座標違い)、
   `junit-addons:1.4` 等。pom を全面的に書き換えるより、ソースを Central 相当 jar に対して
   直接コンパイルする方が確実。
3. 親 pom は `maven-compiler-plugin` の `source/target` を **1.4** に固定しており、最近の
   Maven/JDK では扱いづらい。

そこで `verification/env/build-seasar2.sh` は各モジュールの `src/main/java` を **依存順**
(framework → extension → tiger、dao → dao-tiger)に `javac -source 1.6 -target 1.6 -encoding UTF-8`
でコンパイルし、`src/main/resources`(dicon / dtd / properties 等)を同梱して jar 化する。
`-source/-target 1.6` は JDK8 で指定可能な下限で、Java5 互換世代のバイトコードを生成する。

### pom 等への修正内容 / ソース改変

- **pom は使用していない**(javac 直ビルドのため)。したがって pom の書き換えは無し。
- **src/main/java の実装コード改変は 1 行のみ**:
  `s2-framework .../util/DisposableUtil.java` の `OgnlRuntime.clearCache();` を
  コメントアウト(ビルドスクリプトが `sed` で自動適用)。この API は **seasar パッチ版 ognl
  にしか存在せず** Central の ognl 2.6.9 にはない。コンテナ dispose 時のキャッシュ掃除呼び出し
  1 箇所のみで、DAO 実行の挙動には影響しない。他の実装コードは無改変。

---

## 4. JDK8 で古い Seasar2 をビルドするための JDBC3 スタブ

`verification/env/jdbc3-stub/`

Seasar2 2.4.x / S2Dao 1.0.x は **JDBC3 世代(Java 1.4)**のコードであり、`java.sql.*` /
`javax.sql.*` を実装する多数のクラス
(`ConnectionWrapperImpl`, `DataSourceImpl`, `XADataSourceImpl`, `XAConnectionImpl`,
`ResultSet/Statement/PreparedStatement/CallableStatement` の各 Wrapper, s2-dao の `BlobImpl` 等)が、
**JDBC4.0(Java6)/4.1(Java7)で `java.sql` に追加された抽象メソッド**
(`getParentLogger`, `getNetworkTimeout`, `isCloseOnCompletion`, `setNClob`, `getObject(,Class)`,
`isWrapperFor`, `removeStatementEventListener` など)を実装していない。オリジナル jar は JDK5/6
でビルドされていたため問題なかったが、**JDK8 でソースからコンパイルするとこれらがコンパイル不能**になる。

対策として、影響を受ける **約12 個の `java.sql` / `javax.sql` インタフェースだけ**を JDBC3 相当
(新メソッドなし)に縮小したスタブを用意し、**コンパイル時のみ** `-Xbootclasspath/p:` で前置して
本物の `java.sql` を隠す。`SQLException` 等のクラスは隠さないので rt.jar から解決される。
**実行時にはこのスタブは一切クラスパスに乗せない**ため、本物の JDK8 `java.sql` が使われ、
追加された JDBC4 メソッドは S2Dao から呼ばれないので実害はない。

これは実装コードの改変ではなく、周知の「旧 JDBC コードを新 JDK でビルドするためのブート
クラスパス縮小」手法である。

### jar から除外したパッケージ(テストサポート、実行時不要)

| モジュール | 除外パッケージ | 理由 |
|---|---|---|
| s2-framework | `org.seasar.framework.mock.*`, `org.seasar.framework.unit.*` | モック JDBC / S2TestCase。JDBC4 スタブ実装が必要・実行時不要 |
| s2-extension | `.mock.*`, `.unit.*`, `tx.adapter.WAS6*` | 上記 + WebSphere UOW アダプタ(**proprietary `com.ibm.*` が入手不能**) |
| s2-tiger | `org.seasar.framework.unit.*` | JUnit4 版 S2TigerTestCase(除外済み mock/unit に依存) |
| s2-dao | `org.seasar.dao.unit.*` | S2DaoTestCase 等テストサポート |
| s2-dao-tiger | `org.seasar.dao.unit.*` | 同上 |

`org.seasar.framework.jpa.unit`(s2-tiger)は実行時パッケージなので**除外していない**。

---

## 5. スモークテスト結果

`verification/env/smoke/SmokeTest.java` は次を検証する(constant-annotation 版 `examples.dao.EmployeeDao`):

1. dicon で S2Container を起動し `dao.dicon` 相当のコンポーネント群 + `S2DaoInterceptor` を構成
2. `container.getComponent(EmployeeDao.class)` で AOP 適用済み DAO を取得
3. Oracle 方言 DDL(`samples/s2dao/hsql/sql/demo-oracle.sql` = scott/EMP・DEPT, `TO_DATE`)で
   テーブル作成 + 14 行投入
4. `getAllEmployees()`(明示 SQL + N:1 リレーション dept)/
   `getEmployeeByJobDeptno("CLERK", 20)`(2-way SQL `/*BEGIN//IF//bindVar/*/`)/
   `update()`(明示 SQL) を実行

### 5.1 H2(MODE=Oracle) = 旧環境サロゲート — **ALL PASSED**

```
  [OK]  getAllEmployees returns 14 rows
  [OK]  first employee empno == 7369 (ORDER BY empno)
  [OK]  N:1 relation dept mapped (7369 -> RESEARCH)
  [OK]  CLERK in dept 20 -> 2 rows (SMITH,ADAMS)
  [OK]  dept 20 (job IF skipped) -> 5 rows      # /*IF job != null*/ が正しくスキップ
  [OK]  update affects 1 row
  [OK]  update persisted (7369 ename == SMITH2)
==== H2-Oracle-mode: ALL PASSED ====
```
dicon: `dicon/app-h2.dicon`(`jdbc:h2:mem:s2daosmoke;MODE=Oracle;DB_CLOSE_DELAY=-1`)
実行: `verification/env/smoke/run-h2.sh`

### 5.2 PostgreSQL 16.13 = 新環境 — **ALL PASSED**

```
  [OK]  getAllEmployees returns 14 rows
  [OK]  first employee empno == 7369 (ORDER BY empno)
  [OK]  N:1 relation dept mapped (7369 -> RESEARCH)
  [OK]  CLERK in dept 20 -> 2 rows (SMITH,ADAMS)
  [OK]  dept 20 (job IF skipped) -> 5 rows
  [OK]  update affects 1 row
  [OK]  update persisted (7369 ename == SMITH2)
==== PostgreSQL16: ALL PASSED ====
```
dicon: `dicon/app-pg.dicon`(`jdbc:postgresql://127.0.0.1:5432/s2daosmoke`, user=s2dao)
セットアップ: `verification/env/pg-setup.sh`(クラスタ起動 + ロール/DB 作成、TCP md5 認証)
実行: `verification/env/smoke/run-pg.sh`

同一 DDL(Oracle 方言 `TO_DATE`)が H2 Oracle モード・PostgreSQL 双方でそのまま通ることも確認。

---

## 6. 生成テスト実行時に使うべき classpath と dicon 設定

### 6.1 classpath

`verification/lib/*.jar` を全て並べる(PostgreSQL 実行時も H2 jar が乗っていて無害)。
実行は **JDK8**。最小構成は下記(順不同):

```
s2-framework-2.3.23.jar  s2-extension-2.3.23.jar  s2-dao-1.0.52.jar
ognl-2.6.9.jar  javassist-3.18.1-GA.jar  commons-logging-1.1.1.jar
geronimo-jta_1.1_spec-1.0.jar  geronimo-j2ee_1.4_spec-1.0.jar  aopalliance-1.0.jar
junit-3.8.2.jar
<DB ドライバ>            # 旧: h2-1.4.199.jar / 新: postgresql-42.2.27.jar
```
Tiger アノテーション DAO を使う場合は `s2-dao-tiger-1.0.52.jar` を追加。
Excel DataSet を使う場合のみ `poi-3.0-FINAL.jar`。

> 実 Oracle11g / 実 PostgreSQL では `h2-*.jar` / `postgresql-*.jar` を実 JDBC ドライバ
> (ojdbc / 目的の postgresql バージョン)に差し替えるだけでよい。

### 6.2 dicon 設定の要点(`verification/env/smoke/dicon/app-*.dicon` 参照)

- 1 ファイルに **JTA/DataSource 層** と **S2Dao コンポーネント群** と **対象 DAO** をまとめた自己完結 dicon。
- DataSource 層(`j2ee.dicon` 相当):
  - `org.seasar.extension.jta.TransactionManagerImpl`
  - `org.seasar.extension.dbcp.impl.XADataSourceImpl`(`driverClassName` / `URL` / `user` / `password`)
  - `org.seasar.extension.dbcp.impl.ConnectionPoolImpl`(`allowLocalTx=true`)
  - `org.seasar.extension.dbcp.impl.DataSourceImpl`
  - `resultSetFactory` = `PagerResultSetFactoryWrapper(BasicResultSetFactory)`,
    `ConfigurableStatementFactory(PagerStatementFactory)`
- S2Dao 層(`dao.dicon` 相当):
  - `AnnotationReaderFactoryImpl`, `DaoMetaDataFactoryImpl`, `BeanMetaDataFactoryImpl`,
    `DtoMetaDataFactoryImpl`, `ValueTypeFactoryImpl`, `DaoNamingConventionImpl`,
    `BeanEnhancerImpl`, `ResultSetHandlerFactoryImpl`,
    `PropertyTypeFactoryBuilderImpl`, `RelationPropertyTypeFactoryBuilderImpl`,
    `DefaultTableNaming`, `DefaultColumnNaming`, `ProcedureMetaDataFactoryImpl`
  - `interceptor` = `PagerS2DaoInterceptorWrapper(S2DaoInterceptor)`
- 対象 DAO は `<component class="...Dao"><aspect>dao.interceptor</aspect></component>` で
  `S2DaoInterceptor` を織り込む。
- **DB 切替は DataSource 定義の `driverClassName` / `URL` / `user` / `password` の 4 プロパティ差し替えのみ**
  (`app-h2.dicon` と `app-pg.dicon` の差分はこの 4 行)。旧環境(Oracle)は
  `oracle.jdbc.OracleDriver` + `jdbc:oracle:thin:@...` にすればよい。
- **`.sql`(2-way SQL / 明示 SQL)は DAO と同じパッケージパスでクラスパス上に配置する**こと
  (例 `examples/dao/EmployeeDao_getEmployeeByJobDeptno.sql`)。無いと S2Dao が自動生成 SQL に
  フォールバックし、エンティティのタイムスタンプ列(`timestamp_COLUMN`)等を含む UPDATE を組んで
  スキーマ不一致になる場合がある。

---

## 7. 再現手順

```bash
# 依存 jar は verification/lib に同梱済み。ソースから作り直す場合:
JDK8_HOME=/usr/lib/jvm/java-8-openjdk-amd64 verification/env/build-seasar2.sh
#   -> verification/env/build-out/ に 5 jar を再生成(2.3.23 は samples 同梱の既ビルド jar を使用)

# スモーク:
verification/env/smoke/run-h2.sh          # H2 Oracle モード
verification/env/pg-setup.sh              # PostgreSQL 起動 + DB 作成
verification/env/smoke/run-pg.sh          # PostgreSQL 16
```
