# S2Dao 実ソース調達・依存関係調査レポート

調査日: 2026-07-03
担当: リサーチ(調達)エージェント

本レポートは `docs/DESIGN.md` の要求に基づき、(1) 実際に S2Dao 規約で書かれた Java
ソース(verification/samples/)の調達、(2) Seasar2/S2Dao 依存 jar の入手経路確定、(3) 2-way SQL
パーサ・S2Dao 本体コアソースのベンダリング(vendor/)を行った結果をまとめる。

## 0. 環境上の制約(重要)

- `curl` での `https://github.com/...`、`https://api.github.com/...`、
  `https://codeload.github.com/...`、`https://search.maven.org/...`、
  `https://cdn.jsdelivr.net/...` への直接アクセスはプロキシのアウトバウンドポリシーで
  **403 (CONNECT tunnel failed)** となり使用不可。GitHub API によるリポジトリ検索・
  コード検索は今回の環境では実行できなかった。
- 一方で **`git clone` / `git ls-remote` (git スマート HTTP プロトコル) 経由の
  `https://github.com/<owner>/<repo>.git` へのアクセスは許可されている**。存在しない
  リポジトリに対しては認証プロンプトを要求され `terminal prompts disabled` で失敗する
  ため、これを「404 相当」として利用しリポジトリの存在確認を行った。
- `https://repo1.maven.org/maven2/...` への `curl` は正常にアクセス可能(200/404 で
  実在確認ができる)。`mvn dependency:get` も同様に動作し、実際に `~/.m2/repository` へ
  ダウンロードして検証した。
- 上記制約により、GitHub 上の該当リポジトリの**発見は「既知のリポジトリ名を
  `git ls-remote` で総当たり確認する」方式**で行った。GitHub コード検索が使えないため、
  「サードパーティの実アプリで S2Dao を使っているリポジトリ」の網羅的な発見はできて
  いない。今回確保できたのは **Seasar2 プロジェクト公式(`seasarorg` Organization)の
  S2Dao 本体付属サンプル・チュートリアル**であり、これは DESIGN.md がそもそも
  「有力候補」として挙げているものと合致する。

## 1. 取得した verification/samples/ 一覧と評価

### 1.1 `verification/samples/s2dao/` (推薦・本命)

- 取得元: `https://github.com/seasarorg/s2dao.git` (Apache-2.0, `master` ブランチ、
  タグなし。pom.xml のバージョンは `1.0.52-SNAPSHOT`。changelog_ja.txt の最新確定版は
  `1.0.50`。DESIGN.md が挙げた `1.0.51` 相当の開発版とみてよい)
- Maven モジュール構成:
  - `s2-dao` … S2Dao 本体コア(`org.seasar.dao.*`、Java ファイル213本)
  - `s2-dao-examples` … **定数アノテーション方式**の実 dao/entity/sql サンプル
    (`examples.dao` パッケージ)
  - `s2-dao-s24-test` … Java 1.4 相当の互換テスト(publicフィールド版アノテーション
    リーダーのテストなど)
