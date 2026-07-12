#!/bin/sh
# =============================================================================
# 生成テストコードの -source 1.5 コンパイル検証スクリプト(フェーズ2の統合検証)
#
# samples/s2dao/s2-dao-examples と samples/s2dao-tiger/s2-dao-tiger-examples に
# 対して gen-all を実行し、生成された <Dao>Test.java を JDK8 の
#   javac -source 1.5 -target 1.5
# で junit-3.8.2 + testsupport + サンプルの dao/entity ソースと共にコンパイルし、
# エラーゼロであることを確認する。
# (JDK8 の javac は -source/-target 1.5 を obsolete 警告付きで受理し、
#  バイトコード major version 49 = Java5 を生成する。Java5 実VMで動作する世代。)
#
# 実行(DB 接続)までは行わない。それは後続の検証フェーズが担当する。
#
# 使い方:
#   scripts/verify-generated-compile.sh
#
# 環境変数(省略可):
#   JDK8_HOME    JDK8 の場所 (既定 /usr/lib/jvm/java-8-openjdk-amd64)
#   S2_LIB_DIR   Seasar2/S2Dao ランタイム jar のディレクトリ
#                (既定 verification/lib。サンプル DAO ソースのコンパイルに必要)
#   WORK_DIR     作業ディレクトリ (既定 <repo>/build/verify-generated)
# =============================================================================
set -u

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
JDK8_HOME=${JDK8_HOME:-/usr/lib/jvm/java-8-openjdk-amd64}
JAVAC="$JDK8_HOME/bin/javac"
S2_LIB_DIR=${S2_LIB_DIR:-$ROOT/verification/lib}
WORK_DIR=${WORK_DIR:-$ROOT/build/verify-generated}

fail() {
    echo "NG: $1" >&2
    exit 1
}

[ -x "$JAVAC" ] || fail "JDK8 javac が見つかりません: $JAVAC (JDK8_HOME を指定してください)"

# --- junit 3.8.2 の解決 (~/.m2 → S2_LIB_DIR → mvn dependency:get) ---
JUNIT_JAR="$HOME/.m2/repository/junit/junit/3.8.2/junit-3.8.2.jar"
if [ ! -f "$JUNIT_JAR" ] && [ -f "$S2_LIB_DIR/junit-3.8.2.jar" ]; then
    JUNIT_JAR="$S2_LIB_DIR/junit-3.8.2.jar"
fi
if [ ! -f "$JUNIT_JAR" ]; then
    echo "junit-3.8.2 を取得します..."
    mvn -q dependency:get -Dartifact=junit:junit:3.8.2 || fail "junit 3.8.2 の取得に失敗"
    JUNIT_JAR="$HOME/.m2/repository/junit/junit/3.8.2/junit-3.8.2.jar"
fi
[ -f "$JUNIT_JAR" ] || fail "junit-3.8.2.jar が見つかりません"

