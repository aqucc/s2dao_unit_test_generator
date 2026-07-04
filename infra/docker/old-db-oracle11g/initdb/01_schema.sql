-- =====================================================================
-- Oracle XE 11g コンテナ初回起動時に実行されるスキーマ作成スクリプト
-- (gvenzl イメージの /container-entrypoint-initdb.d は SYS で実行されるため、
--  CURRENT_SCHEMA をアプリユーザーへ切り替えてから作成する)
--
-- 内容は verification/oracle/schema-oracle.sql と同一のテーブル定義。
-- 初回起動時のみ実行されるため DROP は不要(再初期化は `docker compose down -v`)。
-- 自アプリの検証に使う場合は、このファイルを実 DDL に差し替えること。
-- =====================================================================
ALTER SESSION SET CURRENT_SCHEMA = S2DAOTEST;

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
