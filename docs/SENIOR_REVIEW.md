# 上級レビュー結果(サブエージェント実装の全コード精査)

実施日: 2026-07-04
対象: generator / testsupport の全実装(約5,000行)を「自分で実装したならどう書くか」
という観点で精査し、発見した問題を修正した。修正後に全回帰
(generator 24 / testsupport 16 テスト、旧新両環境 41+41 テスト、
エビデンス比較 75/75、javac -source 1.5、ECJ -1.5)を再実行し PASS を確認済み。

## 修正した問題

### R1【重要/正確性】UPDATE 系テストが FK 列まで更新値に変更していた
- **問題**: UPDATE 用エンティティ組み立てで、PK・versionNo・timestamp 以外の
  全カラムを ALT(更新値)に変更していた。`deptno` のような FK 列も 50→51 に
  変更されるため、**FK 制約のある実 DB では外部キー違反で必ず失敗**する
  (サンプル DDL に FK が無かったため検証をすり抜けていた。サブエージェント自身も
  「FK制約のある実DBでは要調整」と限界を認識していたが、対処されていなかった)。
- **修正**: FK 列を 2 系統で検出し、UPDATE/DELETE では BASE(親行に一致)を維持:
  1. エンティティのリレーション定義(`_RELNO`/`@Relation`)から。relationKey
     省略時は S2Dao の既定規則(リレーション先 PK と同名の子カラム)を、
     **全 DAO 横断のエンティティレジストリ**(gen-all/generate が全メタから構築)で解決
  2. SQL 中の `a.col = b.col` 結合条件から(従来の JOIN 検出)
- 更新反映 assert の対象カラム選定からも FK 列を除外。

### R2【重要/正確性】リレーション先の親行が投入されないケース
- **問題**: 親テーブルの投入は「SQL に現れるテーブル」だけが対象だったため、
  JOIN を持たない DAO(例: EmployeeAutoDao)のテストでは DEPT の親行が
  投入されず、実 FK 環境では EMP への INSERT/UPDATE が失敗する。
- **修正**: リレーション定義から解決できる親エンティティは、SQL に現れなくても
  setUp で親行を投入する(全永続カラムを埋めた完全な行。NOT NULL 制約にも耐える)。

### R3【重要/正確性】親側テーブルのクリーンアップ順序
- **問題**: 親側 DAO(例: DepartmentDao)のテスト setUp は自テーブル(DEPT)しか
  DELETE しないため、他テストが残した EMP 行がある状態の実 FK 環境では
  `DELETE FROM DEPT` が外部キー違反になる。
- **修正**: レジストリの逆引きで「このテーブルを参照する子テーブル」を特定し、
  子 → 対象 → 親 の順で DELETE するコードを生成。

### R4【性能】S2Container をテストメソッドごとに生成・破棄していた
- **問題**: JUnit3 はテストメソッドごとに setUp を呼ぶため、dicon パース+AOP
  織り込みという高コスト処理がメソッド数だけ繰り返される。1,500 DAO 規模では
  実行時間が容認できないレベルになる。
- **修正**: `S2TestContext` を dicon パス単位の**プロセス内静的キャッシュ**に変更。
  破棄は JVM 終了時の shutdown hook に一本化(`close()` は参照解放のみ、API 不変)。

### R5【堅牢性】数値正規化の特殊値
- Double/Float の NaN・Infinity が `new BigDecimal(...)` で例外になるため、
  特殊値はそのまま文字列化するガードを追加(EvidenceWriter)。

### R6【堅牢性】ロケール非依存の識別子正規化
- カラム名等の `toUpperCase()/toLowerCase()` をすべて `Locale.ENGLISH` 明示に変更
  (トルコ語ロケール等の JVM で `i`→`İ` となり照合が壊れる古典的問題の予防)。

### R7【保守性】シード値表の二重管理リスク
- ジェネレーター側 `TestValues` と実行時側 `ValueFactory` のシード表・正準化規則は
  完全一致が前提(ずれると「投入データに引数がヒットする」保証が壊れる)。
  双方に相互参照の警告コメントを追加し、同時変更を義務付けた。

## 修正の検証
- 新規回帰テスト: `FkColumnUpdateTest`(3 件)
  - SQL JOIN 由来の FK 検出(EmployeeDao)
  - リレーション定義由来の FK 検出+親行投入(EmployeeAutoDao、JOINなし)
  - 子→親の削除順(DepartmentDao)
- 生成コードの差分は意図どおり(deptno の BASE 維持、DEPT 親行投入、EMP 先行削除)
  であることを確認し、旧新両環境での実行・エビデンス一致(75/75)・
  javac 1.5 / ECJ 1.5 / animal-sniffer の全検証を PASS。

## レビューしたが修正不要と判断した点(記録)
- `WriteDbUtil`/`GetDatasetUtil` のテーブル名文字列連結: 入力はジェネレーターが
  管理するメタ情報のみでありユーザー入力ではないため、SQL インジェクションの
  懸念なし(ドキュメント化のみ)。
- `EvidenceWriter` の日時整形が JVM 既定タイムゾーン依存:
  新旧比較は同一 TZ での実行が前提(ORACLE_MIGRATION_CHECKLIST.md 参照)。
- Bean プロパティ列挙は `TreeSet` で名前順のため `getMethods()` の順序差
  (Java5/8 間)の影響なし — 既に正しい設計。
- `ValueFactory.deriveString` が最長 7 文字を生成: 極端に短い VARCHAR 列では
  桁あふれの可能性(既知の限界としてチェックリストに記載済みの範囲)。
- Calendar 型プロパティへの日付リテラル代入は未対応(該当 DTO はスキップされ
  TODO コメントが残る設計のため実害なし)。
