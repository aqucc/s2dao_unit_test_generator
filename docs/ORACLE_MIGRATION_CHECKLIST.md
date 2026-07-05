# 実 Oracle 11g で生成テストを実行する際のチェックリスト

本書は「生成されたユニットテストコードが **実 Oracle 11g + ojdbc14/ojdbc5(JDBC3 世代)+ Java5/Eclipse** で
動作するか」の観点で、生成テストコード(verification/generated/ の実物)・testsupport ランタイム・
ジェネレーター(generator/src/main/java/com/example/s2daotestgen/gen/)を静的レビューした結果と、
実 Oracle で実行する際の手順・注意点をまとめたものである。

- 本環境では実 Oracle が調達不可のため(docs/CONSTRAINTS.md)、動的検証は
  H2 Oracle 互換モード(旧環境サロゲート)と PostgreSQL 16 実 DB で行った。
  その結果は docs/VERIFICATION_DB_CONNECTIVITY.md を参照。
- Oracle 固有セマンティクスの実動作プローブは `verification/oracle/semantics-probe/` にある。

---

## 1. レビュー結果一覧(問題 → 影響 → 対処)

### 1-1. 修正済み(コード/テンプレートを直したもの)

| # | 重要度 | 問題 | 影響 | 対処 |
|---|--------|------|------|------|
| F1 | **高** | Eclipse プロジェクトテンプレート(old-env / new-env)の `.classpath` に **`aopalliance-1.0.jar` が無い** | S2AOP の `MethodInterceptor` 親インタフェースが無く、S2Container 初期化(`S2DaoInterceptor` を含む dicon の読込)が `NoClassDefFoundError: org/aopalliance/intercept/MethodInterceptor` で失敗。**全テストが実行不能** | `.classpath` 両方に `lib/aopalliance-1.0.jar` を追加(実行クラスパスの最小構成実験で必須と確認) |
| F2 | **高** | 同 `.classpath` に **`geronimo-j2ee_1.4_spec-1.0.jar` が無い** | S2Container 初期化時に `javax.servlet.http.HttpServletRequest` がクラス参照され `NoClassDefFoundError` で失敗。**全テストが実行不能**(aopalliance より先に発生) | `.classpath` 両方に `lib/geronimo-j2ee_1.4_spec-1.0.jar` を追加。なお `geronimo-ejb_2.1_spec` / `portlet-api` / `poi` は実行時不要と実験で確認(追加不要) |
| F3 | **高** | `WriteDbUtil` が null 値を **型無しの `setObject(i, null)`** でバインドしていた | Oracle JDBC(ojdbc14 等)は型無し `setObject(i, null)` を **ORA-17004 (Invalid column type)** で拒否する。null を含むテストデータ投入が実 Oracle で失敗 | `setNull(i, java.sql.Types.NULL)` に変更(JDBC3 API。Oracle/PostgreSQL/H2 とも受理)。単体テスト `WriteAndDatasetTest#testNullBindingOnStringAndNumericColumns` を追加 |
| F4 | 中 | old-env テンプレートの README/コメントが **`ojdbc6.jar` を Java5 で使える**かのように読めた | `ojdbc6.jar` は Java6+ 用クラスファイル(major50)であり、Java5 JVM では `UnsupportedClassVersionError`。 | `.classpath` コメントと README を「Java5 では ojdbc14(10.2 系)か ojdbc5(11g 系)」に修正 |
| F5 | 低 | 実 Oracle 用の DDL / dicon / properties のひな形が無かった(schema.sql は `DROP TABLE IF EXISTS` を使っており **Oracle 11g では構文エラー**) | 利用者が実 Oracle で流す DDL・接続定義を自作する必要があった | `verification/oracle/` に `schema-oracle.sql`(NUMBER/VARCHAR2/DATE/TIMESTAMP、DROP は ORA-00942 無視運用+PL/SQL 例外無視ブロック併記)、`s2dao-oracle.dicon` / `s2dao-tiger-oracle.dicon`、`s2daotest-oracle.properties.example` を追加 |

### 1-2. 設計上問題なし(レビューで確認し、根拠を明記するもの)