- `s2-dao-examples` の内容:
  - dao インタフェース 7本: `EmployeeDao`, `Employee2Dao`, `EmployeeAutoDao`,
    `DepartmentDao`, `NoPkTableDao`, `StoredProcedureTestDao`, `TruncateTableDao`
  - entity(DTO) 2本: `Employee`(`TABLE="EMP"`, `department_RELNO`(N:1リレーション)、
    `timestamp_COLUMN` 等を含む)、`Department`(`TABLE="DEPT"`, `versionNo` で
    楽観ロック)
  - `.sql` 6本(`EmployeeDao_getAllEmployees.sql` 等)。うち
    `EmployeeDao_getEmployeeByJobDeptno.sql` は
    `/*BEGIN*/ /*IF*/ /*bindVar*/ /*END*/` を含む正統な2-way SQL:
    ```sql
    SELECT * FROM emp
    /*BEGIN*/WHERE
      /*IF job != null*/job = /*job*/'CLERK'/*END*/
      /*IF deptno != null*/AND deptno = /*deptno*/20/*END*/
    /*END*/
    ```
  - `.dicon` 8本(DI コンテナ定義、S2Container 起動確認に使える)
  - `EmployeeDao.java` は `BEAN`, `<method>_ARGS`, `<method>_QUERY`, `<method>_SQL`
    を**すべて**含む理想的なリファレンス実装(DESIGN.md 3.2(a)(b) の全パターンを
    1ファイルで検証できる)
  - DDL: `hsql/sql/demo-oracle.sql`(Oracle方言、`TO_DATE` 使用、EMP/DEPT の
    CREATE TABLE + INSERT データ、classic scott/emp スキーマ)、
    `hsql/sql/demo-hsqldb.sql`、`derby/sql/derby-ddl.sql`、
    `hsql/sql/storedprocedure-demo-oracle.sql` / `-postgres.sql`
    (Oracle→PostgreSQL移行検証に直接使えるストアドプロシージャ対比サンプルもあり)
- 評価: **dao・entity・.sql・DDL(Oracle方言)がすべて揃っており、DESIGN.md の
  検証シナリオ(Oracle→PostgreSQL移行)に理想的に合致する。本命として推薦する。**
  さらに `s2-dao` コア本体も同梱されているため、フェーズ1解析結果と
  `DaoMetaDataImpl` 実物の突き合わせ(クロスチェック)も同一リポジトリ内で可能。

### 1.2 `verification/samples/s2dao-tiger/` (推薦・補完)

- 取得元: `https://github.com/seasarorg/s2dao-tiger.git` (Apache-2.0, タグなし)
- Maven モジュール構成:
  - `s2-dao-tiger` … Tiger アノテーション方式の S2Dao 拡張コア
    (`org.seasar.dao.annotation.tiger.*`, `org.seasar.dao.tiger.*`)
  - `s2-dao-tiger-examples` … Tiger アノテーション版の実 dao/entity/sql サンプル
  - `s2-dao-tiger-s24-test`
- `s2-dao-tiger-examples` の内容:
  - dao インタフェース 5本(`EmployeeDao`, `Employee2Dao`, `EmployeeAutoDao`,
    `DepartmentDao`, `StoredProcedureTestDao`)。`s2dao/` 版とほぼ同一の
    emp/dept ドメインを **`@S2Dao(bean=...)`, `@Arguments`, `@Sql`,
    `FetchHandler<T>`** 等 s2dao-tiger アノテーションで書き直したもの:
    ```java
    @S2Dao(bean = Employee.class)
    public interface EmployeeDao {
        @Arguments("empno")
        public Employee getEmployee(int empno);
        @Sql("SELECT count(*) FROM emp")
        public int getCount();
        ...
    }
    ```
  - `.sql` 5本、DDL は `hsql/sql/demo-oracle.sql`(s2dao 版と同様の emp/dept)
- 評価: **定数アノテーション版と1:1対応する Tiger アノテーション版**であり、
  DESIGN.md 3.2(a) が要求する「Tiger アノテーション版」の解析ロジックを、
  s2dao 版と同一ドメイン・同一 SQL で検証できる。2本目のサンプルとして推薦する。

### 1.3 その他確認したが不採用のリポジトリ

- `seasarorg/sa-struts` … 存在は確認できたが dao / .sql を含まず(Struts 統合層のみ)。
  取得しなかった。
- サードパーティ(seasarorg 以外)の実アプリ探索は、前述の GitHub 検索制約により
  総当たりのリポジトリ名推測でしか行えず、有効なヒットを得られなかった。
  ネットワーク制約が緩和され GitHub Code Search / API が使えるようになれば、
  `"S2Dao" "_ARGS"` 等での検索や `seasar-user` ML 界隈のプロジェクト
  (DBFlute系、SAStruts系実アプリ)を追加調査することを推奨する。

