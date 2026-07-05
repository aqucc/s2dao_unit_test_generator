# s2dao ユニットテストコードジェネレーター 設計書

## 1. 目的

Seasar2 / S2Dao で実装されたアプリケーションの Oracle11g → PostgreSQL16 移行
(java5/tomcat5 → java8/tomcat9)にあたり、移行前後で DAO の挙動が一致することを
検証するための JUnit ユニットテストコードを **dao.java と .sql から自動生成**する
ツールを提供する。

生成されるテストコードは **旧環境(Java5 + Oracle11g)と新環境(Java8 + PostgreSQL16)
の両方で動作**しなければならない。

## 2. リポジトリ構成

```
/
├── README.md                 … 使い方・全体概要
├── docs/
│   ├── DESIGN.md             … 本書
│   ├── CONSTRAINTS.md        … 本開発環境での検証上の制約
│   └── VERIFICATION.md       … 検証結果レポート
├── generator/                … 【製品】ジェネレーター本体 (Maven, Java8 で動作)
│   ├── pom.xml
│   └── src/main/java/...
├── runtime/                  … 【生成後に必要なもの】生成テストの実行環境
│   ├── testsupport/          … テスト実行時ライブラリ (Java5 互換。jar を classpath へ)
│   └── infra/                … テスト用 DB 環境 (docker-compose。DB が無い人向け)
│       ├── old-db-oracle11g/ … Oracle XE 11g (+JA16SJIS 張り替えスクリプト)
│       └── new-db-postgres16/… PostgreSQL 16.8 (ja_JP.utf8)
├── vendor/                   … 【参照】ベンダリングした Seasar2/S2Dao ソース (Apache-2.0)
└── verification/             … 本リポジトリで行った検証作業一式
    ├── samples/              … インターネット上の実 S2Dao ソース(検証対象)
    ├── scripts/              … 生成コードのコンパイル検証 (javac 1.5 / ECJ)
    ├── env/                  … Seasar2 ビルド・スモーク・DB 起動スクリプト
    ├── lib/                  … Seasar2 ランタイム jar 一式
    ├── generated/            … 生成テスト(検証時点の成果物)
    ├── old-env/              … 旧環境相当での実行 (H2 Oracle互換 + -source 1.5)
    ├── new-env/              … 新環境での実行 (PostgreSQL16 + Java8)
    ├── oracle/               … 実 Oracle 用 DDL/dicon/properties
    ├── eclipse-project-templates/ … Eclipse(Pleiades) 用プロジェクト雛形
    └── compare/              … 結果・データセット突き合わせ
```

## 3. ジェネレーター本体 (generator/)

### 3.1 入力

- DAO の Java ソースが置かれたディレクトリ(再帰走査)
- 2-way SQL(.sql)が置かれたディレクトリ(S2Dao 規約: `<DaoName>_<methodName>.sql`、
  DB サフィックス `_oracle.sql` / `_postgre.sql` 対応)
- 設定ファイル(YAML/properties): 対象 DB 方言、出力先、パッケージ名など

### 3.2 フェーズ1: 解析(メタ情報抽出)

#### (a) DAO Java 解析
S2Dao の規約に従い、DAO インタフェース/クラスから以下を抽出する:

- クラス名、`BEAN` 定数アノテーション / `@S2Dao`・`@Bean`(Tiger アノテーション)
  が指すエンティティ(DTO)クラス
- メソッド一覧: 名前・引数(名前/型)・戻り値型
- メソッド単位の定数アノテーション:
  `<method>_ARGS`, `<method>_QUERY`, `<method>_SQL`,
  `<method>_NO_PERSISTENT_PROPS`, `<method>_PERSISTENT_PROPS` など
- Tiger アノテーション版: `@Arguments`, `@Query`, `@Sql`,
  `@NoPersistentProperty` など(s2dao-tiger)
- エンティティ(DTO)解析: `TABLE` 定数 / `@Bean(table=...)`、プロパティ一覧、
  `<prop>_COLUMN` / `@Column`、`ID` / `@Id`(identity/sequence/assigned)、
  `VERSION_NO_PROPERTY`, `TIMESTAMP_PROPERTY`、`<prop>_RELNO`(リレーション)

実装: **JavaParser(com.github.javaparser)によるソース解析**を第一とし、
S2Dao の「規約(コンベンション)解釈ロジック」は S2Dao 本体
(`DaoMetaDataImpl`/`FieldAnnotationReader` 等)の仕様に忠実に合わせる。
コンパイル済みクラスが得られる場合はリフレクション+S2Dao 本体での
クロスチェックを可能とする(検証フェーズで使用)。

#### (b) メソッド→SQL の解決
S2Dao 本体 `DaoMetaDataImpl` と同一の解決順:

1. `<method>_SQL` 定数 / `@Sql` があればその SQL
2. `<DaoClassName>_<methodName>_<dbmsSuffix>.sql` → `<DaoClassName>_<methodName>.sql`
3. .sql が無ければ **S2Dao 自動生成 SQL** をメソッド名プレフィクス
   (insert/create/add, update/modify/store/edit, delete/remove, select/find/get 等)
   とエンティティメタ情報から S2Dao と同じロジックで組み立てる
   (`INSERT INTO t (...) VALUES (...)`, `UPDATE t SET ... WHERE id = ? [AND version = ?]`,
   `DELETE FROM t WHERE id = ?`, `SELECT cols FROM t` + QUERY 等)