| # | 観点 | 結論 |
|---|------|------|
| O1 | **JDBC API 範囲(ojdbc14 = JDBC3 適合)** | testsupport・生成テスト・検証ランナーが使う java.sql API は `DriverManager.getConnection` / `Connection.prepareStatement・createStatement・setAutoCommit・close` / `PreparedStatement.setObject・setNull・executeQuery・executeUpdate・close` / `ResultSet.next・getObject・close` / `ResultSetMetaData.getColumnCount・getColumnLabel・getColumnName` / `Statement.execute・close` のみ。**すべて JDBC1〜2 世代の API であり JDBC3(ojdbc14)に存在する**。JDBC4+ の API(`Connection.isValid` / `createBlob` / `getNClob` / `unwrap` / `isWrapperFor` / `getObject(int,Class)` 等)は不使用(grep で機械確認)。try-with-resources 等の Java7+ 構文も不使用(Java5 検証済み: docs/VERIFICATION_JAVA5_ECLIPSE.md) |
| O2 | **空文字列 = NULL 問題** | Oracle は `''` を NULL として格納する。ジェネレーターが埋め込むリテラル(`TestValues`)と実行時の埋め草値(`ValueFactory`)は **空文字を生成しない**(シード表+カラム名由来の非空文字列のみ。`ValueFactoryTest#testStringValuesNeverEmpty` で機械確認)。よって生成テストの投入データ・引数・期待値に `''` は現れず、assert も壊れない。なお `EvidenceWriter.normalize` は null→`NULL`、空文字→空欄と**別表現**で出力するため、万一利用者データに `''` が混じっても差異は黙って消えず可視化される(実動作プローブ (a) で確認。H2 Oracle モードは実 Oracle 同様 ''→NULL、PostgreSQL は '' のまま) |
| O3 | **DATE 型マッピング** | ojdbc のバージョンで DATE 列の `getObject` 戻り型が揺れる(ojdbc14(10.2)=`java.sql.Date`、ojdbc5/6(11g)=`java.sql.Timestamp`)。`EvidenceWriter.normalize` は `java.util.Date` 系をすべて `yyyy-MM-dd HH:mm:ss` に統一するため、**時刻成分なしの値なら戻り型の揺れはエビデンスに現れない**。生成テストデータは DATE 列(hiredate)に `java.sql.Date`(時刻なし)のみを使い、時刻付きは TIMESTAMP 列(tstamp)のみ。→ 注意点 C2 も参照 |
| O4 | **NUMBER 精度と BigDecimal 正規化** | Oracle の `getObject(NUMBER)` は常に `BigDecimal`(スケールは格納値依存: `3001` / `3001.00`)。他 DB は `Integer`/`Long`/`BigDecimal` と実装差がある。`EvidenceWriter.normalize` が BigDecimal の末尾ゼロ除去(+`toPlainString` で指数表記回避、0 特別扱いで Java5 の `stripTrailingZeros("0.00")` バグも回避)を行い正準形に揃えるため、実装差はエビデンス・`GetDatasetUtil.find/count` の照合に現れない(実動作プローブ (c) で PG=`3001.00`/H2=`3001` が同一正準形 `3001` になることを確認)。テスト値は DDL の桁幅に収まる小さい整数のみで、Float/Double の 2 進丸めも発生しない |
| O5 | **識別子の大文字化** | Oracle は無引用識別子を大文字に折り畳む(PostgreSQL は小文字)。`WriteDbUtil` / `GetDatasetUtil` / 生成テストが発行する SQL のテーブル名・カラム名は**すべて無引用**(`"` で囲まない)ため、`emp`・`EMP` どちらの表記でも両 DB で同一テーブルに解決される。読み取り側はカラム名を `toUpperCase()` で正規化して照合・出力するため、メタデータの大文字/小文字差も吸収される |
| O6 | **セッションのタイムゾーン・NLS 設定** | testsupport・生成コードは `TO_CHAR`/`TO_DATE`/`SYSDATE` 等の NLS 依存 SQL を発行しない。日時は `java.sql.Date/Timestamp.valueOf`(壁時計)で投入し `getObject`+`SimpleDateFormat` で読み出すため、DATE/TIMESTAMP(TZ なし)の値はセッション TZ・`NLS_DATE_FORMAT` の影響を受けない。`TIMESTAMP WITH (LOCAL) TIME ZONE` 列は対象外(→ C5) |

