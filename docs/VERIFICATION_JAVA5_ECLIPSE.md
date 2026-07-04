# Java5 / Eclipse(Pleiades)観点の厳格検証レポート

対象: s2dao ユニットテストコードジェネレーターが生成するテストコード、および
その実行時ランタイム `testsupport`。

要件:
- 旧環境 = **Java5 の Eclipse(Pleiades)+ Oracle 接続**
- 新環境 = **Java8 の Eclipse(Pleiades)+ PostgreSQL 接続**

従来は JDK8 の `-source/-target 1.6` コンパイル確認のみだったため、本レポートでは
**(1) `-source 1.5` 厳格コンパイル、(2) API レベルの Java5 互換(animal-sniffer)、
(3) Eclipse 実コンパイラ ECJ、(4) Pleiades の MS932 文字コード、(5) Eclipse
プロジェクトテンプレート**の各観点で厳格化・再検証した結果を示す。

検証環境: JDK8 = OpenJDK `1.8.0_492`(`/usr/lib/jvm/java-8-openjdk-amd64`)、
Maven 3.9.11。実 Java5 VM は本環境に導入不可のため、後述の代替で世代・API・
実コンパイラを検証した(`docs/CONSTRAINTS.md` §2 参照)。

---

## 1. `-source/-target 1.5` 厳格コンパイル

- **javac が 1.5 を受理するか**: JDK8 の javac は `-source 1.5 -target 1.5` を
  **受理する**(obsolete 警告 3 種を出すのみ):
  ```
  warning: [options] bootstrap class path not set in conjunction with -source 1.5
  warning: [options] source value 1.5 is obsolete and will be removed in a future release
  warning: [options] target value 1.5 is obsolete and will be removed in a future release
  ```
  生成されるバイトコードは **major version 49(= Java5 世代)**。実 Java5 VM で
  ロード可能な世代である。
- **testsupport 本体**: `testsupport/pom.xml` の
  `maven.compiler.source/target` を **1.6 → 1.5** に変更。`mvn test` で
  ビルド成功・単体テスト **14 件すべて成功**。本体 7 クラスすべて major 49 を確認:
  `DbDialect / EvidenceWriter / GetDatasetUtil / S2TestContext / TestDataParam /
  ValueFactory / WriteDbUtil`。
- **生成テスト**: `scripts/verify-generated-compile.sh` を **1.5** に強化。
  両サンプルを `gen-all` で再生成し `javac -source 1.5 -target 1.5` でコンパイル:
  - s2dao(定数アノテーション): テストクラス **8 件**、エラーゼロ、major 49
  - s2dao-tiger(Tiger アノテーション): テストクラス **5 件**、エラーゼロ、major 49

  → `==== 検証 OK: 両サンプルとも生成テストが -source 1.5 でエラーゼロ ====`

## 2. API レベルの Java5 互換(animal-sniffer)

構文だけでなく **使用 API が Java5 に存在するか**を機械検証した。

- `testsupport/pom.xml` に `animal-sniffer-maven-plugin:1.23` +
  `org.codehaus.mojo.signature:java15:1.0`(Maven Central 取得)を導入し、
  `check` ゴールを **test フェーズ**に束縛。`mvn test` 実行時に本体クラス
  (`target/classes`)を検査する。
- **結果: 違反ゼロ**。
  ```
  [INFO] --- animal-sniffer:1.23:check (check-java15-api) @ s2dao-testgen-support ---
  [INFO] Checking unresolved references to org.codehaus.mojo.signature:java15:1.0
  [INFO] BUILD SUCCESS
  ```
- **修正した Java5 非互換 API**: **なし**。testsupport は当初から
  `String.length()==0`(`isEmpty()` 不使用)、`Integer.valueOf` 等の Java5 API、
  `BigDecimal.stripTrailingZeros()`(1.5)、`ResultSetMetaData.getColumnLabel()`(1.4)
  のみを使用しており、Java6+ API は検出されなかった。

### 生成テストコード側の API 網羅レビュー

生成コードは animal-sniffer アノテーションを持てないため、`TestClassGenerator` が
**出力しうる全 API パターン**を目視レビューした。生成コードが参照する外部シンボルは
以下がすべてであり、いずれも Java5(JDK1.5)で提供される:

