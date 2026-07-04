# DB 接続観点の検証記録(実 Oracle / PostgreSQL 実 DB)

docs/VERIFICATION.md(全体の検証記録)への追補。発注者指摘
「生成されたユニットテストコードが (1) Java5 Eclipse + 実 Oracle 接続
(2) Java8 Eclipse + PostgreSQL 接続 で動作するコードになっているか」のうち、
**DB 接続観点**の厳格レビューと実動作テストの結果を記録する
(Java5/Eclipse 観点は docs/VERIFICATION_JAVA5_ECLIPSE.md で検証済み)。

実施日: 2026-07-04。実 Oracle 11g は本環境で調達不可(docs/CONSTRAINTS.md)のため、
Oracle 観点は「静的レビュー + H2 Oracle 互換モードでの実動作」で検証し、
PostgreSQL 観点は **PostgreSQL 16 実 DB への実接続**で検証した。

---

## 1. 結論サマリ

| 観点 | 結論 |
|------|------|
| ojdbc14(JDBC3)API 適合 | **適合**。testsupport・生成テスト・ランナーが使う java.sql API は JDBC1〜2 世代のみで、JDBC4+ API は不使用(grep で機械確認)。詳細: docs/ORACLE_MIGRATION_CHECKLIST.md O1 |
| Oracle 固有セマンティクス(''=NULL / DATE / NUMBER / 識別子 / NLS) | 生成テストコード・testsupport の設計で吸収済みまたは発生しない。修正 3 件・注意点 11 件を docs/ORACLE_MIGRATION_CHECKLIST.md に整理 |
| Eclipse テンプレート .classpath | **重大欠落 2 件を発見・修正**(aopalliance-1.0.jar / geronimo-j2ee_1.4_spec-1.0.jar。無いと S2Container 初期化で NoClassDefFoundError となり全テスト実行不能) |
| PostgreSQL 16 実 DB 実動作 | **PASS**(41 テスト 0 fail 0 error、エビデンス 75 ファイルが旧環境と完全一致) |
| H2 Oracle 互換モード(旧環境サロゲート)実動作 | **PASS**(同上) |
| Oracle 固有セマンティクス実動作プローブ | **PASS**(H2 Oracle モード・PostgreSQL 16 実 DB の双方で実行) |

---

## 2. 静的レビュー(実 Oracle 11g + ojdbc14/ojdbc5 観点)

レビュー対象: verification/generated/ の生成テスト実物 8+5 クラス、
testsupport 本体(S2TestContext / WriteDbUtil / GetDatasetUtil / EvidenceWriter /
ValueFactory / TestDataParam / DbDialect)、generator の gen パッケージ
(TestClassGenerator / TestValues)、verification/runner/RunGeneratedTests.java。

発見した問題と対処の全一覧(重要度付き)は **docs/ORACLE_MIGRATION_CHECKLIST.md 第 1 章**を参照。
要点:

- **修正した問題(3 件)**
  - F1/F2(高): Eclipse テンプレート .classpath の `aopalliance-1.0.jar` /
    `geronimo-j2ee_1.4_spec-1.0.jar` 欠落 → 追加(§4 の実験で必須と実証)
  - F3(高): `WriteDbUtil` の null バインドが型無し `setObject(i, null)` で、
    Oracle JDBC が ORA-17004 で拒否 → `setNull(i, Types.NULL)` に修正し単体テスト追加
  - F4(中): 「Java5 で ojdbc6 可」と誤読できる記載 → ojdbc14/ojdbc5 指定に修正
- **問題なしと確認**: JDBC3 API 適合(O1)、空文字列非生成(O2、単体テスト
  `ValueFactoryTest#testStringValuesNeverEmpty` 追加)、DATE 型の戻り型揺れの正規化吸収(O3)、
  NUMBER→BigDecimal の正準化(O4)、無引用識別子による大文字化差の吸収(O5)、
  NLS/TZ 非依存(O6)
- **整備した成果物**: `verification/oracle/`(schema-oracle.sql / s2dao-oracle.dicon /
  s2dao-tiger-oracle.dicon / s2daotest-oracle.properties.example / semantics-probe)

## 3. 実動作テスト結果

### 3-1. 旧環境サロゲート: H2 Oracle 互換モード(run-old-env.sh)

修正(WriteDbUtil の setNull 化、testsupport 再ビルド)後に全生成テストを再実行:

```
s2dao       : ==== RESULT tests=21 run=21 failures=0 errors=0 ====
s2dao-tiger : ==== RESULT tests=20 run=20 failures=0 errors=0 ====
exit=0
```

### 3-2. 新環境: PostgreSQL 16 実 DB(run-new-env.sh)= 実 DB 実接続の実動作テスト