### 1.4 verification/samples/ 配置サマリ

| ディレクトリ | dao数 | .sql数 | .dicon数 | アノテーション方式 | DDL |
|---|---|---|---|---|---|
| `verification/samples/s2dao/s2-dao-examples` | 7 | 6 | 8 | 定数アノテーション(`BEAN`/`_ARGS`/`_QUERY`/`_SQL`) | あり(Oracle/HSQLDB/Derby) |
| `verification/samples/s2dao-tiger/s2-dao-tiger-examples` | 5 | 5 | 同梱dicon複数 | s2dao-tiger(`@S2Dao`/`@Arguments`/`@Sql`) | あり(Oracle/HSQLDB) |

各リポジトリは `.git` を削除した上でリポジトリ全体(コア本体・examples・s24-test・
lib(jarバイナリ含む)・hsql/derbyスクリプト一式)をそのまま配置している
(`verification/samples/s2dao` 16MB, `verification/samples/s2dao-tiger` 1.6MB)。

## 2. 依存 jar の入手可否

### 2.1 Maven Central に**存在しない**もの(すべて 404 確認済み)

`org.seasar.*` グループ ID の成果物は Maven Central に**一切存在しない**
(グループ自体のディレクトリも 404)。`maven.seasar.org` が旧来の唯一の配布先であり、
現在停止しているとの前提が裏付けられた。

| groupId:artifactId | 確認したバージョン | Central | 備考 |
|---|---|---|---|
| org.seasar.dao:s2-dao | 1.0.47 / 1.0.51 / 1.0.52 | ✗ 404 | GitHub `seasarorg/s2dao` からソース取得・ローカルビルドが必要 |
| org.seasar.container:s2-extension | 2.3.23 / 2.4.46 / 2.4.48 | ✗ 404 | GitHub `seasarorg/seasar2` (`seasar2/s2-extension`) から |
| org.seasar.container:s2-framework | 2.4.46 / 2.4.48 | ✗ 404 | 同上 (`seasar2/s2-framework`) |
| org.seasar.container:s2-tiger | 2.4.46 / 2.4.48 | ✗ 404 | 同リポジトリ `s2-tiger` モジュール |

GitHub 上の実在確認(git ls-remote で解決):
- `https://github.com/seasarorg/s2dao.git`(S2Dao本体+examples。pom.xml上のバージョンは
  `org.seasar.dao:s2-dao-project:1.0.52-SNAPSHOT`。s2-extension依存は
  `org.seasar.container:s2-extension:2.3.23`と明記)
- `https://github.com/seasarorg/s2dao-tiger.git`(Tigerアノテーション版)
- `https://github.com/seasarorg/seasar2.git`(S2Container本体。モジュール:
  `seasar2/s2-framework`, `seasar2/s2-extension`, `s2-tiger`, `s2jdbc-gen` 等。
  **タグ確認済み**: `Seasar2.4.44`〜`Seasar2.4.48` が存在し、`Seasar2.4.48` が
  リポジトリ上で確認できる最終リリースタグ。DESIGN.md の想定バージョン
  (2.4.46 / 2.4.48)と一致する)

→ **方針: これら4つは Maven Central 上のjar取得を諦め、GitHub ソースからの
ローカルビルド(またはソース直接ベンダリング/コンパイル)で対応する。**
`seasar2` リポジトリの `pom.xml` (`org.seasar.container:s2-container-project`) は
Maven プロジェクトとして構成されているため、`mvn install` によるローカル `.m2` への
インストールが理論上可能(依存する `aopalliance`, `jboss:*` 等の外部依存が Central に
あるかは個別に要確認。本調査では実ビルドまでは行っていない)。

### 2.2 Maven Central に**存在する**もの(`mvn dependency:get` で実ダウンロード検証済み、`~/.m2/repository` に格納確認済み)

