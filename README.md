# s2dao ユニットテストコードジェネレーター

Seasar2 / S2Dao で実装された DAO クラス(.java)と 2-way SQL(.sql)を解析し、
**旧環境(Java5 + Oracle11g)と新環境(Java8 + PostgreSQL16)の両方で動作する
JUnit ユニットテストコードを自動生成**するツールです。

Oracle → PostgreSQL 移行時に「同一 dao/sql に対して新旧環境で同一の結果・
データセットが得られること」をエビデンス CSV 付きで検証できます。

## 特長

- **S2Dao と同一のパーサ**: 2-way SQL(`/*IF*/` `/*BEGIN*/` `/*bindVar*/` 等)は
  Seasar2 本体の `SqlParserImpl` をベンダリングして解析(挙動完全一致)。
  DAO の規約解釈(BEAN / _ARGS / _QUERY / _SQL / Tiger アノテーション)も
  S2Dao 本体 `DaoMetaDataImpl` の仕様に忠実
- **自動生成 CRUD も対象**: .sql が無いメソッドは S2Dao と同じロジックで
  INSERT/UPDATE/DELETE/SELECT を組み立てて解析対象にする
- **生成テストは JUnit 3.8 形式 + Java5 互換構文のみ** → 旧環境でもそのまま動く
- **決定的テストデータ**(乱数・現在時刻不使用)+ **正規化エビデンス CSV**
  → 新旧環境の実行結果を機械的に diff 可能
- 生成テストのランタイム(testsupport)は **junit 3.8.2 のみに依存**
  (S2Container はリフレクションで起動するため Seasar への compile 依存なし)

## リポジトリ構成

| パス | 内容 |
|---|---|
| `generator/` | ジェネレーター本体(Maven)。`analyze` / `generate` / `gen-all` CLI |
| `testsupport/` | 生成テスト用ランタイム(Java5 互換、junit3 のみ依存) |
| `samples/` | 検証に使用した実 S2Dao ソース(seasarorg 由来) |
| `vendor/` | ベンダリングした Seasar2/S2Dao ソース(Apache-2.0) |
| `verification/` | 検証環境一式: Seasar2 ランタイム jar、dicon、DDL、実行スクリプト、エビデンス |
| `docs/DESIGN.md` | 設計書 |
| `docs/CONSTRAINTS.md` | 本開発環境での検証上の制約(Oracle/Java5 の代替方法) |
| `docs/VERIFICATION.md` | 新旧環境での実行・一致検証レポート |
| `docs/research/` | 調査レポート(サンプル調達・jar 入手・ランタイムビルド) |

## 使い方

### 1. ビルド

```bash
cd generator  && mvn package     # → target/s2dao-testgen.jar (実行可能 fat-jar)
cd testsupport && mvn package    # → target/s2dao-testgen-support.jar
```

### 2. テストコード生成

```bash
# 解析+生成を一括(dao単位の <Dao>.meta.json と <Dao>Test.java を出力)
java -jar generator/target/s2dao-testgen.jar gen-all \
  --src /path/to/dao/java/sources \
  --sql /path/to/sql/resources \
  --out ./generated-tests \
  --dbms oracle

# 解析のみ(メタ情報 JSON)/ 生成のみ も可能
java -jar generator/target/s2dao-testgen.jar analyze  --src ... --sql ... --out ./meta
java -jar generator/target/s2dao-testgen.jar generate --meta ./meta --out ./generated-tests
```

- `--src` / `--sql` は再帰走査。定数アノテーション方式・Tiger アノテーション方式の両対応
- 生成できないメソッド(ストアドプロシージャ等)は生成コード内に
  `// TODO: テスト未生成(スキップ)` と理由が出力される

### 3. 生成テストの実行

生成テストは実行時に以下を必要とします(詳細は `docs/research/RUNTIME_BUILD.md`):

1. **classpath**: 生成テスト+DAO/エンティティのクラス、`s2dao-testgen-support.jar`、
   junit 3.8.2、Seasar2 ランタイム一式(`verification/lib/` に同梱)、JDBC ドライバ、
   dicon と `.sql`(DAO と同一パッケージパスに配置必須)
2. **接続設定** `s2daotest.properties`(雛形: `testsupport/s2daotest.properties.example`):
   ```properties
   jdbc.driver=org.postgresql.Driver
   jdbc.url=jdbc:postgresql://127.0.0.1:5432/mydb
   jdbc.user=...
   jdbc.password=...
   dialect=postgre        # oracle | postgre | h2-oracle
   dicon=s2dao-pg.dicon
   ```
   実 Oracle にする場合は driver/url を `oracle.jdbc.OracleDriver` /
   `jdbc:oracle:thin:...` に差し替えるだけ(dicon も 4 行差)
3. **実行**: `java -Ds2daotest.config=... -Ds2daotest.evidence.dir=./evidence junit.textui.TestRunner <生成Testクラス>`

実行例は `verification/run-old-env.sh` / `run-new-env.sh` を参照してください。

### 4. 新旧環境の一致検証

両環境のエビデンス CSV(戻り値+操作後データセット)を比較:

```bash
bash verification/compare/compare.sh
```

S2Dao が自動更新するカラム(TSTAMP/VERSIONNO 等)は比較から除外されます。

## 検証結果(本リポジトリでの実績)

実 S2Dao ソース 2 プロジェクト(定数アノテーション方式 / Tiger 方式)から生成した
テストを両環境で実行:

| 項目 | 結果 |
|---|---|
| 旧環境相当(H2 Oracleモード + `-source 1.6`) | **41/41 テスト成功** |
| 新環境(PostgreSQL 16 + Java8) | **41/41 テスト成功** |
| エビデンス CSV 突き合わせ | **75/75 ファイル完全一致** |

詳細は `docs/VERIFICATION.md`、環境制約(実 Oracle11g/実 Java5 の代替)は
`docs/CONSTRAINTS.md` を参照。

## ライセンス

`vendor/` および `samples/` 配下の Seasar プロジェクト由来コードは
Apache License 2.0(各ディレクトリの LICENSE 参照)。
