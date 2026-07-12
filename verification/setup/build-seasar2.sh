#!/usr/bin/env bash
#
# Reproducible build of the Seasar2 / S2Dao runtime jars used by the generated tests.
# Rebuilds everything under verification/lib/ from source.
#
#   * s2-framework / s2-extension / s2-tiger : built from github.com/seasarorg/seasar2 @ tag Seasar2.4.48
#   * s2-dao / s2-dao-tiger                  : built from the sources bundled in samples/
#
# WHY javac-direct instead of `mvn install`:
#   maven.seasar.org is dead, and the poms reference seasar-only artifacts that are NOT on
#   Maven Central: ognl:2.6.9-patch-20090427, jboss:javassist:3.4.ga, portlet-api:1.0, the old
#   parent pom, etc. Patching all of that is more fragile than compiling the sources directly
#   against Central-equivalent jars. No src/main/java implementation code is modified except a
#   single guarded line (see PATCH below).
#
# WHY a JDBC3 compile-time stub (verification/setup/jdbc3-stub):
#   Seasar2 2.4.x predates JDBC4. Its java.sql-implementing classes (ConnectionWrapperImpl,
#   DataSourceImpl, XADataSourceImpl, ResultSet/Statement wrappers, ...) do not implement the
#   methods that JDBC 4.0/4.1 added to java.sql.* in Java 6/7. The original jars were compiled
#   on JDK5/6; on JDK8 those classes will not compile. We shadow ONLY the ~12 affected java.sql
#   / javax.sql interfaces with JDBC3-era stubs via -Xbootclasspath/p at COMPILE time. At RUNTIME
#   the real JDK8 java.sql is used (the stub jar is never on the runtime classpath); the added
#   JDBC4 methods are simply never called by S2Dao.
#
# Must be run with JDK8.
set -euo pipefail

# build MUST use JDK8. Pin it, ignoring any ambient JDK21 JAVA_HOME.
JDK8_HOME="${JDK8_HOME:-/usr/lib/jvm/java-8-openjdk-amd64}"
export JAVA_HOME="$JDK8_HOME"; export PATH="$JAVA_HOME/bin:$PATH"

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
WORK="${WORK:-$HERE/build-work}"
OUT="$HERE/build-out"
STUBSRC="$HERE/jdbc3-stub/src"
SLIB="$ROOT/verification/samples/s2dao/lib"            # patched-ognl / javassist-3.4 / 2.3.23 jars live here
CLIB="$ROOT/verification/lib"             # Central deps already vendored into the repo
TAG=Seasar2.4.48

mkdir -p "$WORK" "$OUT"

# --- 0. compile the JDBC3 compile-time stub -------------------------------------------------
echo "== [0] compile JDBC3 stub =="
rm -rf "$WORK/stub"; mkdir -p "$WORK/stub"
find "$STUBSRC" -name '*.java' > "$WORK/stub_srcs.txt"
javac -d "$WORK/stub" @"$WORK/stub_srcs.txt"
STUB="$WORK/jdbc3-stub.jar"; jar cf "$STUB" -C "$WORK/stub" .

# --- 1. clone seasar2 @ tag -----------------------------------------------------------------
echo "== [1] clone seasar2 @ $TAG =="
if [ ! -d "$WORK/seasar2/.git" ]; then
  git clone --depth 1 --branch "$TAG" https://github.com/seasarorg/seasar2.git "$WORK/seasar2"
fi
S2="$WORK/seasar2/seasar2"     # s2-framework / s2-extension modules
TG="$WORK/seasar2/s2-tiger"    # s2-tiger module

# --- 2. Central-equivalent compile deps -----------------------------------------------------
# (all already vendored under verification/lib; add poi for s2-extension excel support)
# geronimo-j2ee_1.4_spec supplies javax.servlet / javax.transaction / javax.ejb etc.;
# portlet-api (JSR-168) is separate and needed by org.seasar.framework.container.external.portlet.*
DEPS="$CLIB/geronimo-j2ee_1.4_spec-1.0.jar:$CLIB/geronimo-jta_1.1_spec-1.0.jar:$CLIB/geronimo-ejb_2.1_spec-1.1.jar:$CLIB/aopalliance-1.0.jar:$CLIB/javassist-3.18.1-GA.jar:$CLIB/ognl-2.6.9.jar:$CLIB/commons-logging-1.1.1.jar:$CLIB/junit-3.8.2.jar:$CLIB/poi-3.0-FINAL.jar:$CLIB/portlet-api-1.0.jar"

