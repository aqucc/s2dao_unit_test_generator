# verification/oracle/ — 実 Oracle 11g 接続用の一式

本ディレクトリは、生成テストを**実 Oracle 11g** に対して実行するためのひな形と、
Oracle 固有セマンティクスの実動作プローブを収める。
(本開発環境では実 Oracle が調達不可のため、旧環境サロゲート = H2 Oracle 互換モードで
検証している。docs/CONSTRAINTS.md 参照。実 Oracle での実行手順・注意点の全体は
**docs/ORACLE_MIGRATION_CHECKLIST.md** にまとめている)

| ファイル | 役割 |
|----------|------|
| `schema-oracle.sql` | 生成テスト用スキーマの Oracle 11g 版(NUMBER / VARCHAR2 / DATE / TIMESTAMP。DROP は ORA-00942 無視運用、SQL*Plus 用の例外無視 PL/SQL ブロック版もコメントで併記) |
| `s2dao-oracle.dicon` | 定数アノテーション版サンプル(examples.dao)用の S2Container 定義。`oracle.jdbc.OracleDriver` + `jdbc:oracle:thin:@...` に差し替え済み |
| `s2dao-tiger-oracle.dicon` | Tiger アノテーション版サンプル(examples.dao.tiger)用。同上 |
| `s2daotest-oracle.properties.example` | `s2daotest.properties` の Oracle 用記入例(素の JDBC 接続 / dialect=oracle / dicon) |
| `semantics-probe/` | Oracle 固有セマンティクスの実動作プローブ(下記) |

## semantics-probe

`semantics-probe/run-probe.sh` は、(a) 空文字列 INSERT の NULL 化、(b) DATE 列への
時刻付き値、(c) NUMBER 精度と BigDecimal 正規化、(d) null の setNull バインド、の
4 点について testsupport(WriteDbUtil / GetDatasetUtil / EvidenceWriter)の実挙動を
観測する。引数無しで H2 Oracle 互換モード、引数指定で PostgreSQL / 実 Oracle にも
向けられる:

```
./run-probe.sh                                                            # H2 Oracle モード
./run-probe.sh jdbc:postgresql://127.0.0.1:5432/s2daogen org.postgresql.Driver s2dao s2dao
./run-probe.sh "jdbc:oracle:thin:@host:1521:SID" oracle.jdbc.OracleDriver scott tiger  # 要 ojdbc jar
```

本環境での観測結果(H2 Oracle モード / PostgreSQL 16 実 DB)と実 Oracle との差分の
整理は docs/VERIFICATION_DB_CONNECTIVITY.md を参照。
