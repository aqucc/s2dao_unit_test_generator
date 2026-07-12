# verification/ — 検証作業一式の歩き方

S2Dao 単体テストジェネレーターが「実プロジェクトの DAO から、新旧どちらの実行環境でも
同じ結果になるテストを生成できる」ことを確かめるための、入力・生成物・実行・結果・検証を
まとめたディレクトリです。

## 各フォルダの役割

| フォルダ | 役割 |
| --- | --- |
| `samples/` | 実プロジェクトを模した**検証対象(入力)**。s2dao(定数アノテーション)/ s2dao-tiger(Tiger アノテーション)/ servicebase(ServiceBase 具象 Service)の 3 種の DAO・エンティティ・2-way SQL ソース。 |
| `generated/` | 上記 samples から generator で作った**生成結果サンプル(出力)**。同じ 3 種。検証時点の成果物のスナップショット。 |
| `lib/` | 生成テストの実行に使う Seasar2 / S2Dao ランタイム jar 一式(`seasar2-2.4.48-archive` を含む)。 |
| `setup/` | **初回の環境構築**。Seasar2 ランタイム jar のビルド(`build-seasar2.sh` + `jdbc3-stub/`)、PostgreSQL 起動・DB 作成(`pg-setup.sh`)、ランタイム健全性のスモークテスト(`smoke/`)。 |
| `run/` | **両環境での実行一式**。旧環境相当(`run-old-env.sh`)/ 新環境(`run-new-env.sh`)/ 共通ロジック(`run-lib.sh`)、JUnit ランナー(`runner/`)、dicon(`dicon/`)、DDL(`ddl/`)、任意設定(`config/`)、結果突き合わせ(`compare/`)。 |
| `results/` | **実行結果**。`old-env/`(H2 Oracle 互換モード + `-source 1.5`)と `new-env/`(PostgreSQL 16 + Java8)それぞれの build / evidence(エビデンス CSV)/ log。 |
| `checks/` | 生成コードの**コンパイル検証**。`verify-generated-compile.sh`(javac `-source 1.5`)/ `verify-eclipse-compile.sh`(Eclipse JDT / ECJ、UTF-8・MS932)。 |
| `oracle/` | **実 Oracle** 用の DDL / dicon / properties と、Oracle 固有セマンティクスの動作プローブ(`semantics-probe/`)。 |
| `eclipse-project-templates/` | Eclipse(Pleiades)で生成テストを取り込むためのプロジェクト雛形(旧環境 / 新環境)。 |

## 検証の流れ

```
samples/ (入力: 実プロジェクトを模した検証対象)
   │  generator で生成
   ▼
generated/ (出力: 生成結果サンプル)
   │  run/run-old-env.sh / run-new-env.sh で両環境実行
   ▼
results/old-env, results/new-env (実行結果: evidence CSV + log)
   │  run/compare/compare.sh で新旧を突き合わせ
   ▼
一致検証(PASS = 新旧環境で同一の DAO 挙動)
```

- `checks/` … 生成テストが Java5(`-source 1.5`)/ ECJ でエラーゼロにコンパイルできることを別途検証する。
- `setup/` … 上記の実行に先立ち、初回だけ実行する環境構築(ランタイム jar ビルド・DB 作成・スモーク)。

**samples は実プロジェクトを模した検証対象(入力)であり、generated はそこからの生成結果(出力)サンプル**である点に注意。
