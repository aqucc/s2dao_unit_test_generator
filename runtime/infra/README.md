# テスト用インフラ(docker-compose 一式)

WSL2 の `/opt/docker/` 配下に配置して使う、移行検証用 DB コンテナ一式。

```
/opt/docker/
├── old-db-oracle11g/     … 旧DB: Oracle XE 11g 相当(gvenzl/oracle-xe:11)
│   ├── docker-compose.yaml
│   └── initdb/01_schema.sql
└── new-db-postgres16/    … 新DB: PostgreSQL 16.8(ja_JP.utf8)
    ├── docker-compose.yaml
    ├── Dockerfile        … ja_JP.utf8 ロケール追加
    └── initdb/01_schema.sql
```

## 使い方

```bash
# 旧DB(初回はイメージ取得+DB作成で数分かかる)
cd /opt/docker/old-db-oracle11g && docker compose up -d
# 新DB
cd /opt/docker/new-db-postgres16 && docker compose up -d --build

# 状態確認(healthy になるまで待つ)
docker ps
```

Windows 側の Eclipse からは WSL2 の localhost 転送により
そのまま `localhost` で接続できる:

| | 旧DB | 新DB |
|---|---|---|
| JDBC URL | `jdbc:oracle:thin:@//localhost:1521/XE` | `jdbc:postgresql://localhost:5432/s2daogen` |
| ユーザー | `S2DAOTEST` / `s2daotest` | `s2daotest` / `s2daotest` |
| ドライバ | ojdbc5(Java5)/ ojdbc6(Java8)※利用者調達 | postgresql-42.2.x(`verification/lib/` 同梱) |
| dicon 例 | `verification/oracle/s2dao-oracle.dicon` | `verification/run/dicon/s2dao-pg.dicon` |
| properties 例 | `verification/oracle/s2daotest-oracle.properties.example` | `runtime/testsupport/s2daotest.properties.example` |

`initdb/01_schema.sql` は本リポジトリの検証用スキーマ(EMP/DEPT/NOPKTABLE)。
**自アプリの検証時は実 DDL に差し替える**こと(初回起動時のみ実行。
作り直しは `docker compose down -v` でボリューム削除後に再起動)。

## ご質問への回答(過不足の指摘)

### 旧API(java5+tomcat5)・新API(java8+tomcat9)コンテナは不要か?

**ユニットテスト目的なら不要**、という判断で本一式には含めていない。

- 生成される JUnit テストは Eclipse(または CLI)の JVM 内で S2Container を起動し
  DAO を直接実行するため、**Tomcat を経由しない**。必要なのは DB だけ。
- 将来、画面/API を通した結合テストをしたくなった場合:
  - **新API**: 公式イメージで簡単に建つ。例:
    ```yaml
    services:
      new-api:
        image: tomcat:9-jre8            # Tomcat9 + Java8(公式)
        ports: ["8080:8080"]
        volumes:
          - ./webapps:/usr/local/tomcat/webapps
    ```
  - **旧API**: Tomcat5/Java5 の公式・維持イメージは**存在しない**。
    どうしても要る場合は archive.apache.org の Tomcat 5.5 と
    Oracle アーカイブの JDK 1.5(要ライセンス確認)から自前で
    イメージをビルドすることになるが、EOL ソフトの再構築でありお勧めしない。
    旧APIの動作確認は既存の実機/実VMで行う方が確実。

### 文字コードの制約(旧DB shiftjis について)【重要】

- `gvenzl/oracle-xe:11` の DB キャラクタセットは **AL32UTF8 固定**で、
  正攻法(CSALTER / DB 再作成)では JA16SJIS にできない。
