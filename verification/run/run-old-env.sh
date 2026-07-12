#!/usr/bin/env bash
#
# 【旧環境相当】H2 Database の Oracle 互換モード + JDK8(-source 1.6)+ JUnit3。
# 生成テストを両サンプルで実行し、エビデンス CSV を verification/results/old-env/evidence/ に出力する。
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$HERE/run-lib.sh"

ENVDIR="old-env"
ENVLABEL="OLD(H2-Oracle-mode)"
DIALECT="h2-oracle"
DRIVER="org.h2.Driver"
URL="jdbc:h2:mem:s2daogen;MODE=Oracle;DB_CLOSE_DELAY=-1"
USER="sa"
PASS=""

CONST_JAVA="$HERE/../samples/s2dao/s2-dao-examples/src/main/java"
CONST_SQL="$HERE/../samples/s2dao/s2-dao-examples/src/main/resources"
TIGER_JAVA="$HERE/../samples/s2dao-tiger/s2-dao-tiger-examples/src/main/java"
TIGER_SQL="$HERE/../samples/s2dao-tiger/s2-dao-tiger-examples/src/main/resources"

fail=0
run_sample "$ENVDIR" "$ENVLABEL" "$DIALECT" "$DRIVER" "$URL" "$USER" "$PASS" \
    s2dao "$CONST_JAVA" "$CONST_SQL" s2dao-h2.dicon \
    examples.dao.EmployeeDaoTest!testGetEmployeeByDeptno examples.dao.DepartmentDaoTest \
    examples.dao.EmployeeAutoDaoTest examples.dao.NoPkTableDaoTest \
    examples.dao.DepartmentManagerTest || fail=1

run_sample "$ENVDIR" "$ENVLABEL" "$DIALECT" "$DRIVER" "$URL" "$USER" "$PASS" \
    s2dao-tiger "$TIGER_JAVA" "$TIGER_SQL" s2dao-tiger-h2.dicon \
    examples.dao.tiger.EmployeeDaoTest examples.dao.tiger.DepartmentDaoTest \
    examples.dao.tiger.EmployeeAutoDaoTest examples.dao.tiger.Employee2DaoTest || fail=1

echo "==== OLD-ENV overall exit=$fail ===="
exit $fail