| groupId:artifactId:version | Central | ~/.m2 格納確認 |
|---|---|---|
| junit:junit:3.8.2 | ✓ 200 | ✓ `junit-3.8.2.jar` |
| junit:junit:4.13.2 | ✓ 200 | ✓ `junit-4.13.2.jar` |
| com.h2database:h2:1.4.199 | ✓ 200 | ✓ `h2-1.4.199.jar` |
| org.postgresql:postgresql:42.2.27 | ✓ 200 | ✓ `postgresql-42.2.27.jar` |
| ognl:ognl:2.6.9 | ✓ 200 | ✓ `ognl-2.6.9.jar`(2.6.7 も 200。2.6.11 は 404 = 存在しない) |
| commons-logging:commons-logging:1.1.1 | ✓ 200 | ✓ `commons-logging-1.1.1.jar`(1.1〜1.1.3 すべて 200) |
| org.javassist:javassist:3.18.1-GA | ✓ 200 | ✓ `javassist-3.18.1-GA.jar`(※下記注記) |
| org.apache.geronimo.specs:geronimo-jta_1.0.1B_spec:1.0 | ✓ 200 | ✓ |
| org.apache.geronimo.specs:geronimo-j2ee_1.4_spec:1.0 | ✓ 200 | ✓ |

注記: `javassist:3.18.1-GA` は jar 自体のダウンロードは成功したが、`mvn dependency:get`
がこの jar の pom に記載された `com.sun:tools:jar` (システムスコープ、JDK5/6 の
`tools.jar` を指す) を依存解決しようとして失敗する。これは **JDK21 環境固有の
副作用**(JDK21 に `tools.jar` が存在しないため)であり、Central 上の javassist
jar 自体の入手可否とは無関係。generator/testsupport のビルドで javassist が
必要な場合は `<exclusions>` で `com.sun:tools` を除外すればよい。

### 2.3 まとめ表

| カテゴリ | 入手経路 |
|---|---|
| S2Dao/S2Container本体4種(s2-dao, s2-extension, s2-framework, s2-tiger) | Maven Central 不可。GitHub (`seasarorg/s2dao`, `seasarorg/seasar2`) からソース取得しローカルビルド、またはソースを直接ベンダリング |
| JUnit 3.8.2 / 4.13.2 | Maven Central で入手可(確認済み) |
| OGNL 2.6.x(2.6.9 まで) | Maven Central で入手可(確認済み) |
| commons-logging 1.1.x | Maven Central で入手可(確認済み) |
| javassist | Maven Central で入手可(確認済み。JDK21でのdependency:get実行時はtools.jar除外が必要) |
| geronimo-jta_1.0.1B_spec / geronimo-j2ee_1.4_spec | Maven Central で入手可(確認済み) |
| H2 1.4.199 | Maven Central で入手可(確認済み) |
| PostgreSQL JDBC 42.2.27 | Maven Central で入手可(確認済み) |

## 3. 2-way SQL パーサ・S2Dao コアソースの所在

### 3.1 `org.seasar.extension.sql`(2-way SQLパーサ本体)

- リポジトリ: `https://github.com/seasarorg/seasar2.git`
- パス: `seasar2/s2-extension/src/main/java/org/seasar/extension/sql/`
  (テストは `seasar2/s2-extension/src/test/java/org/seasar/extension/sql/`)
- 含まれる主要クラス:
  - `parser/SqlParserImpl.java`, `parser/SqlTokenizerImpl.java`
  - `node/{AbstractNode, BeginNode, IfNode, ElseNode, BindVariableNode,
    ParenBindVariableNode, EmbeddedValueNode, PrefixSqlNode, SqlNode,
    ContainerNode, AddWhereIfNode}.java`
  - `context/{SqlContextImpl, SqlContextPropertyAccessor}.java`
  - `{SqlParser, SqlTokenizer, Node, SqlContext, SqlArgWrapper}.java`(interface/基底)
  - 例外群(`IfConditionNotFoundRuntimeException` 等)
