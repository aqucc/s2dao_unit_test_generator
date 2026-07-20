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
    ├── README.md            … verification/ の歩き方(各フォルダの役割と検証の流れ)
    ├── samples/              … 実プロジェクトを模した検証対象(入力: s2dao / tiger / servicebase)
    ├── generated/            … samples からの生成結果サンプル(出力)
    ├── lib/                  … Seasar2 ランタイム jar 一式
    ├── setup/                … 初回環境構築 (Seasar2 ビルド・スモーク・DB 起動スクリプト)
    ├── run/                  … 両環境の実行一式 (run-*.sh, runner/, dicon/, ddl/, config/, compare/)
    ├── results/              … 実行結果 (old-env=H2 Oracle互換 + -source 1.5, new-env=PostgreSQL16 + Java8)
    ├── checks/               … 生成コードのコンパイル検証 (javac 1.5 / ECJ)
    ├── oracle/               … 実 Oracle 用 DDL/dicon/properties
    └── eclipse-project-templates/ … Eclipse(Pleiades) 用プロジェクト雛形
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
3. **メソッド本体の bySql 系呼び出しによる明示指定**(S2JDBC の
   `selectBySqlFile` / `updateBySqlFile` / `deleteBySqlFile` /
   `getResultListBySqlFile` / `selectBySql` 等)。メソッド本体(`MethodDeclaration`
   の body)を JavaParser AST で走査し、**メソッド名に "BySql" を含む呼び出し**
   (呼び出し元が `this` でも `jdbcManager` でも可 = メソッド名のみで判定)の
   **文字列引数**を SQL 名候補として収集する。候補は
   (a)直接の文字列リテラル `"xxx"`(および `"a" + "b"` 連結)、
   (b)同一クラスの `static final String` 定数参照(`NameExpr` / `FieldAccessExpr`
   → `AstUtil.getStaticStringField`)。各候補 n について
   `--sql` フォルダ配下(= `SqlFileIndex` 索引済み)から順に解決する:
   ① n からディレクトリ部(最後の `/` 以降)を除去し末尾 `.sql` を除去 → n'、
   ② `<ClassSimpleName>_<n'>[<dbmsSuffix>].sql`、
   ③ n' が既に `<ClassSimpleName>_` で始まる(フルベース名指定)場合は
   `<n'>[<dbmsSuffix>].sql`。最初に解決したファイルを採用し、残りは notes に記録。
   解決すると `resolutionType=SQL_FILE` として 2-way 解析へ回す(CRUD 種別は
   ステップ1の `refineKindFromSql` が SQL 先頭トークンで決める)。
   インライン SQL(候補文字列が SELECT/INSERT/UPDATE/DELETE で始まる形)は
   スコープ外として候補から除外する。
3.5. **ゆらぎ吸収フォールバック(大文字小文字ゆらぎの逆引き)**。(2)(3) のいずれでも
   .sql が見つからないとき、`SqlFileIndex` の**逆引き台帳**で大文字小文字を無視した
   緩い照合を行う。台帳は走査時に各ベース名を**最初の `_`** で「クラス名部」「名前部」に
   割って構築する(`クラス名部(ci)` → { `名前部(ci)` → File }。`_` 無しのベース名は
   台帳対象外。衝突は先勝ち)。`findRelaxed(クラス単純名, 名前, dbmsSuffix)` の照合順は
   ① 名前部 == `名前 + dbmsSuffix`(ci)→ ② 名前部 == `名前`(ci)。方言サフィックスの
   意味論は完全一致解決と同じで、サフィックス付き候補は**現方言のサフィックスのみ**を
   試す(例: dbms=oracle で `getCount_hsql` が `getCount` にマッチすることはない)。
   まず `findRelaxed(クラス単純名, メソッド名, dbmsSuffix)`、次に (3) で収集した bySql 候補
   n' それぞれで `findRelaxed`(n' が `クラス名_` 始まりのフルベース名なら分割してから)。
   解決すると `resolutionType=SQL_FILE` として (3) と同様に 2-way 解析+`refineKindFromSql`
   を適用し、`notes` に「大文字小文字ゆらぎを吸収して解決: <採用ファイル>(規約名 '<期待名>')」
   を記録する。(2)(3)(3.5) いずれで解決しても採用ファイルを `markClaimed` する。
4. .sql が無ければ **S2Dao 自動生成 SQL** をメソッド名プレフィクス
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