### 1-3. チェックリスト記載に留めたもの(実 Oracle 実行時の注意点)

| # | 重要度 | 注意点 |
|---|--------|--------|
| C1 | **高** | **Java5 で使うドライバは ojdbc14.jar(10.2 系。11g に接続可)か ojdbc5.jar(11g 系)**。ojdbc6.jar は Java6+ 専用(major50)。クラス名はいずれも `oracle.jdbc.OracleDriver` でよい |
| C2 | **高** | **DATE 列に時刻成分を持つデータを入れない/入っている場合の対処**: 実 Oracle の DATE は時刻を保持する(実動作プローブで H2 Oracle モードも同挙動と確認)が、(1) ojdbc14 の既定では `getObject` が `java.sql.Date` を返し**時刻が消えて** 00:00:00 と出力される(`-Doracle.jdbc.V8Compatible=true` で `Timestamp` になり保持)、(2) PostgreSQL の DATE は時刻を持たないため 00:00:00 になる。**時刻付きの値が DATE 列にあると新旧エビデンスは不一致になる**。生成テストデータは DATE 列に時刻なし値のみを使うため既定で安全。既存データを SELECT するテストで DATE 列に時刻が入っている場合は、新環境側で当該列を TIMESTAMP にする・比較除外にする等の判断が必要 |
| C3 | 中 | **s2daotest.properties の jdbc.\* と dicon の xaDataSource は必ず同一 DB・同一スキーマ**を指すこと(前者はデータ投入/データセット取得、後者は DAO 実行。別スキーマだと「投入したのにヒットしない」「[EDAO0017]No SELECT list(表メタデータが見えない)」等になる)。接続ユーザーの既定スキーマに EMP/DEPT/NOPKTABLE を作るのが簡単 |
| C4 | 中 | **CHAR 列は使わない(VARCHAR2 を使う)**: Oracle の CHAR(n) は空白パディングされ、`v.toString()` の末尾空白がそのままエビデンスに出て他 DB と食い違いうる。schema-oracle.sql は VARCHAR2 のみ使用 |
| C5 | 中 | **`TIMESTAMP WITH (LOCAL) TIME ZONE` / `INTERVAL` 列は対象外**: `getObject` が `oracle.sql.TIMESTAMPTZ` 等の Oracle 独自クラスを返し、normalize は `toString()` 頼みになる。生成対象のエンティティにこれらの型が現れる場合は列を比較除外にする |
| C6 | 中 | **BOOLEAN 型カラムは Oracle に無い**: エンティティに boolean プロパティがある場合、Oracle 側は NUMBER(1) 等で表現され S2Dao の値型変換に依存する。サンプルには存在しないが、利用者の DAO にある場合は投入値(`ValueFactory` は `Boolean` を返す)が `setNull`/`setObject` でエラーになりうるため、DDL を NUMBER(1) にし投入カラムから外す等の調整が必要 |
| C7 | 低 | **ORDER BY の照合順**: `GetDatasetUtil` は主キー(数値)で ORDER BY するため通常影響しないが、文字列カラムで並べる場合は Oracle(既定 NLS_SORT=BINARY)と PostgreSQL(ロケール依存)で順序が変わりうる。生成テストデータは ASCII 大文字のみで実害なし |
| C8 | 低 | **NUMBER の超高精度値**: NUMBER は最大 38 桁。エンティティ側が Float/Double だと約 7/15 桁で丸まる。生成テスト値は小さい整数のみのため影響なし。既存データを扱う場合のみ注意 |
| C9 | 低 | **自動更新カラムはエビデンス比較から除外**: S2Dao が自動設定する TIMESTAMP 列(TSTAMP)・バージョン列(VERSIONNO)は実行時刻依存のため、compare.py が既定で除外する(TIMESTAMP / TSTAMP / VERSIONNO / VERSION_NO)。実 Oracle でも同じ除外で比較すること |
| C10 | 低 | **DDL の DROP**: Oracle 11g に `DROP TABLE IF EXISTS` は無い。schema-oracle.sql は素の DROP(初回の ORA-00942 は無視して継続)を採用。SQL*Plus でエラー表示も避けたい場合は同ファイル末尾コメントの例外無視 PL/SQL ブロック版を使う |
| C11 | 低 | **シーケンス/トリガー由来の PK は生成対象サンプルに無い**: 生成テストは PK を明示投入する。利用者の DAO が INSERT 時にシーケンス採番する場合は、schema-oracle.sql 相当に `CREATE SEQUENCE` を足すこと |