| 分類 | 参照シンボル | Java5 可否 |
|------|--------------|-----------|
| テスト基盤 | `junit.framework.TestCase`(継承)、`setUp/tearDown/super.*`、`assertTrue/assertNotNull/assertNull/assertEquals/assertFalse` | ○ JUnit3.8 |
| ランタイム(自作) | `S2TestContext / TestDataParam / WriteDbUtil / GetDatasetUtil / EvidenceWriter / ValueFactory`(= testsupport。§2 で Java5 検証済み) | ○ |
| JDBC | `java.sql.Connection`(`.close()` のみ) | ○ JDK1.1 |
| コレクション | `java.util.List`(`.size()`)、`java.util.Map`(`.get()`)※ raw 型で使用 | ○ JDK1.2 |
| ボクシング | `Integer/Long/Short/Byte/Float/Double/Boolean/Character.valueOf(...)` | ○ **すべて 1.5 で追加** |
| 対象コード | `dao.<method>(...)`、`new <Entity>()`、`entity.set<Prop>(...)`、配列 `result.length` | ○(利用者コード) |
| 文字列 | 文字列リテラル(日本語コメント含む) | ○ |

Java6+ でしか使えない API(`String.isEmpty()`、`Arrays.copyOf`、`Deque`、
`Console` 等)は **一切出力しない**ことを確認した。実サンプル生成物 13 ファイルの
実測でも `Integer/Long/Short/Float.valueOf` と上表の型のみが出現した。

- S2Container を呼ぶ経路(`S2ContainerFactory#create/init/destroy`、`getComponent`)は
  `S2TestContext` が**リフレクションで実行時解決**するため、コンパイル時の Java5 API
  検査対象外(要件どおり)。

## 3. ECJ(Eclipse JDT バッチコンパイラ)での検証

Eclipse/Pleiades が実際に使うコンパイラは javac ではなく **ECJ** である。
`org.eclipse.jdt.core.compiler:ecj:4.6.1`(Maven Central、JDK8 で動作し `-1.5` 対応)を
用い、`scripts/verify-eclipse-compile.sh` で検証した。

- **testsupport 本体**: ECJ `-1.5` で **エラーゼロ**(警告 77 件、すべて raw type /
  未使用要素のスタイル警告)。
- **生成テスト**(サンプルは UTF-8、生成テストは対象エンコーディング):

  | サンプル | エンコーディング | エラー | 警告 | 生成 Test クラス | major |
  |----------|------------------|--------|------|------------------|-------|
  | s2dao | UTF-8 | 0 | 78 | 8 | 49 |
  | s2dao-tiger | UTF-8 | 0 | 59 | 5 | 49 |
  | s2dao | MS932 | 0 | 78 | 8 | 49 |
  | s2dao-tiger | MS932 | 0 | 59 | 5 | 49 |

- **主要警告の内訳**(Eclipse は javac より厳しく、既定で下記を出す。いずれも
  **エラーではなくスタイル警告**):
  - `... is a raw type / should be parameterized`(例 48〜59 件/サンプル):
    生成コードは Java5 互換のため `java.util.List` / `Map` を **raw 型**で使用。
    ジェネリクスの型実引数を付けない設計上の意図的な結果で、実害なし。
  - `The import ... is never used` / `is never used`(5〜16 件):テストクラスによっては
    投入データが無く一部の support import(`WriteDbUtil` 等)が未使用になるための
    未使用 import 警告。実害なし。
  - **エラー(ERROR in ...)は全ケースでゼロ**。

## 4. Pleiades(日本語 Eclipse)エンコーディング対応

- ジェネレーターの `generate` / `gen-all` に **`--encoding <charset>`**(既定 UTF-8)を
  追加。`java.nio.charset.Charset` が解決できる任意の文字セットを指定でき、
  **MS932 / Shift_JIS / Windows-31J** をサポート(未知の名前はエラー終了)。
  出力ライタを固定 UTF-8 から指定 Charset に変更(`Main.writeText`)。
