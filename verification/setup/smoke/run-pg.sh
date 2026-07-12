#!/usr/bin/env bash
#
# S2Dao runtime smoke test on PostgreSQL 16 ("new environment").
# Requires the cluster + database created by verification/setup/pg-setup.sh.
# Must be run with JDK8.
#
set -euo pipefail

# the smoke MUST run on JDK8 (Java5-compat -source 1.6). Pin it, ignoring any ambient JDK21 JAVA_HOME.
JDK8_HOME="${JDK8_HOME:-/usr/lib/jvm/java-8-openjdk-amd64}"
export JAVA_HOME="$JDK8_HOME"
export PATH="$JAVA_HOME/bin:$PATH"
export JAVA_TOOL_OPTIONS=""

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../../.." && pwd)"
LIB="$ROOT/verification/lib"
EX="$ROOT/verification/samples/s2dao/s2-dao-examples/src/main/java"
EXRES="$ROOT/verification/samples/s2dao/s2-dao-examples/src/main/resources/examples/dao"
BUILD="$HERE/build"

CP="$(find "$LIB" -maxdepth 1 -name '*.jar' | tr '\n' ':')"

rm -rf "$BUILD"; mkdir -p "$BUILD/examples/dao"
javac -encoding UTF-8 -source 1.6 -target 1.6 -nowarn -cp "$CP" -d "$BUILD" \
  "$EX/examples/dao/EmployeeDao.java" \
  "$EX/examples/dao/Employee.java" \
  "$EX/examples/dao/Department.java" \
  "$EX/examples/dao/EmployeeSearchCondition.java"
javac -encoding UTF-8 -source 1.6 -target 1.6 -nowarn -cp "$CP:$BUILD" -d "$BUILD" "$HERE/SmokeTest.java"
cp "$EXRES"/*.sql "$BUILD/examples/dao/"
cp "$HERE/dicon/app-pg.dicon" "$BUILD/"

java -cp "$CP:$BUILD" SmokeTest app-pg.dicon "$HERE/ddl/emp-dept-oracle.sql" "PostgreSQL16"
