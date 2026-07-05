# 本開発環境における検証上の制約

本リポジトリの開発・検証はクラウド上のコンテナ環境で行った。
以下の制約により、一部の検証は「等価な代替手段」で実施している。
**生成されるテストコード自体は実環境(実Oracle11g/実Java5、実PostgreSQL16.8/実Java8)
での動作を前提に設計されている**。利用者の実環境ではドライバ jar と dicon の
接続定義を差し替えるだけで実行できる。

## 1. Oracle 11g 実物が使用不可

- 本環境は外部ネットワークがプロキシ制限されており、Docker Hub からの
  イメージ取得(gvenzl/oracle-xe:11 等)が 403 で拒否される。
- Oracle のドライバ ojdbc14/ojdbc6 も再配布制限のため Maven Central に存在しない。
- **代替**: H2 Database を `MODE=Oracle` (Oracle 互換モード)で使用し、
  旧環境側の DB として検証した。
- 影響: Oracle 固有機能(ROWNUM の細部、NUMBER 型精度の端数、Oracle 方言の
  関数など)の完全な互換検証はできない。標準的な CRUD SQL と 2-way SQL の
  バインド・結果セット検証は可能。

## 2. Java 5 JVM 実物が使用不可

- Ubuntu 24.04 に導入可能な最古の JDK は OpenJDK 8。
- **代替(Java5 世代を厳格に検証)**:
  - 生成コード・testsupport 本体は Java5 互換構文のみ使用(ジェネリクスは可、
    ダイヤモンド演算子・try-with-resources・マルチキャッチ・
    String switch・インタフェース実装メソッドへの @Override は不使用)
  - **`-source 1.5 -target 1.5` で厳格コンパイル**:JDK8 の javac は
    `-source/-target 1.5` を obsolete 警告付きで受理し、バイトコード
    **major version 49(= Java5 世代)**を生成する。testsupport の pom も
    `maven.compiler.source/target=1.5` に設定し、生成テストは
    `verification/scripts/verify-generated-compile.sh` が両サンプルとも 1.5 でエラーゼロを確認。
  - **API レベルの Java5 互換を機械検証(animal-sniffer)**:testsupport 本体クラス
    (`target/classes`)を `animal-sniffer-maven-plugin` +
    `org.codehaus.mojo.signature:java15:1.0` シグネチャで検査し、
    Java6+ の API(`String.isEmpty()` 等)を使用していないことを保証する
    (`mvn test` 実行時に自動チェック。違反ゼロ)。生成コードは junit3 +
    testsupport + dao/entity しか呼ばず、`Integer.valueOf` 等 Java5 API のみを
    出力する(下記 VERIFICATION_JAVA5_ECLIPSE.md にパターン網羅レビューあり)。
  - **ECJ(Eclipse JDT バッチコンパイラ)での検証**:Eclipse/Pleiades が実際に
    使うコンパイラ ECJ(`org.eclipse.jdt.core.compiler:ecj:4.6.1`)で
    `-1.5` コンパイルし、エラーゼロ・警告一覧を確認(`verification/scripts/verify-eclipse-compile.sh`)。
  - JUnit は 3.8.x 形式(`junit.framework.TestCase`)で生成し、
    Java5 時代のランナーでも動作する形とする
- 影響: 実 Java5 VM 上での実行確認のみ利用者環境での実施が必要
  (構文・API・バイトコード世代・Eclipse 実コンパイラの各観点は本環境で検証済み)。

## 2b. Pleiades(日本語 Eclipse・Windows)の文字コード

- Java5 世代の Pleiades/Eclipse(Windows)はソース既定エンコーディングが
  **MS932(Windows-31J)**のことが多く、UTF-8 の日本語コメント入り `.java` は
  文字化け・コンパイルエラーの原因になりうる。
- **対応**: ジェネレーターに `--encoding <charset>`(既定 UTF-8)を追加。
  `--encoding MS932`(Shift_JIS / Windows-31J も可)で生成でき、生成コードの
  日本語コメントは**すべて MS932 で表現可能な文字のみ**を使用していることを検証済み
  (UTF-8→MS932 の無損失往復確認 + ECJ `-encoding MS932 -1.5` でエラーゼロ)。
- Eclipse へ即インポートできるプロジェクトテンプレートを
  `verification/eclipse-project-templates/{old-env,new-env}/` に用意
  (old=J2SE-1.5/MS932、new=JavaSE-1.8/UTF-8)。

## 3. PostgreSQL のバージョン

- 要件は 16.8 だが、本環境で入手可能なのは apt の 16.13
  (postgres:16.8 Docker イメージは取得不可)。
- 16.x 系のマイナーバージョン差であり、SQL 挙動の検証には影響しないと判断。

## 4. Seasar2 の入手

- maven.seasar.org は停止済み。Maven Central に存在する Seasar2/S2Dao
  アーティファクトの調査結果は docs/research/RESEARCH.md を参照。
- Central に無いものは GitHub 上の seasarorg ミラーからソースを取得し
  ローカルビルド/ベンダリングで対応(Apache License 2.0)。

## 5. 検証の考え方

上記により本環境での「旧環境」は *Oracle 互換モード H2 + Java5 互換構文
(-source 1.5)+ JUnit3.8*、「新環境」は *PostgreSQL 16.13 + OpenJDK 8 +
同一テストコード* として構成する。両環境で

1. JUnit の実行結果(テスト数・成功数)
2. エビデンス CSV(DAO 戻り値・操作後データセット)

を正規化して突き合わせることで、「同一 dao/sql に対して新旧環境で同一の
結果・データセットが得られること」を確認する。
