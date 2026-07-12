-- 生成テスト実行用スキーマ(旧環境=H2 Oracle互換モード / 新環境=PostgreSQL16 共通)
-- Oracle 方言の scott/EMP・DEPT を基に、生成テストが参照する全テーブルを定義する。
-- ・EMP に TSTAMP(Employee.timestamp_COLUMN="tstamp": S2Dao 自動タイムスタンプ列)を追加
-- ・DEPT に VERSIONNO(Department.versionNo: S2Dao 楽観ロック列)を追加
-- ・NOPKTABLE は NoPkTable エンティティ(主キー無し)から起こした表(samples の DDL に無いため)
-- DROP TABLE IF EXISTS は H2・PostgreSQL 双方が解釈できる(実 Oracle では IF EXISTS を外すこと)。

DROP TABLE IF EXISTS EMP;
DROP TABLE IF EXISTS DEPT;
DROP TABLE IF EXISTS NOPKTABLE;

CREATE TABLE DEPT (
    DEPTNO    NUMERIC(4)  NOT NULL PRIMARY KEY,
    DNAME     VARCHAR(30),
    LOC       VARCHAR(30),
    VERSIONNO NUMERIC(8)
);

CREATE TABLE EMP (
    EMPNO    NUMERIC(10) NOT NULL PRIMARY KEY,
    ENAME    VARCHAR(30),
    JOB      VARCHAR(30),
    MGR      NUMERIC(4),
    HIREDATE DATE,
    SAL      NUMERIC(12, 2),
    COMM     NUMERIC(12, 2),
    DEPTNO   NUMERIC(4),
    TSTAMP   TIMESTAMP
);

CREATE TABLE NOPKTABLE (
    AAA VARCHAR(30),
    BBB INTEGER
);
