#!/usr/bin/env bash
#
# S2Dao runtime smoke test on H2 (Oracle compatibility mode = "old environment" surrogate).
# Must be run with JDK8.
#
set -euo pipefail

# the smoke MUST run on JDK8 (Java5-compat -source 1.6). Pin it, ignoring any ambient JDK21 JAVA_HOME.
JDK8_HOME="${JDK8_HOME:-/usr/lib/jvm/java-8-openjdk-amd64}"
export JAVA_HOME="$JDK8_HOME"
export PATH="$JAVA_HOME/bin:$PATH"
export JAVA_TOOL_OPTIONS=""   # keep smoke output clean (no proxy noise); smoke is offline

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../../.." && pwd)"
LIB="$ROOT/verification/lib"
EX="$ROOT/samples/s2dao/s2-dao-examples/src/main/java"
EXRES="$ROOT/samples/s2dao/s2-dao-examples/src/main/resources/examples/dao"
BUILD="$HERE/build"

# runtime classpath = every jar under verification/lib (postgresql jar is harmless for H2)
CP="$(find "$LIB" -maxdepth 1 -name '*.jar' | tr '\n' ':')"

rm -rf "$BUILD"; mkdir -p "$BUILD/examples/dao"
# compile the reference entity/DAO classes (constant-annotation style) + the smoke driver
javac -encoding UTF-8 -source 1.6 -target 1.6 -nowarn -cp "$CP" -d "$BUILD" \
  "$EX/examples/dao/EmployeeDao.java" \
  "$EX/examples/dao/Employee.java" \
  "$EX/examples/dao/Department.java" \
  "$EX/examples/dao/EmployeeSearchCondition.java"
javac -encoding UTF-8 -source 1.6 -target 1.6 -nowarn -cp "$CP:$BUILD" -d "$BUILD" "$HERE/SmokeTest.java"
# the .sql (2-way SQL / explicit SQL) files must sit on the classpath next to the DAO
cp "$EXRES"/*.sql "$BUILD/examples/dao/"
# the dicon must be on the classpath root
cp "$HERE/dicon/app-h2.dicon" "$BUILD/"

java -cp "$CP:$BUILD" SmokeTest app-h2.dicon "$HERE/ddl/emp-dept-oracle.sql" "H2-Oracle-mode"