#### (c) 2-way SQL 解析
**S2Container(s2-extension)の `SqlParserImpl` と同一のパーサ**を使用する。
jar が入手できない場合は Apache-2.0 ライセンスに基づき該当パッケージ
(`org.seasar.extension.sql.*`)のソースを generator にベンダリングする。
これにより `/*IF*/`, `/*BEGIN*/`, `/*bindVar*/`, `/*$embedded*/`, ELSE コメント等の
解釈が S2Dao 実物と完全一致する。

抽出メタ情報:
- バインド変数ノード一覧(パラメータ名・DTO プロパティパス)
- IF/BEGIN 条件式(OGNL)一覧
- DB エンジンに渡される SQL(代表パラメータでの展開結果)

#### (d) SQL 構造解析
展開後 SQL から以下を抽出:
- 文種別(SELECT/INSERT/UPDATE/DELETE)
- 参照/更新テーブル一覧(FROM/JOIN/INSERT INTO/UPDATE/DELETE FROM)
- 取得カラム一覧(SELECT 句; `*` はエンティティメタ情報で展開)
- WHERE 句で参照されるカラムとバインドパラメータの対応

実装は JSqlParser 等既製ライブラリに頼らず、S2Dao が生成する定型 CRUD と
業務 SQL の範囲に絞った自前トークナイザで行う(Oracle 方言で確実に動くこと)。

#### フェーズ1出力
dao 1 つにつき 1 つのメタ情報 JSON(`<Dao>.meta.json`)。フェーズ2の入力。
中間成果物として人がレビューできる形式とする。

### 3.3 フェーズ2: テストコード生成

- dao 単位に 1 テストクラス `<DaoName>Test.java` を生成
- メソッド単位に 1 テストメソッド `testXxx()` を生成
- **JUnit 3.8 形式**(`junit.framework.TestCase` 継承)
  → JUnit3 ランナーは Java5/Java8 双方で動作し、JUnit4 環境でも実行可能
- **Java5 互換構文のみ使用**(ダイヤモンド演算子・try-with-resources・
  マルチキャッチ・インタフェース実装への @Override 禁止)
- DAO の実行は S2Container を起動して DAO を取得
  (`S2ContainerFactory.create(dicon)` + `container.getComponent(daoIf)`)

テストメソッドの流れ(生成コードの骨格):

```
1. TestDataParam 構築(テーブル名、カラム名配列、値配列)
   … SQL 構造解析メタから対象テーブル・カラムに対する適当な行データを決定
2. WriteDbUtil.write(conn, param) でデータ投入
   … update/delete 系は事前レコード投入、update 系は投入値と異なる更新用 DTO も用意
3. 引数準備(スカラ引数 or クエリパラメータ DTO)… メタ情報から型に応じた値を生成
4. dao メソッド呼び出し
5. 戻り値 assert(件数・非null・更新件数など)
6. GetDatasetUtil.getDataset(conn, table, ...) で操作後レコード取得
7. レコード数・内容 assert
8. EvidenceWriter で戻り値/データセットを CSV +コンソールに出力
```

### 3.4 testsupport ライブラリ

生成コードから使う小さなランタイム(Java5 互換、JUnit3 のみ依存):

- `TestDataParam` … テーブル名・カラム名配列・値配列
- `WriteDbUtil` … INSERT 実行(JDBC 直)
- `GetDatasetUtil` … SELECT 実行し `List<Map<String,Object>>` 相当で返す
  (Java5 互換のため素の型 or ジェネリクスは Java5 範囲で使用)
- `EvidenceWriter` … 戻り値・データセットの CSV/コンソール出力
- `ValueFactory` … カラム型に応じたテストデータ値の生成(決定的・シード固定)
- DB 方言差の吸収(型マッピング、シーケンス/identity の扱い)

### 3.5 テストデータ生成の決定性

新旧環境で結果を突き合わせるため、テストデータ・引数値は**乱数を使わず
メタ情報から決定的に生成**する(同じ dao/sql からは常に同じテストと同じデータ)。
日付・時刻も固定値を使用する。

## 4. 検証計画

### 4.1 検証対象の実ソース
インターネット上で公開されている実 S2Dao ソースを verification/samples/ に取得する。
候補(リサーチで確定):
- seasarorg 系ミラー(s2dao 本体の examples / テスト用 dao)
- s2dao-tutorial / sa-struts examples 系
- その他 GitHub 上の実プロジェクト

### 4.2 本環境の制約(docs/CONSTRAINTS.md に詳述)
- Oracle11g 実物・Java5 JVM 実物・Docker イメージ取得は本環境では不可
- 代替: 旧環境相当 = **H2 Database の Oracle 互換モード** + JDK8 の
  `-source/-target 1.5`(Java5 世代の厳格コンパイル)
- 新環境 = **PostgreSQL 16.13(apt 版)** + Java8(OpenJDK 1.8.0_492)
- 生成コード自体は実 Oracle11g/実 Java5 で動作する設計とする
  (ドライバ・dicon の差し替えのみで移行可能)

### 4.3 一致検証
同一 dao/sql から生成した同一テストを両環境で実行し、
- JUnit 結果(成功/失敗/実行数)
- エビデンス CSV(戻り値・データセット)
を正規化した上で diff し、一致レポートを verification/compare/ に出力する。

## 5. サブエージェント分担

| 作業 | 担当 |
|---|---|
| 実ソース調達・依存 jar 調査 | sonnet |
| フェーズ1 解析エンジン | opus |
| フェーズ2 ジェネレーター+testsupport | opus |
| 検証環境構築・実行検証 | opus |
| 新旧一致検証・レポート | sonnet/opus |
| 検品・レビュー・統合 | メイン(監督) |