- ライセンス: Apache-2.0。リポジトリルートの `seasar2/LICENSE.txt` に全文あり。
  各ソースファイル冒頭にも `Copyright 2004-20xx the Seasar Foundation and the
  Others. / Licensed under the Apache License, Version 2.0` ヘッダあり。
- **ベンダリング先: `vendor/s2-extension-sql/`**
  (`src/main/java/org/seasar/extension/sql/...`、`src/test/java/...`、
  `LICENSE.txt` を配置済み。サイズ約292KB)
- バージョン: `seasar2/s2-extension/pom.xml` は `org.seasar.container:s2-extension`,
  親 `s2-container-project` は `2.4.49-SNAPSHOT`。タグ確認では `Seasar2.4.48` が
  最終リリース相当。DESIGN.md が挙げた 2.4.46/2.4.48 に整合する。

### 3.2 S2Dao 本体 `DaoMetaDataImpl` / アノテーションリーダー

- リポジトリ: `https://github.com/seasarorg/s2dao.git`
- パス: `s2-dao/src/main/java/org/seasar/dao/`
- 含まれる主要クラス:
  - `impl/DaoMetaDataImpl.java` … dao メタ情報構築の中核(SQL解決順、CRUD自動生成)
  - `impl/FieldDaoAnnotationReader.java` … `_ARGS`/`_SQL`/`_QUERY`/
    `_NO_PERSISTENT_PROPS`/`_PERSISTENT_PROPS`/`_SQL_FILE` サフィックス定数を
    ソースコード上で確認(DESIGN.md 3.2(a)(b)の規約定義と完全一致を確認済み)
  - `impl/FieldBeanAnnotationReader.java`, `impl/FieldArgumentDtoAnnotationReader.java`
    … entity(DTO) 側の `TABLE`/`_COLUMN`/`_RELNO` 等の規約
  - `node/`, `parser/`, `dbms/`, `types/`, `id/`, `pager/`, `handler/`,
    `interceptors/`, `context/`, `unit/`, `util/` … 付随する実装一式
- ライセンス: Apache-2.0(`Apache_Software_License_2.0.txt`、各ファイルヘッダ同上)
- **ベンダリング先: `vendor/s2dao-core/main-org-seasar/dao/`**
  (`org.seasar.dao` パッケージ全体、213ファイル、約1.3MB。`LICENSE.txt` 同梱)

### 3.3 s2dao-tiger 版アノテーションリーダー

- リポジトリ: `https://github.com/seasarorg/s2dao-tiger.git`
- パス: `s2-dao-tiger/src/main/java/org/seasar/dao/annotation/tiger/impl/`
  (`DaoAnnotationReaderImpl.java`, `BeanAnnotationReaderImpl.java`,
  `ArgumentDtoAnnotationReaderImpl.java`, `AnnotationReaderFactoryImpl.java`)
  および `org/seasar/dao/tiger/` (`FetchHandler` 等)
- **ベンダリング先: `vendor/s2dao-tiger-annotations/main-org-seasar/dao/`**
  (約200KB。LICENSE.txt は s2dao と同一 Apache-2.0 のため複製配置)

### 3.4 ベンダリング推奨方針

- generator は `vendor/s2-extension-sql` と `vendor/s2dao-core` を
  そのまま(パッケージ名 `org.seasar.extension.sql` / `org.seasar.dao` を変更せず)
  ソースとして取り込み、S2Container 本体を jar として用意できない場合でも
  `SqlParserImpl` と `DaoMetaDataImpl`(+アノテーションリーダー)を実物同一ロジックで
  利用できるようにする。
- Tiger アノテーション対応が必要な場合は `vendor/s2dao-tiger-annotations` も追加で
  取り込む。
- いずれも Apache-2.0 のため、ソースヘッダのコピーライト表示を保持したまま
  vendoring すれば再配布・改変とも問題ない。NOTICE ファイルは両リポジトリとも
  未確認(見当たらなかった)。LICENSE本文のみ同梱。

## 4. 判明したリスク・注意点