---

## 2. 実 Oracle 11g での実行手順

前提: Oracle 11g インスタンス(SID 例: ORCL)、テスト専用ユーザー(例: scott)、
ojdbc14.jar または ojdbc5.jar(Java5 の場合。Java6+ なら ojdbc6.jar も可)。

### 2-1. DDL

```
sqlplus scott/tiger@ORCL @verification/oracle/schema-oracle.sql
```
- 初回の `DROP TABLE` は ORA-00942 になるが無視してよい(SQL*Plus は継続する)。
- 検証ランナー経由なら `RunGeneratedTests` の第 1 引数に `verification/oracle/schema-oracle.sql` を渡す
  (ランナーが DDL エラーを `[ddl-warn]` として無視して継続する)。

### 2-2. dicon

`verification/oracle/s2dao-oracle.dicon`(定数アノテーション版)/
`s2dao-tiger-oracle.dicon`(Tiger 版)をクラスパスルートへ(例: `app.dicon` の名前で)コピーし、
`xaDataSource` の URL / user / password を実環境に合わせて書き換える:

```xml
<property name="driverClassName">"oracle.jdbc.OracleDriver"</property>
<property name="URL">"jdbc:oracle:thin:@127.0.0.1:1521:ORCL"</property>   <!-- SID 指定 -->
<!-- サービス名なら "jdbc:oracle:thin:@//host:1521/service_name" -->
<property name="user">"scott"</property>
<property name="password">"tiger"</property>
```

対象 DAO の `<component>` 行は利用者の DAO クラスに合わせて追加・削除する。

### 2-3. s2daotest.properties

`verification/oracle/s2daotest-oracle.properties.example` を `s2daotest.properties` に
リネームしてクラスパスルートへ置き、jdbc.\* を **dicon と同じ接続先**に合わせる:

```properties
jdbc.driver=oracle.jdbc.OracleDriver
jdbc.url=jdbc:oracle:thin:@127.0.0.1:1521:ORCL
jdbc.user=scott
jdbc.password=tiger
dialect=oracle
dicon=app.dicon
```

### 2-4. クラスパス(実行時に必要な jar 一式)

実行クラスパスの最小構成実験(docs/VERIFICATION_DB_CONNECTIVITY.md)で確認した必須 jar:

```
junit-3.8.2.jar
s2dao-testgen-support.jar        … testsupport を mvn package(Java5 世代 major49)
s2-framework-2.3.23.jar
s2-extension-2.3.23.jar
s2-dao-1.0.52.jar
s2-dao-tiger-1.0.52.jar          … Tiger アノテーション版を使う場合
ognl-2.6.9.jar
javassist-3.18.1-GA.jar
commons-logging-1.1.1.jar
geronimo-jta_1.1_spec-1.0.jar
geronimo-j2ee_1.4_spec-1.0.jar   … javax.servlet 等(S2Container 初期化に必須)
aopalliance-1.0.jar              … S2AOP の MethodInterceptor 親(必須)
ojdbc14.jar または ojdbc5.jar     … Java5 の場合(Java6+ なら ojdbc6.jar 可)
```

`geronimo-ejb_2.1_spec` / `portlet-api` / `poi` は**実行時不要**(コンパイル・Excel DataSet 用)。

### 2-5. 実行コマンド(コマンドライン)

