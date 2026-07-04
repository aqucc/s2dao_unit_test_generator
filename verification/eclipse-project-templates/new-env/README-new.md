# Eclipse プロジェクトテンプレート（新環境：Java8 / PostgreSQL16 / Pleiades）

生成した JUnit テストを **Java8 世代の Eclipse（Pleiades）** にインポートして、そのまま
実行できるようにするためのテンプレートです。ソース文字コードは **UTF-8** を前提にしています。

## 同梱ファイルと意味

| ファイル | 役割 |
|----------|------|
| `.project` | Java プロジェクト定義。プロジェクト名は `s2dao-generated-tests-new`。 |
| `.classpath` | ソースフォルダ `src`、**JavaSE-1.8 実行環境コンテナ**、`lib/` 配下の jar を参照。出力は `bin`。 |
| `.settings/org.eclipse.core.resources.prefs` | プロジェクトの既定テキストエンコーディングを **UTF-8** に固定。`encoding/<project>=UTF-8`。 |
| `.settings/org.eclipse.jdt.core.prefs` | コンパイラ準拠レベルを **1.8** に固定。 |

`.classpath` の書式は Eclipse JDT の classpath スキーマに忠実です（`src` / `con`（JRE_CONTAINER）
/ `lib` / `output`）。実行環境コンテナの ID は **JavaSE-1.8** です。

## 使い方

1. 本ディレクトリ（`new-env/`）をワークスペースへコピーし、「既存プロジェクトのインポート」で取り込む。
2. `src/` に、生成した `<Dao>Test.java` と entity/DAO ソースをパッケージ構成のまま配置する。
   生成は既定（UTF-8）で行う：
   ```
   java -jar s2dao-testgen.jar gen-all --src <daoソース> --sql <sqlディレクトリ> \
        --out src --dbms postgre
   ```
   （`--encoding` を省略すると UTF-8。明示するなら `--encoding UTF-8`。）
3. `lib/` に以下の jar を配置する（本リポジトリの `verification/lib/` からコピー可）：
   - `junit-3.8.2.jar`
   - `s2dao-testgen-support.jar`
   - Seasar2 / S2Dao 一式
   - **PostgreSQL JDBC ドライバ** `postgresql-42.2.27.jar`（Maven Central 入手可）
4. 実行時設定：`s2daotest.properties`（`dialect=postgre` / PostgreSQL の `jdbc.url` 等）と
   PostgreSQL 用 `*.dicon` をクラスパスルートに配置。
5. `src` 上で右クリック > 実行 > JUnit テスト。

## 旧環境との違い

- **JRE コンテナ**：`JavaSE-1.8`（旧環境は `J2SE-1.5`）。
- **エンコーディング**：`UTF-8`（旧環境は `MS932`）。生成時の `--encoding` と一致させること。
- **コンパイラ準拠**：1.8（旧環境は 1.5）。ただし生成コード自体は Java5 互換構文なので
  1.5〜1.8 のどのレベルでもコンパイル・実行できます（`testsupport` のバイトコードは major49）。
- **JDBC ドライバ**：PostgreSQL（旧環境は Oracle）。