jarmod() { # jarmod <classesdir> <resourcesdir> <outjar>
  cp -r "$2"/* "$1"/ 2>/dev/null || true
  ( cd "$1" && jar cf "$3" . )
}

# --- 3. s2-framework 2.4.48 -----------------------------------------------------------------
echo "== [3] s2-framework 2.4.48 =="
# PATCH: OgnlRuntime.clearCache() only exists in seasar's patched ognl (2.6.9-patch-20090427),
# which is not on Maven Central. It is a cache clear on container dispose only -> guarded out.
DU="$S2/s2-framework/src/main/java/org/seasar/framework/util/DisposableUtil.java"
sed -i 's|^\([[:space:]]*\)OgnlRuntime.clearCache();|\1// [build-patch] OgnlRuntime.clearCache(); // seasar-patched-ognl-only API, absent from Central ognl 2.6.9|' "$DU"
rm -rf "$WORK/fw"; mkdir -p "$WORK/fw"
# mock/ and unit/ are JUnit test-support packages (mock JDBC objects would need JDBC4 stubs);
# not needed by the DAO runtime.
find "$S2/s2-framework/src/main/java" -name '*.java' | grep -v '/mock/' | grep -v '/unit/' > "$WORK/fw_srcs.txt"
javac -encoding UTF-8 -source 1.6 -target 1.6 -nowarn -cp "$DEPS" -d "$WORK/fw" @"$WORK/fw_srcs.txt"
jarmod "$WORK/fw" "$S2/s2-framework/src/main/resources" "$OUT/s2-framework-2.4.48.jar"

# --- 4. s2-extension 2.4.48 -----------------------------------------------------------------
echo "== [4] s2-extension 2.4.48 =="
rm -rf "$WORK/ext"; mkdir -p "$WORK/ext"
# WAS6* = WebSphere UOW adapters needing proprietary com.ibm.* (unavailable anywhere) -> omitted.
find "$S2/s2-extension/src/main/java" -name '*.java' | grep -v '/mock/' | grep -v '/unit/' | grep -v 'tx/adapter/WAS6' > "$WORK/ext_srcs.txt"
javac -encoding UTF-8 -source 1.6 -target 1.6 -nowarn -Xbootclasspath/p:"$STUB" \
  -cp "$OUT/s2-framework-2.4.48.jar:$DEPS" -d "$WORK/ext" @"$WORK/ext_srcs.txt"
jarmod "$WORK/ext" "$S2/s2-extension/src/main/resources" "$OUT/s2-extension-2.4.48.jar"

# --- 5. s2-tiger 2.4.48 ---------------------------------------------------------------------
echo "== [5] s2-tiger 2.4.48 =="
rm -rf "$WORK/tiger"; mkdir -p "$WORK/tiger"
TLIB="$(find "$TG/lib" -maxdepth 1 -name '*.jar' | tr '\n' ':')"   # bundled geronimo jpa/ejb/annotation specs
# framework/unit/* = JUnit4 test-support (S2TigerTestCase) depending on the omitted mock/unit.
find "$TG/src/main/java" -name '*.java' | grep -v '/framework/unit/' > "$WORK/tiger_srcs.txt"
javac -encoding UTF-8 -source 1.6 -target 1.6 -nowarn -Xbootclasspath/p:"$STUB" \
  -cp "$OUT/s2-framework-2.4.48.jar:$OUT/s2-extension-2.4.48.jar:$DEPS:$TLIB" -d "$WORK/tiger" @"$WORK/tiger_srcs.txt"
jarmod "$WORK/tiger" "$TG/src/main/resources" "$OUT/s2-tiger-2.4.48.jar"

# --- 6. s2-dao 1.0.52 (targets s2-extension 2.3.23 API) -------------------------------------
echo "== [6] s2-dao 1.0.52 =="
# NOTE: s2-dao 1.0.52 (its pom declares s2-extension 2.3.23) is built and RUN against the
# 2.3.23 stack. Its ValueType/procedure APIs diverge from 2.4.x, so the 2.4.48 jars above are
# archival only (see RUNTIME_BUILD.md).
DAO="$ROOT/verification/samples/s2dao/s2-dao"
CP323="$CLIB/s2-framework-2.3.23.jar:$CLIB/s2-extension-2.3.23.jar:$SLIB/ognl-2.6.9-patch-20070624.jar:$SLIB/javassist-3.4.ga.jar:$CLIB/commons-logging-1.1.1.jar:$CLIB/geronimo-j2ee_1.4_spec-1.0.jar:$CLIB/aopalliance-1.0.jar:$CLIB/poi-3.0-FINAL.jar:$CLIB/junit-3.8.2.jar"
rm -rf "$WORK/dao"; mkdir -p "$WORK/dao"
find "$DAO/src/main/java" -name '*.java' | grep -v '/unit/' > "$WORK/dao_srcs.txt"
javac -encoding UTF-8 -source 1.6 -target 1.6 -nowarn -Xbootclasspath/p:"$STUB" -cp "$CP323" -d "$WORK/dao" @"$WORK/dao_srcs.txt"
jarmod "$WORK/dao" "$DAO/src/main/resources" "$OUT/s2-dao-1.0.52.jar"

# --- 7. s2-dao-tiger 1.0.52 -----------------------------------------------------------------
echo "== [7] s2-dao-tiger 1.0.52 =="
DT="$ROOT/verification/samples/s2dao-tiger/s2-dao-tiger"
rm -rf "$WORK/daotiger"; mkdir -p "$WORK/daotiger"
find "$DT/src/main/java" -name '*.java' | grep -v '/dao/unit/' > "$WORK/dt_srcs.txt"
javac -encoding UTF-8 -source 1.6 -target 1.6 -nowarn -Xbootclasspath/p:"$STUB" \
  -cp "$CP323:$OUT/s2-dao-1.0.52.jar" -d "$WORK/daotiger" @"$WORK/dt_srcs.txt"
jarmod "$WORK/daotiger" "$DT/src/main/resources" "$OUT/s2-dao-tiger-1.0.52.jar"

echo
echo "Build complete. Jars in: $OUT"
ls -la "$OUT"
echo
echo "Runtime stack = s2-framework/extension 2.3.23 (prebuilt in samples) + s2-dao(-tiger) 1.0.52."
echo "The 2.4.48 jars are archival; copy \$OUT/*1.0.52*.jar over verification/lib/ to refresh the runtime."
