#!/usr/bin/env bash
#
# 【新環境】PostgreSQL 16 + JDK8 + JUnit3。verification/env/pg-setup.sh 実行後に使う
# (DB=s2daogen が無ければ pg-setup.sh を参考に作成。ここでは s2daosmoke と同じロール s2dao を使う)。
# 生成テストを両サンプルで実行し、エビデンス CSV を verification/new-env/evidence/ に出力する。
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$HERE/run-lib.sh"

# 新環境 DB(s2daogen)を用意する(冪等)。ロール s2dao は pg-setup.sh が作成済みの想定。
ensure_pg_db() {
    if ! pg_lsclusters -h 2>/dev/null | awk '{print $4}' | grep -q online; then
        pg_ctlcluster 16 main start || true; sleep 2
    fi
    if ! PGPASSWORD=s2dao psql -h 127.0.0.1 -U s2dao -d s2daogen -c 'select 1' >/dev/null 2>&1; then
        sudo -u postgres psql -v ON_ERROR_STOP=1 <<'SQL' || true
DROP DATABASE IF EXISTS s2daogen;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='s2dao') THEN CREATE ROLE s2dao LOGIN PASSWORD 's2dao'; END IF; END $$;
CREATE DATABASE s2daogen OWNER s2dao;
SQL
        sudo -u postgres psql -d s2daogen -v ON_ERROR_STOP=1 <<'SQL' || true
GRANT ALL ON SCHEMA public TO s2dao;
ALTER SCHEMA public OWNER TO s2dao;
SQL
    fi
}
ensure_pg_db

ENVDIR="new-env"
ENVLABEL="NEW(PostgreSQL16)"
DIALECT="postgre"
DRIVER="org.postgresql.Driver"
URL="jdbc:postgresql://127.0.0.1:5432/s2daogen"
USER="s2dao"
PASS="s2dao"

CONST_JAVA="$HERE/../samples/s2dao/s2-dao-examples/src/main/java"
CONST_SQL="$HERE/../samples/s2dao/s2-dao-examples/src/main/resources"
TIGER_JAVA="$HERE/../samples/s2dao-tiger/s2-dao-tiger-examples/src/main/java"
TIGER_SQL="$HERE/../samples/s2dao-tiger/s2-dao-tiger-examples/src/main/resources"

fail=0
run_sample "$ENVDIR" "$ENVLABEL" "$DIALECT" "$DRIVER" "$URL" "$USER" "$PASS" \
    s2dao "$CONST_JAVA" "$CONST_SQL" s2dao-pg.dicon \
    examples.dao.EmployeeDaoTest!testGetEmployeeByDeptno examples.dao.DepartmentDaoTest \
    examples.dao.EmployeeAutoDaoTest examples.dao.NoPkTableDaoTest \
    examples.dao.DepartmentManagerTest || fail=1

run_sample "$ENVDIR" "$ENVLABEL" "$DIALECT" "$DRIVER" "$URL" "$USER" "$PASS" \
    s2dao-tiger "$TIGER_JAVA" "$TIGER_SQL" s2dao-tiger-pg.dicon \
    examples.dao.tiger.EmployeeDaoTest examples.dao.tiger.DepartmentDaoTest \
    examples.dao.tiger.EmployeeAutoDaoTest examples.dao.tiger.Employee2DaoTest || fail=1

echo "==== NEW-ENV overall exit=$fail ===="
exit $fail
