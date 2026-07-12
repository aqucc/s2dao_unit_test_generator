#!/usr/bin/env bash
#
# 生成テストの実行共通ロジック(run-old-env.sh / run-new-env.sh から source して使う)。
#
# 旧環境相当 = H2 Oracle互換モード、新環境 = PostgreSQL16。いずれも JDK8 で
#   1) sample の entity/DAO ソース + 生成テスト + ランナーを -source 1.5 でコンパイル
#      (生成テストは Java5 互換。バイトコード major49 = Java5 世代)
#   2) .sql / dicon / s2daotest.properties をクラスパスに配置
#   3) RunGeneratedTests(DDL 流し込み + JUnit3 実行)
# を行う。エビデンス CSV と実行ログを verification/results/<envdir>/ に残す。
set -euo pipefail

JDK8_HOME="${JDK8_HOME:-/usr/lib/jvm/java-8-openjdk-amd64}"
export JAVA_HOME="$JDK8_HOME"
export PATH="$JAVA_HOME/bin:$PATH"
export JAVA_TOOL_OPTIONS=""   # ログを汚さない(オフライン実行)

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LIB="$ROOT/verification/lib"
DDL="$ROOT/verification/run/ddl/schema.sql"
RUNNER="$ROOT/verification/run/runner/RunGeneratedTests.java"
SUPPORT_JAR="$ROOT/runtime/testsupport/target/s2dao-testgen-support.jar"
CP_LIB="$(find "$LIB" -maxdepth 1 -name '*.jar' | tr '\n' ':')"

# run_sample <envdir> <envlabel> <dialect> <jdbc.driver> <jdbc.url> <jdbc.user> <jdbc.password> \
#            <sampleName> <sampleJavaDir> <sampleSqlResourceDir> <dicon> <TestClass...>
run_sample() {
    local envdir="$1"; shift
    local envlabel="$1"; shift
    local dialect="$1"; shift
    local jdriver="$1"; shift
    local jurl="$1"; shift
    local juser="$1"; shift
    local jpass="$1"; shift
    local sample="$1"; shift
    local javadir="$1"; shift
    local sqldir="$1"; shift
    local dicon="$1"; shift
    local testclasses=("$@")

    local gendir="$ROOT/verification/generated/$sample"
    local build="$ROOT/verification/results/$envdir/build/$sample"
    local evdir="$ROOT/verification/results/$envdir/evidence/$sample"
    local logf="$ROOT/verification/results/$envdir/log/${sample}.log"
    rm -rf "$build" "$evdir"
    mkdir -p "$build" "$evdir" "$(dirname "$logf")"

    # 生成テストの .java 一覧(実行対象クラスに対応するファイル)
    local testfiles=()
    local tc classonly pkgpath
    for tc in "${testclasses[@]}"; do
        classonly="${tc%%!*}"          # 除外指定 "Class!method" の method 部を落とす
        pkgpath="${classonly//.//}"
        testfiles+=("$gendir/${pkgpath}.java")
    done

    # 1) コンパイル(sample ソースは -sourcepath 経由で必要分のみ取り込む)
    javac -encoding UTF-8 -source 1.5 -target 1.5 -nowarn \
        -cp "$CP_LIB:$SUPPORT_JAR" \
        -sourcepath "$javadir:$gendir" \
        -d "$build" \
        "$RUNNER" "${testfiles[@]}" 2>&1 | tee "$logf"

    # 2) リソース配置: .sql を DAO と同じパッケージパスへ、dicon をクラスパスroot(app.dicon)へ
    if [ -d "$sqldir" ]; then
        ( cd "$sqldir" && find . -name '*.sql' -print0 | while IFS= read -r -d '' f; do
            mkdir -p "$build/$(dirname "$f")"
            cp "$f" "$build/$f"
        done )
    fi
    cp "$ROOT/verification/run/dicon/$dicon" "$build/app.dicon"

    # 3) s2daotest.properties
    local prop="$build/s2daotest.properties"
    {
        echo "jdbc.driver=$jdriver"
        echo "jdbc.url=$jurl"
        echo "jdbc.user=$juser"
        echo "jdbc.password=$jpass"
        echo "dialect=$dialect"
        echo "dicon=app.dicon"
    } > "$prop"

    # 4) 実行
    echo "==== [$envlabel] sample=$sample dialect=$dialect ====" | tee -a "$logf"
    set +e
    java -cp "$CP_LIB:$SUPPORT_JAR:$build" \
        -Ds2daotest.config="$prop" \
        -Ds2daotest.evidence.dir="$evdir" \
        RunGeneratedTests "$DDL" "${testclasses[@]}" 2>&1 | tee -a "$logf"
    local rc=${PIPESTATUS[0]}
    set -e
    echo "==== [$envlabel] sample=$sample exit=$rc ====" | tee -a "$logf"
    return $rc
}