PostgreSQL 16.13(apt、127.0.0.1:5432、DB=s2daogen、ロール s2dao)へ
JDBC(postgresql-42.2.27)で実接続し、DDL 投入 → 全生成テスト実行:

```
s2dao       : ==== RESULT tests=21 run=21 failures=0 errors=0 ====
s2dao-tiger : ==== RESULT tests=20 run=20 failures=0 errors=0 ====
exit=0
```

### 3-3. 新旧一致(compare.sh)

```
[s2dao]       比較ファイル=37 一致=37 不一致=0 片側のみ(OLD=0, NEW=0)
[s2dao-tiger] 比較ファイル=38 一致=38 不一致=0 片側のみ(OLD=0, NEW=0)
== 合計: 比較=75 一致=75 不一致=0 片側のみ=0 ==
== 判定: 一致(PASS) ==
```

(除外カラム: TIMESTAMP / TSTAMP / VERSIONNO / VERSION_NO = S2Dao 自動更新列)

### 3-4. Oracle 固有セマンティクス実動作プローブ(verification/oracle/semantics-probe/)

同一プローブを H2 Oracle 互換モードと PostgreSQL 16 実 DB の両方に対して実行し PASS。
観測結果(要旨):

| 項目 | H2 Oracle モード(旧環境サロゲート) | PostgreSQL 16 実 DB | 実 Oracle 11g(文献/仕様) |
|------|------|------|------|
| (a) `''` の INSERT | **NULL として格納**(Oracle と同挙動) | `''` のまま格納 | NULL として格納 |
| (b) DATE 列へ時刻付き値 | **時刻を保持**(Oracle と同挙動。getObject=Timestamp) | 時刻を切り捨て(getObject=java.sql.Date、00:00:00) | 時刻を保持。ただし ojdbc14 既定は getObject=java.sql.Date で時刻消失(V8Compatible=true で保持)、ojdbc5/6 は Timestamp で保持 |
| (c) NUMBER(12,2) へ 3001 | BigDecimal `3001` → normalize `3001` | BigDecimal `3001.00` → normalize `3001` | BigDecimal(スケールは格納値依存)→ normalize で同一正準形 |
| (c') NUMBER(19) へ 19 桁整数 | 正準形一致(`1234567890123456789`) | 同左 | 同左(38 桁まで) |
| (d) null の setNull(Types.NULL) バインド | 成功(NULL 格納) | 成功(NULL 格納) | ojdbc の ORA-17004(型無し setObject(null))を回避する既知の正当手段 |

この結果から:
- H2 Oracle モードは (a)(b) の Oracle 固有挙動を再現しており、旧環境サロゲートとして妥当。
- 逆に言えば **(a)(b) は「実 Oracle(旧)vs PostgreSQL(新)」で実際に差が出る点**である。
  生成テストは空文字を使わず(O2)、DATE 列に時刻なし値のみを使う(O3)ため差は顕在化しない。
  利用者データで踏む場合の対処は docs/ORACLE_MIGRATION_CHECKLIST.md C2 参照。

## 4. Eclipse テンプレート実行クラスパスの実証実験

生成テスト(s2dao サンプル 21 テスト)を「.classpath 記載の jar のみ」で実行する実験を行い、
テンプレートの欠落を実証・修正した:

| クラスパス構成 | 結果 |
|----------------|------|
| 修正前 .classpath 相当(junit / support / s2-framework / s2-extension / s2-dao / s2-dao-tiger / ognl / javassist / commons-logging / geronimo-jta + ドライバ) | `NoClassDefFoundError: javax.servlet.http.HttpServletRequest`(S2Container 初期化で全滅) |
| + geronimo-j2ee_1.4_spec-1.0.jar | `NoClassDefFoundError: org/aopalliance/intercept/MethodInterceptor`(S2DaoInterceptor の AOP で全滅) |
| + aopalliance-1.0.jar | **21 テスト全緑** |
| 参考: geronimo-jta を抜く(j2ee spec が javax.transaction を包含) | 全緑(ただし冗長性のため .classpath には jta も残す) |

`geronimo-ejb_2.1_spec` / `portlet-api` / `poi` は上記全緑構成に含まれておらず、**実行時不要**と確認。
old-env / new-env 両テンプレートの `.classpath` と README に必須 2 jar を追記済み。

## 5. 単体テスト・回帰

- `testsupport` : `mvn package`(test 含む)緑。追加テスト:
  - `WriteAndDatasetTest#testNullBindingOnStringAndNumericColumns`(setNull バインド)
  - `ValueFactoryTest#testStringValuesNeverEmpty`(空文字非生成 = Oracle ''=NULL 対策)
- `generator` : `mvn test` 緑(変更なし・回帰確認のみ)
- 修正後に run-old-env.sh / run-new-env.sh / compare.sh を全て再実行し PASS 維持(§3)
