-- =====================================================================
-- 生成テスト実行用スキーマ【実 Oracle 11g 用】
--
-- verification/ddl/schema.sql(H2 Oracle互換モード / PostgreSQL16 用)と
-- 論理的に同一のテーブルを、Oracle 11g ネイティブの型で定義する。
--   ・NUMERIC   → NUMBER
--   ・VARCHAR   → VARCHAR2
--   ・TIMESTAMP → TIMESTAMP(Oracle 9i 以降ネイティブ対応。11g で使用可)
--   ・HIREDATE は Oracle 伝統の DATE(時刻成分も保持できる点が H2/PG と異なる。
--     docs/ORACLE_MIGRATION_CHECKLIST.md の「DATE 型マッピング」参照)
--
-- 実行方法:
--   (1) SQL*Plus      : sqlplus <user>/<pass>@<接続記述子> @schema-oracle.sql
--   (2) 検証ランナー   : RunGeneratedTests の第1引数にこのファイルを渡す
--
-- Oracle には DROP TABLE IF EXISTS が無い(11g)。ここでは素の DROP TABLE を
-- 使い、対象が無い初回実行時の ORA-00942 は無視する運用とする
--   ・RunGeneratedTests(JDBC)は DDL エラーを [ddl-warn] として無視して継続する
--   ・SQL*Plus は既定でエラー後も継続する(WHENEVER SQLERROR を設定しないこと)
-- エラー表示自体を避けたい場合は、末尾コメントの「例外無視の PL/SQL 無名ブロック」
-- 版を使う(SQL*Plus 専用。JDBC のランナーはブロック内の ; を跨げない)。
-- =====================================================================

DROP TABLE EMP;
DROP TABLE DEPT;
DROP TABLE NOPKTABLE;

CREATE TABLE DEPT (
    DEPTNO    NUMBER(4)   NOT NULL PRIMARY KEY,
    DNAME     VARCHAR2(30),
    LOC       VARCHAR2(30),
    VERSIONNO NUMBER(8)
);

CREATE TABLE EMP (
    EMPNO    NUMBER(10)   NOT NULL PRIMARY KEY,
    ENAME    VARCHAR2(30),
    JOB      VARCHAR2(30),
    MGR      NUMBER(4),
    HIREDATE DATE,
    SAL      NUMBER(12, 2),
    COMM     NUMBER(12, 2),
    DEPTNO   NUMBER(4),
    TSTAMP   TIMESTAMP
);

CREATE TABLE NOPKTABLE (
    AAA VARCHAR2(30),
    BBB NUMBER(10)
);

-- ---------------------------------------------------------------------
-- 【SQL*Plus で ORA-00942 の表示も出したくない場合の DROP(例外無視ブロック)】
-- 上の DROP TABLE 3 文の代わりに以下を使う(各ブロックは『/』で実行)。
--
-- BEGIN EXECUTE IMMEDIATE 'DROP TABLE EMP';
-- EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF; END;
-- /
-- BEGIN EXECUTE IMMEDIATE 'DROP TABLE DEPT';
-- EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF; END;
-- /
-- BEGIN EXECUTE IMMEDIATE 'DROP TABLE NOPKTABLE';
-- EXCEPTION WHEN OTHERS THEN IF SQLCODE != -942 THEN RAISE; END IF; END;
-- /
-- ---------------------------------------------------------------------
