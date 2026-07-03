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
- **代替**:
  - 生成コードは Java5 互換構文のみ使用(ジェネリクスは可、
    ダイヤモンド演算子・try-with-resources・マルチキャッチ・
    String switch・インタフェース実装メソッドへの @Override は不使用)
  - JDK8 の `javac -source 1.6 -target 1.6` でコンパイル検証
    (JDK8 で指定可能な最古のソースレベル。Java5 と 1.6 の構文差は
    ほぼ無いため、Java5 構文互換の実質的な検証となる)
  - JUnit は 3.8.x 形式(`junit.framework.TestCase`)で生成し、
    Java5 時代のランナーでも動作する形とする
- 影響: 実 Java5 VM 上での実行確認は利用者環境での実施が必要。

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
(-source 1.6)+ JUnit3.8*、「新環境」は *PostgreSQL 16.13 + OpenJDK 8 +
同一テストコード* として構成する。両環境で

1. JUnit の実行結果(テスト数・成功数)
2. エビデンス CSV(DAO 戻り値・操作後データセット)

を正規化して突き合わせることで、「同一 dao/sql に対して新旧環境で同一の
結果・データセットが得られること」を確認する。