1. **GitHub 検索が使えないため、サードパーティの実アプリ調達が不十分。**
   確保できたのは Seasar2 公式チュートリアル(emp/dept ドメイン)2種のみ。
   DESIGN.md 検証には十分な内容(dao/entity/.sql/DDL/2-way SQL すべて揃っている)
   だが、より多様な実運用パターン(複雑なJOIN、多数のカラム型、業務固有の
   バリデーション等)の網羅性は低い。ネットワーク制約が緩和された場合、
   GitHub Code Search で `"_ARGS ="` や `"public Class BEAN"` などの規約特有文字列
   検索を追加実施することを推奨する。
2. **`org.seasar.*` 系 jar は Maven Central に存在しないことが確定した。**
   generator/testsupport のビルド、および検証環境(verification/)での実行には、
   (a) `vendor/` にソースを取り込んでコンパイルする、または
   (b) `seasarorg/s2dao` と `seasarorg/seasar2` を実際に `mvn install` して
   ローカル `.m2` にインストールする、のいずれかが必須。後者は本調査では
   実ビルドまで検証していない(pom.xml の存在とリポジトリ実在は確認済みだが、
   `seasar2` リポジトリは157MBと大きく、`aopalliance`/`jboss:*` 等の追加依存が
   Central に揃っているかは未確認。ビルド時に個別調査が必要)。
3. **`verification/samples/s2dao` には S2Dao 本体ソース(`s2-dao` モジュール)がそのまま
   同梱されている**ため、これを `vendor/s2dao-core` と重複して保持している。
   意図的な重複であり(verification/samples/ = 検証対象の実プロジェクト一式、vendor/ =
   generator が実際にビルド時参照する切り出し済みソース)、generator 実装時は
   `vendor/` 側を正とすること。
4. **javassist の `mvn dependency:get` は JDK21 環境で失敗しうる**(前述、
   `com.sun:tools:jar` システムスコープ依存が原因)。jar 自体は取得できているため
   実害はないが、generator/testsupport の pom.xml で javassist を使う場合は
   `<exclusion>` 設定を入れておくことを推奨する。
5. **`seasarorg/s2dao` と `seasarorg/s2dao-tiger` のリリースタグは存在しない。**
   `1.0.52-SNAPSHOT` などスナップショット版がリポジトリの最終状態であり、
   「どのコミットが1.0.51として実際にリリースされたか」は本調査では特定できて
   いない(changelog_ja.txt に 1.0.50 までの変更履歴はあるが、それ以降の記述はない)。
   DaoMetaDataImpl の規約解釈ロジックとしては master 相当で問題ないと考えられる。
6. **DDL は Oracle 方言(`TO_DATE` 等)のみ**で PostgreSQL 用 DDL は含まれていない
   ため、`verification/` フェーズで PostgreSQL16 用に別途 DDL 変換が必要
   (CONSTRAINTS.md の方針と整合)。ストアドプロシージャのみ Oracle版/PostgreSQL版
   両方が `hsql/sql/storedprocedure-demo-{oracle,postgres}.sql` として存在する。

## 5. 成果物一覧

- `verification/samples/s2dao/`(旧 `seasarorg/s2dao`、.git削除済み、16MB)
- `verification/samples/s2dao-tiger/`(旧 `seasarorg/s2dao-tiger`、.git削除済み、1.6MB)
- `vendor/s2-extension-sql/`(`org.seasar.extension.sql` 2-way SQLパーサ一式 + LICENSE、292KB)
- `vendor/s2dao-core/`(`org.seasar.dao` S2Dao本体コア一式 + LICENSE、1.3MB)
- `vendor/s2dao-tiger-annotations/`(`org.seasar.dao.annotation.tiger` / `org.seasar.dao.tiger` 一式 + LICENSE、200KB)
- `~/.m2/repository` に Central 入手可能jar 9点をダウンロード済み(セッション環境限定。
  実開発環境では改めて `mvn dependency:get` または pom.xml 経由で取得すること)