- **採用した妥協策**: 初回起動直後(データ辞書が ASCII のみ・日本語データ投入前)に
  `ALTER DATABASE CHARACTER SET INTERNAL_USE JA16SJIS` で
  **キャラクタセットを張り替える**(同梱の `charset-ja16sjis.sh` を一度だけ実行)。

  ```bash
  cd /opt/docker/old-db-oracle11g
  docker compose up -d          # healthy になるまで待つ
  ./charset-ja16sjis.sh         # 既定 JA16SJIS(引数で JA16SJISTILDE も可)
  ```

  - 初期化直後なら辞書は実質 ASCII のみで、ASCII 域は AL32UTF8/JA16SJIS で
    バイト表現が同一のため、張り替えによる既存データ不整合は実質発生しない。
  - 張り替え後は VARCHAR2 が SJIS バイト列になり、旧環境の本質的な挙動差
    (**バイト長セマンティクス=全角2バイト、ORA-12899 の出方、LENGTHB/SUBSTRB、
    BINARY ソートの SJIS バイト順**)が忠実に再現される。JDBC 取得後の Java 側は
    従来どおり Unicode(String)であり、テストコードへの影響はない。
  - **条件**: (1) props$ の直接 UPDATE は使わない(INTERNAL_USE 版を使う)
    (2) JA16SJIS / JA16SJISTILDE は旧本番実機の
    `SELECT value FROM nls_database_parameters WHERE parameter='NLS_CHARACTERSET';`
    に合わせる (3) NLS_NCHAR_CHARACTERSET は触らない
    (4) **thin JDBC には ojdbc と同版の `orai18n.jar` を classpath に追加**
    (thin 組み込み変換は ASCII/ISO8859-1/UTF-8 系のみ。無いと
    「Non supported character set」エラー)。
  - Oracle 非サポート操作のため**このテスト用コンテナ限定**。やり直しは
    `docker compose down -v` で作り直す。
- 張り替えをしない場合の代替(参考): テストデータの全角文字列を SJIS 想定桁の
  2/3 以下に抑える/DDL を `VARCHAR2(n CHAR)` にする/正規ライセンスの 11g SE/EE +
  [oracle/docker-images](https://github.com/oracle/docker-images) でカスタムビルド。
- なお新DB側は要件どおり **ja_JP.utf8**(Dockerfile で localedef 済み)。
  PostgreSQL の `VARCHAR(n)` は文字数セマンティクスなので桁差問題は起きない。

### その他の推奨・注意

- **タイムゾーン**: 両コンテナとも `Asia/Tokyo` に固定済み。エビデンス CSV の
  日時正規化は JVM の TZ に依存するため、**テストを実行する Eclipse/JVM 側も
  新旧で同一 TZ にする**こと(`-Duser.timezone=Asia/Tokyo` 推奨)。
- **メモリ**: XE 11g はおおむね 1〜2GB 消費する。WSL2 の `.wslconfig` で
  `memory=6GB` 以上を確保しておくと安定する。
- **XE 11g の制限**: データ 11GB・RAM 1GB・1CPU(テスト用途では通常問題なし)。
- **PostgreSQL のバージョン固定**: `postgres:16.8` にピン留め済み
  (要件どおり。セキュリティ更新を取り込む場合は 16.x の範囲で更新)。
- **あると便利(任意)**: DB ブラウザ。PostgreSQL は
  `dpage/pgadmin4` か `adminer` をサービス追加、Oracle は Windows 側に
  SQL Developer を入れるのが手軽(コンテナ化不要)。
- **ojdbc ドライバ**: 再配布不可のためコンテナにもリポジトリにも同梱していない。
  Oracle 公式からダウンロードし、Eclipse プロジェクトの `lib/ojdbc.jar` に
  配置する(旧環境=Java5 は ojdbc14/ojdbc5、Java8 なら ojdbc6)。
  ※ Oracle 11g への接続はどちらのドライバでも可。

## 本リポジトリの検証スクリプトとの関係

`verification/run/run-old-env.sh` / `run-new-env.sh` は CI 向けに
H2(Oracle 互換モード)とローカル PostgreSQL を使う。実 Oracle XE コンテナで
生成テストを動かす手順は `docs/ORACLE_MIGRATION_CHECKLIST.md` を参照
(dicon の URL をこの compose の接続先に合わせるだけ)。