あわせて、`--sql` 配下でどの解決経路(完全一致・bySql・ゆらぎ吸収)にも採用されなかった
**未対応 .sql ファイル**を、クラス名部でグループ化して stdout と `<outDir>/unmatched-sql.txt`
に一覧する(取りこぼしの可視化)。区分は (a) クラス名部が解析済み DAO/Service に一致(ci)
するのにどのメソッドにも対応しなかった「名前ズレの疑い」、(b) クラス名部がどの解析対象にも
一致しない「クラス未検出(対象外クラス or 命名不一致)」、(c) `_` を含まずクラス名部が
取れない「規約外のファイル名」の 3 つ。0 件なら「未対応なし」の 1 行を出力する。

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

### 3.6 S2JDBC Service 層対応(ステップ3)

S2Dao(DAO インタフェース + BEAN 定数)に加え、S2JDBC 世代の **Service(具象クラス)**
にも対応する。Service は共通の抽象基底クラス(JdbcManager 委譲)を継承し、
`jdbcManager.selectBySqlFile(...).getResultList()` 等で SQL ファイルを実行する。

- **対象判定(`DaoAnalyzer.isDao`)**: 末尾 `Dao`/`Service`、`@S2Dao`、`BEAN` 定数の
  いずれかをトリガとする。具象クラスも許可するが、誤検出防止のため
  「抽象クラス」「エンティティ(`@Entity`/`@Bean`/`TABLE`)」は除外する
  (基底 `Abstract*Service` やエンティティ自身を拾わない)。
- **CRUD 種別**: メソッド名からは判定できないため、対応する 2-way SQL の先頭トークン
  (SELECT/INSERT/UPDATE/DELETE)で決める(ステップ1の `refineKindFromSql`)。
- **SQL 名を引数で明示指定する呼び方への対応**: Service が基底の
  `findByParams`/`updateByParams`(規約 `<ClassName>_<methodName>.sql`)を経由せず、
  `selectBySqlFile(clazz, "specialQuery", map)` のように **bySql 系 API へ SQL 名/パスを
  直接渡す**ケースがある。この場合はメソッド本体を走査して候補名を集め、
  `<ClassSimpleName>_<明示名>.sql` を `--sql` フォルダ配下から解決する
  (§3.2(b) の解決順(3)。素の名称・`.sql` 付き・`static final String` 定数渡し・
  ディレクトリ付きフルベース名のいずれも対応)。解決できなければ従来どおり
  自動生成へフォールバックする(該当ファイルが無い明示名はスキップ)。
- **エンティティ辞書の完全化**: Service は BEAN を持たず、参照エンティティは戻り値
  ジェネリクス(`List<Emp>`)や別フォルダにある。そこで解析フェーズで
  `EntityAnalyzer.isEntityLike` な全型を **`<Entity>.entity.json`** として出力し、
  生成フェーズでテーブル逆引き辞書(テーブル名→カラム/PK)へ補完登録する
  (DAO 由来の `<Dao>.meta.json` を優先、`entity.json` は未登録キーのみ補う)。
- **テスト生成**:
  - find/get 系(SELECT・`List<Entity>`): 戻り値ジェネリクスから結果エンティティを
    特定し、対象テーブルへ決定的データを投入 → 実行 → 件数・先頭行 PK を assert。
    引数が `Map`(基底 `findByParams(Class, co, Map)` 委譲型)の場合も update 系と
    同様に SQL の bindVariables からキーを取り、投入行にヒットする値(WHERE 束縛は
    その行の値)を詰めた `Map` を渡す。
  - update 系(INSERT/UPDATE/DELETE・戻り void/int・`Map` 引数): SQL の bindVariables
    からキー名を取り、決定的値を詰めた `java.util.Map`(Java5 互換の `new HashMap`+`put`)を
    渡す。対象テーブルを逆引きできれば PK/全カラムで投入・追跡し、UPDATE は SET 列の
    変化を、INSERT/DELETE は行の存在/不在を assert する。逆引き不能なら「呼ぶだけ+
    データセット出力」に安全フォールバック。
- 生成物・辞書登録とも **既存 S2Dao 経路には一切影響しない**(Service 経路は独立)。

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
を正規化した上で diff し、一致レポートを verification/run/compare/ に出力する。

## 5. サブエージェント分担

| 作業 | 担当 |
|---|---|
| 実ソース調達・依存 jar 調査 | sonnet |
| フェーズ1 解析エンジン | opus |
| フェーズ2 ジェネレーター+testsupport | opus |
| 検証環境構築・実行検証 | opus |
| 新旧一致検証・レポート | sonnet/opus |
| 検品・レビュー・統合 | メイン(監督) |