```bash
# コンパイル(生成テスト + entity/DAO ソース + ランナー)
javac -encoding UTF-8 -source 1.5 -target 1.5 -cp "<上記jar一式>" \
      -sourcepath "<daoソース>:<生成テストdir>" -d build \
      verification/runner/RunGeneratedTests.java <生成テスト>.java...

# .sql を DAO と同じパッケージパスへ、dicon/properties をクラスパスルートへ配置後:
java -cp "<上記jar一式>:build" \
     -Ds2daotest.config=build/s2daotest.properties \
     -Ds2daotest.evidence.dir=evidence-oracle \
     RunGeneratedTests verification/oracle/schema-oracle.sql \
     examples.dao.EmployeeDaoTest examples.dao.DepartmentDaoTest ...
```

- ojdbc14 使用時に DATE 列へ時刻付きデータがある場合のみ `-Doracle.jdbc.V8Compatible=true` を付ける(C2)。

### 2-6. Eclipse(Pleiades)での実行手順

1. `verification/eclipse-project-templates/old-env/` をワークスペースへコピーしてインポート
   (J2SE-1.5 実行環境・MS932。詳細は同ディレクトリの README-old.md)。
2. `src/` に生成テスト(`--encoding MS932` で生成)と entity/DAO ソースをパッケージ構成のまま配置。
   S2Dao の 2-way SQL ファイル(`*.sql`)も **DAO と同じパッケージ**へ置く(ビルドで bin/ にコピーされる)。
3. `lib/` に 2-4 の jar 一式を配置し、Oracle ドライバは `lib/ojdbc.jar` の名前で置く
   (`.classpath` は追加済みの `aopalliance-1.0.jar` / `geronimo-j2ee_1.4_spec-1.0.jar` を含めて参照する)。
4. `src/` 直下(クラスパスルート)に `s2daotest.properties` と dicon(2-2, 2-3)を置く。
5. スキーマ投入(2-1)を済ませてから、`src` 上で右クリック > 実行 > JUnit テスト
   (JUnit3 ランナー。テストは `junit.framework.TestCase` 形式)。
6. エビデンス CSV は既定で `./evidence`(プロジェクトルート相対)。出力先を変える場合は
   実行構成の VM 引数に `-Ds2daotest.evidence.dir=<dir>` を追加。

### 2-7. 新旧突き合わせ

旧(実 Oracle)・新(PostgreSQL 16)それぞれのエビデンスディレクトリを
`verification/compare/compare.py <oldDir> <newDir>` に渡す
(自動更新カラム TIMESTAMP/TSTAMP/VERSIONNO/VERSION_NO は既定で除外)。

## 3. DB キャラクタセットを SJIS 系にする場合(テスト用コンテナの張り替え運用)

旧本番が JA16SJIS / JA16SJISTILDE の場合、テスト用 Oracle XE 11g コンテナは
初回起動直後に `infra/old-db-oracle11g/charset-ja16sjis.sh` で
キャラクタセットを張り替える(非サポート操作。詳細・条件は
`infra/README.md` の「文字コードの制約」を参照)。

| 項目 | 内容 |
|---|---|
| 効果 | VARCHAR2 のバイト長セマンティクス(全角2バイト)・ORA-12899 の出方・LENGTHB/SUBSTRB・BINARY ソート順が旧本番と一致する |
| 実施時期 | 初回起動直後・日本語データ投入前に一度だけ |
| charset の選定 | 旧本番実機の `SELECT value FROM nls_database_parameters WHERE parameter='NLS_CHARACTERSET';` に合わせる(TILDE 差に注意) |
| **classpath 追加** | **ojdbc と同版の `orai18n.jar` が必須**(thin 組み込み変換は ASCII/ISO8859-1/UTF-8 系のみ。無いと「Non supported character set」)。Eclipse テンプレートの `.classpath` にコメントアウト済みエントリあり |
| 禁止事項 | props$ の直接 UPDATE / NLS_NCHAR_CHARACTERSET の変更 / 本番系への適用 |
| やり直し | `docker compose down -v` で作り直し |