# --- Seasar ランタイム jar(サンプル DAO/クライアントソースのコンパイル用) ---
# 生成テスト自体は junit + testsupport + dao/entity にしか依存しないが、
# サンプルの DAO ソース(Tiger アノテーション・AbstractDao 等)のコンパイルに必要。
S2_CP=""
for j in "$S2_LIB_DIR"/*.jar; do
    [ -f "$j" ] || continue
    case "$j" in
        *junit*) ;; # junit は上で解決済み
        *) S2_CP="$S2_CP:$j" ;;
    esac
done
[ -n "$S2_CP" ] || fail "Seasar ランタイム jar が見つかりません: $S2_LIB_DIR (S2_LIB_DIR を指定してください)"

# --- ジェネレーター / testsupport のビルド ---
echo "[1/4] generator をビルド..."
GEN_JAR="$ROOT/generator/target/s2dao-testgen.jar"
if [ ! -f "$GEN_JAR" ]; then
    (cd "$ROOT/generator" && mvn -q package -DskipTests) || fail "generator のビルドに失敗"
fi

echo "[2/4] testsupport をビルド..."
SUP_JAR="$ROOT/runtime/testsupport/target/s2dao-testgen-support.jar"
if [ ! -f "$SUP_JAR" ]; then
    (cd "$ROOT/runtime/testsupport" && mvn -q package -DskipTests) || fail "testsupport のビルドに失敗"
fi

rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR"

TOTAL_ERRORS=0

# ---------------------------------------------------------------------------
# verify <名前> <サンプルsrc> <サンプルsql>
# ---------------------------------------------------------------------------
verify() {
    NAME=$1
    SRC=$2
    SQL=$3
    OUT="$WORK_DIR/$NAME"
    CLASSES="$OUT/classes"
    mkdir -p "$CLASSES"

    echo ""
    echo "=== [$NAME] gen-all 実行 ==="
    java -jar "$GEN_JAR" gen-all --src "$SRC" --sql "$SQL" \
        --out "$OUT/gen" --dbms oracle || fail "[$NAME] gen-all に失敗"

    GENERATED=$(find "$OUT/gen" -name '*Test.java' | sort)
    NUM_GEN=$(echo "$GENERATED" | grep -c . || true)
    [ "$NUM_GEN" -gt 0 ] || fail "[$NAME] 生成された Test.java がありません"
    echo "[$NAME] 生成テストクラス: $NUM_GEN 件"

    SAMPLE_SOURCES=$(find "$SRC" -name '*.java' | sort)

    echo "=== [$NAME] javac -source 1.5 -target 1.5 でコンパイル ==="
    # 注: -source/-target 1.5 では bootclasspath 未指定 / obsolete 警告が出るが許容(CONSTRAINTS.md)
    "$JAVAC" -source 1.5 -target 1.5 -encoding UTF-8 \
        -cp "$JUNIT_JAR:$SUP_JAR$S2_CP" \
        -d "$CLASSES" \
        $SAMPLE_SOURCES $GENERATED 2> "$OUT/javac.log"
    RC=$?
    ERRORS=$(grep -c ' error:' "$OUT/javac.log" || true)
    if [ $RC -ne 0 ] || [ "$ERRORS" -gt 0 ]; then
        echo "[$NAME] コンパイルエラー ($ERRORS 件):"
        grep ' error:' "$OUT/javac.log" | head -20
        TOTAL_ERRORS=$((TOTAL_ERRORS + ERRORS + 1))
        return
    fi
    NUM_CLASS=$(find "$CLASSES" -name '*Test.class' | wc -l)
    echo "[$NAME] OK: エラーゼロ (Test クラス $NUM_CLASS 件を含む全ソースをコンパイル)"

    # 生成物のバイトコード世代を確認 (major 49 = Java 5)
    ONE=$(find "$CLASSES" -name '*Test.class' | head -1)
    MAJOR=$("$JDK8_HOME/bin/javap" -verbose -cp "$CLASSES" \
        "$(echo "$ONE" | sed "s|$CLASSES/||; s|\.class$||; s|/|.|g")" \
        | grep 'major version' | awk '{print $3}')
    echo "[$NAME] バイトコード major version: $MAJOR (49 = -target 1.5 = Java5)"
    if [ "$MAJOR" != "49" ]; then
        echo "[$NAME] 警告: 期待した major version 49 と異なります: $MAJOR" >&2
    fi
}

echo "[3/4] s2dao (定数アノテーション) サンプルを検証..."
verify s2dao \
    "$ROOT/verification/samples/s2dao/s2-dao-examples/src/main/java" \
    "$ROOT/verification/samples/s2dao/s2-dao-examples/src/main/resources"

echo "[4/4] s2dao-tiger (Tiger アノテーション) サンプルを検証..."
verify tiger \
    "$ROOT/verification/samples/s2dao-tiger/s2-dao-tiger-examples/src/main/java" \
    "$ROOT/verification/samples/s2dao-tiger/s2-dao-tiger-examples/src/main/resources"

echo "[+] servicebase (ServiceBase 具象 Service / JPA 風エンティティ) フィクスチャを検証..."
verify servicebase \
    "$ROOT/verification/samples/servicebase/src/main/java" \
    "$ROOT/verification/samples/servicebase/src/main/resources"

echo ""
if [ "$TOTAL_ERRORS" -eq 0 ]; then
    echo "==== 検証 OK: 両サンプルとも生成テストが -source 1.5 でエラーゼロでコンパイルできました ===="
    exit 0
else
    echo "==== 検証 NG: コンパイルエラーがあります ($TOTAL_ERRORS) ===="
    exit 1
fi