- **MS932 での無損失性を検証**:
  - 両サンプル 13 ファイルを UTF-8 と MS932 で生成し、
    (a) UTF-8 出力の全文字を **厳格 MS932 エンコーダ**(`REPORT` アクション)に
    かけて **unmappable 文字ゼロ**、
    (b) MS932 出力を MS932 で復号すると UTF-8 出力と **完全一致(mismatch=0、
    置換文字 U+FFFD なし)** を確認 → `RESULT: OK (no character loss)`。
  - すなわちテンプレート文字列の日本語コメントは **すべて MS932 で表現可能な文字**の
    みで構成されており、特殊記号等の非 MS932 文字は含まれない(テンプレート点検済み)。
- **MS932 + ECJ コンパイル**: MS932 で生成した `.java` を ECJ `-encoding MS932 -1.5`
  でコンパイルし **エラーゼロ**(§3 の表 MS932 行)。

## 5. Eclipse プロジェクトテンプレート

`verification/eclipse-project-templates/` に、生成テストを Eclipse へインポートして
すぐ実行できるテンプレートを用意した。実 Eclipse は本環境に無いため、ファイルは
Eclipse JDT の `.classpath` / `.project` / `.settings` スキーマに忠実に作成し、
妥当性を各 README に記載した。

- `old-env/`(**Java5 相当**):
  - `.project`(javanature + Java ビルダー)
  - `.classpath`: `src`、**JRE コンテナ `J2SE-1.5`**、`lib/` の
    `junit-3.8.2` / `s2dao-testgen-support` / Seasar2・S2Dao 一式 / Oracle ドライバ、
    出力 `bin`
  - `.settings/org.eclipse.core.resources.prefs`: `encoding/<project>=MS932`
  - `.settings/org.eclipse.jdt.core.prefs`: compliance/source/target = **1.5**
  - `README-old.md`
- `new-env/`(**Java8**):
  - `.classpath`: JRE コンテナ **`JavaSE-1.8`**、PostgreSQL ドライバ
  - `.settings/org.eclipse.core.resources.prefs`: `encoding/<project>=UTF-8`
  - `.settings/org.eclipse.jdt.core.prefs`: compliance/source/target = **1.8**
  - `README-new.md`

## 6. 回帰確認

変更(testsupport pom 1.5 + animal-sniffer、Main の `--encoding`、
verify スクリプト 1.5 化、run-lib.sh 1.5 化)後の回帰:

| 検証 | 結果 |
|------|------|
| `generator` `mvn test` | **21 件成功** |
| `testsupport` `mvn test`(animal-sniffer 込み) | **14 件成功 / 違反ゼロ** |
| `scripts/verify-generated-compile.sh`(1.5) | **OK**(s2dao 8 + tiger 5、major 49) |
| `scripts/verify-eclipse-compile.sh`(ECJ 1.5、UTF-8/MS932) | **OK**(エラーゼロ) |
| `verification/run-old-env.sh`(H2 Oracle 互換、-source 1.5) | **PASS**(21 + 20) |
| `verification/run-new-env.sh`(PostgreSQL16) | **PASS**(21 + 20) |
| `verification/compare/compare.sh` | **PASS**(比較 75 / 一致 75 / 不一致 0) |

エビデンス CSV・実行ログは自動更新カラム(TSTAMP 等の実行時刻)以外に差が無かったため
`git checkout --` で復元した。

---

## まとめ

- JDK8 javac は `-source/-target 1.5` を受理し、生成テスト・testsupport とも
  **major 49(Java5 世代)**でエラーゼロ。
- **animal-sniffer(java15)で API レベルの Java5 互換をゼロ違反で機械検証**。
  修正が必要な Java5 非互換 API は無かった。生成コードの全出力 API パターンも
  Java5 のみであることをレビュー済み。
- **ECJ(Eclipse 実コンパイラ)で UTF-8 / MS932 ともエラーゼロ**。警告は raw type /
  未使用 import のスタイル警告のみ。
- **`--encoding MS932` を実装**し、日本語コメントの MS932 無損失性と MS932+ECJ
  コンパイルを検証。
- Eclipse プロジェクトテンプレート(old=J2SE-1.5/MS932、new=JavaSE-1.8/UTF-8)を提供。

実 Java5 VM / 実 Oracle 上での最終動作確認のみ利用者環境での実施が必要だが、
Java5 世代・API・Eclipse 実コンパイラ・文字コードの各観点は本環境で検証済みである。
