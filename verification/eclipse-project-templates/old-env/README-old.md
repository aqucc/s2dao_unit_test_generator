# Eclipse プロジェクトテンプレート（旧環境：Java5 / Oracle11g / Pleiades Windows）

生成した JUnit テストを **Java5 世代の Eclipse（Pleiades All in One 日本語版・Windows）**
にインポートして、そのまま実行できるようにするためのテンプレートです。ソース文字コードは
**MS932**（Windows 日本語 Eclipse の既定）を前提にしています。

## 同梱ファイルと意味

| ファイル | 役割 |
|----------|------|
| `.project` | Java プロジェクト定義（`org.eclipse.jdt.core.javanature` + Java ビルダー）。プロジェクト名は `s2dao-generated-tests-old`。 |
| `.classpath` | ソースフォルダ `src`、**J2SE-1.5 実行環境コンテナ**、`lib/` 配下の jar を参照。出力は `bin`。 |
| `.settings/org.eclipse.core.resources.prefs` | プロジェクトの既定テキストエンコーディングを **MS932** に固定。`encoding/<project>=MS932`。 |
| `.settings/org.eclipse.jdt.core.prefs` | コンパイラ準拠レベルを **1.5**（source/target/compliance すべて 1.5）に固定。`assert`/`enum` を予約語として扱う。 |

`.classpath` の書式は Eclipse JDT の classpath スキーマに忠実です：

- `kind="src"` … ソースフォルダ
- `kind="con" path="org.eclipse.jdt.launching.JRE_CONTAINER/.../J2SE-1.5"` …
  実行環境コンテナ。**J2SE-1.5** が Java 5.0（Java1.5）の Execution Environment ID です。
- `kind="lib"` … 参照ライブラリ（`lib/` 相対）
- `kind="output"` … コンパイル出力先

## 使い方

1. 本ディレクトリ（`old-env/`）をワークスペースへコピーし、Eclipse で
   「ファイル > インポート > 既存プロジェクトをワークスペースへ」で取り込む。
2. `src/` に、生成した `<Dao>Test.java` と、テスト対象の entity/DAO ソースを
   **パッケージ構成のまま**配置する。生成は次のように **MS932 指定**で行う：
   ```
   java -jar s2dao-testgen.jar gen-all --src <daoソース> --sql <sqlディレクトリ> \
        --out src --encoding MS932 --dbms oracle
   ```
3. `lib/` に以下の jar を配置する（本リポジトリの `verification/lib/` からコピー可）：
   - `junit-3.8.2.jar`
   - `s2dao-testgen-support.jar`（`testsupport` を `mvn package` して得られる。Java5 世代 major49）
   - Seasar2 / S2Dao 一式（`s2-framework` / `s2-extension` / `s2-dao` / `s2-dao-tiger` /
     `ognl` / `javassist` / `commons-logging` / `geronimo-jta` /
     **`aopalliance-1.0`（S2AOP の MethodInterceptor 親。S2DaoInterceptor 実行に必須）** /
     **`geronimo-j2ee_1.4_spec-1.0`（javax.servlet 等。S2Container 初期化に必須）**）
   - **Oracle JDBC ドライバ**を `lib/ojdbc.jar` として配置（リネーム、または `.classpath` の
     ファイル名を実物に合わせて修正）。Oracle ドライバは再配布不可のため本リポジトリには
     含みません。**Java5 で実行する場合は `ojdbc14.jar`（10.2 系。11g にも接続可）か
     `ojdbc5.jar`（11g 系の Java5 用）**を使うこと。`ojdbc6.jar` は Java6+ 用
     クラスファイル（major50）のため Java5 の JVM ではロードできません。
4. 実行時設定：クラスパスのルート（`src` か `bin`）に
   - `s2daotest.properties`（`jdbc.url` / `jdbc.driver` / `jdbc.user` / `jdbc.password` /
     `dialect=oracle` / `dicon=...`）
   - `*.dicon`（Oracle 接続用 DataSource 定義）
   を置く。実 Oracle 用のひな形は `verification/oracle/`（`s2dao-oracle.dicon` /
   `s2daotest-oracle.properties.example` / `schema-oracle.sql`)を参照。
   詳細手順は `docs/ORACLE_MIGRATION_CHECKLIST.md`。
5. `src` 上で右クリック > 実行 > JUnit テスト。

## 文字コードについて（重要）

- Java5 世代の Pleiades/Eclipse（Windows）は、ソースファイルの既定エンコーディングが
  **MS932（Windows-31J）**であることが多く、UTF-8 の日本語コメント入り `.java` を
  取り込むと文字化けやコンパイルエラーの原因になります。
- そのため本テンプレートは `encoding/<project>=MS932` を設定し、生成も `--encoding MS932`
  で行う運用としています。生成コードの日本語コメントは **すべて MS932 で表現可能な文字**
  のみを使用していることを検証済みです（`verification/scripts/verify-eclipse-compile.sh` が
  ECJ `-encoding MS932 -1.5` でエラーゼロを確認）。
- 逆に、ワークスペース既定が UTF-8 のままなら、UTF-8 で生成し
  `encoding/<project>=UTF-8` に変更しても構いません（`new-env` テンプレート参照）。
