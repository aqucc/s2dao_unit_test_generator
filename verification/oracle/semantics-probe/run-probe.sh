#!/usr/bin/env bash
#
# Oracle 固有セマンティクスの実動作プローブを H2 Oracle 互換モードで実行する。
#   ・(a) 空文字列 INSERT の扱い(実 Oracle は ''=NULL)
#   ・(b) DATE 列への時刻付き値(実 Oracle DATE は時刻保持・ドライバで戻り型が揺れる)
#   ・(c) NUMBER 精度と BigDecimal 正規化
#   ・(d) null の setNull バインド(ORA-17004 回避)
# testsupport の正規化・比較がこれらをどう扱うかを観測・確認する。
#
# 引数: run-probe.sh [jdbc.url] [driverClass] [user] [password]
#   省略時は H2 Oracle 互換モード(メモリ DB)。
#   PostgreSQL 実 DB での対比観測:
#     run-probe.sh jdbc:postgresql://127.0.0.1:5432/s2daogen org.postgresql.Driver s2dao s2dao
#   実 Oracle では ojdbc jar をクラスパスへ足して:
#     run-probe.sh "jdbc:oracle:thin:@host:1521:SID" oracle.jdbc.OracleDriver scott tiger
#   (SEMPROBE 表を作成・破棄する点に注意)
set -euo pipefail

JDK8_HOME="${JDK8_HOME:-/usr/lib/jvm/java-8-openjdk-amd64}"
export JAVA_HOME="$JDK8_HOME"
export PATH="$JAVA_HOME/bin:$PATH"
export JAVA_TOOL_OPTIONS=""

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../../.." && pwd)"
LIB="$ROOT/verification/lib"
SUPPORT_JAR="$ROOT/runtime/testsupport/target/s2dao-testgen-support.jar"

if [ ! -f "$SUPPORT_JAR" ]; then
    echo "testsupport が未ビルドです: cd testsupport && mvn -q package" >&2
    exit 2
fi

CP="$SUPPORT_JAR:$LIB/h2-1.4.199.jar:$LIB/postgresql-42.2.27.jar"
BUILD="$HERE/build"
rm -rf "$BUILD"; mkdir -p "$BUILD"

# 生成テストと同じ Java5 世代でコンパイルする(-source/-target 1.5, major49)
javac -encoding UTF-8 -source 1.5 -target 1.5 -nowarn -cp "$CP" -d "$BUILD" \
    "$HERE/OracleSemanticsProbe.java"

# -Dfile.encoding=UTF-8: 観測メッセージ(日本語)を UTF-8 コンソールで文字化けさせない
java -Dfile.encoding=UTF-8 -cp "$CP:$BUILD" OracleSemanticsProbe "$@"
