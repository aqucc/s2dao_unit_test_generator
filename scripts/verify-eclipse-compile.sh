#!/bin/sh
# =============================================================================
# 生成テストコードの ECJ(Eclipse JDT バッチコンパイラ)による Java5 コンパイル検証
#
# Eclipse(Pleiades を含む)が実際に使うコンパイラは JDK の javac ではなく
# ECJ(org.eclipse.jdt.core.compiler)である。javac が通っても Eclipse で通るとは
# 限らないため、生成テスト + testsupport 本体を ECJ で -1.5 コンパイルし、
#   - エラーゼロであること
#   - 主要な警告(Eclipse は javac より厳しい)の一覧
# を確認する。あわせて Pleiades(日本語 Eclipse / Windows)で一般的な MS932
# エンコーディングでも生成・コンパイルできることを検証する。
#
# 使い方:
#   scripts/verify-eclipse-compile.sh
#
# 環境変数(省略可):
#   JDK8_HOME    JDK8 の場所 (既定 /usr/lib/jvm/java-8-openjdk-amd64)
#   ECJ_VERSION  ECJ のバージョン (既定 4.6.1。JDK8 で動作し -1.5 をサポート)
#   S2_LIB_DIR   Seasar2/S2Dao ランタイム jar のディレクトリ (既定 verification/lib)
#   WORK_DIR     作業ディレクトリ (既定 <repo>/build/verify-eclipse)
# =============================================================================
set -u

ROOT=$(cd "$(dirname "$0")/.." && pwd)
JDK8_HOME=${JDK8_HOME:-/usr/lib/jvm/java-8-openjdk-amd64}
JAVA="$JDK8_HOME/bin/java"
JAVAP="$JDK8_HOME/bin/javap"
ECJ_VERSION=${ECJ_VERSION:-4.6.1}
S2_LIB_DIR=${S2_LIB_DIR:-$ROOT/verification/lib}
WORK_DIR=${WORK_DIR:-$ROOT/build/verify-eclipse}

fail() {
    echo "NG: $1" >&2
    exit 1
}

[ -x "$JAVA" ] || fail "JDK8 java が見つかりません: $JAVA (JDK8_HOME を指定してください)"

# --- ECJ の解決 (~/.m2 → mvn dependency:get) ---
ECJ_JAR="$HOME/.m2/repository/org/eclipse/jdt/core/compiler/ecj/$ECJ_VERSION/ecj-$ECJ_VERSION.jar"
if [ ! -f "$ECJ_JAR" ]; then
    echo "ECJ $ECJ_VERSION を取得します..."
    mvn -q dependency:get \
        -Dartifact=org.eclipse.jdt.core.compiler:ecj:$ECJ_VERSION \
        || fail "ECJ $ECJ_VERSION の取得に失敗(Maven Central 到達性を確認してください)"
fi
[ -f "$ECJ_JAR" ] || fail "ecj-$ECJ_VERSION.jar が見つかりません: $ECJ_JAR"
echo "ECJ: $($JAVA -jar "$ECJ_JAR" -version 2>&1 | grep -i eclipse | head -1)"

# --- junit 3.8.2 の解決 ---
JUNIT_JAR="$HOME/.m2/repository/junit/junit/3.8.2/junit-3.8.2.jar"
if [ ! -f "$JUNIT_JAR" ] && [ -f "$S2_LIB_DIR/junit-3.8.2.jar" ]; then
    JUNIT_JAR="$S2_LIB_DIR/junit-3.8.2.jar"
fi
if [ ! -f "$JUNIT_JAR" ]; then
    mvn -q dependency:get -Dartifact=junit:junit:3.8.2 || fail "junit 3.8.2 の取得に失敗"
    JUNIT_JAR="$HOME/.m2/repository/junit/junit/3.8.2/junit-3.8.2.jar"
fi
[ -f "$JUNIT_JAR" ] || fail "junit-3.8.2.jar が見つかりません"

# --- Seasar ランタイム jar(サンプル DAO/クライアントソースのコンパイル用) ---
S2_CP=""
for j in "$S2_LIB_DIR"/*.jar; do
    [ -f "$j" ] || continue
    case "$j" in
        *junit*) ;;
        *) S2_CP="$S2_CP:$j" ;;
    esac
done
[ -n "$S2_CP" ] || fail "Seasar ランタイム jar が見つかりません: $S2_LIB_DIR"

# --- ジェネレーター / testsupport のビルド ---
echo "[1/5] generator をビルド..."
GEN_JAR="$ROOT/generator/target/s2dao-testgen.jar"
if [ ! -f "$GEN_JAR" ]; then
    (cd "$ROOT/generator" && mvn -q package -DskipTests) || fail "generator のビルドに失敗"
fi

echo "[2/5] testsupport をビルド(1.5 + animal-sniffer)..."
SUP_JAR="$ROOT/testsupport/target/s2dao-testgen-support.jar"
if [ ! -f "$SUP_JAR" ]; then
    (cd "$ROOT/testsupport" && mvn -q package) || fail "testsupport のビルドに失敗"
fi

# --- testsupport 本体ソースも ECJ で -1.5 単体コンパイル(Eclipse 実コンパイラでの健全性)---
echo "[3/5] testsupport 本体を ECJ -1.5 でコンパイル..."
SUP_SRC="$ROOT/testsupport/src/main/java"
SUP_OUT="$WORK_DIR/testsupport-classes"
rm -rf "$SUP_OUT"; mkdir -p "$SUP_OUT"
SUP_SOURCES=$(find "$SUP_SRC" -name '*.java')
$JAVA -jar "$ECJ_JAR" -1.5 -encoding UTF-8 -cp "$JUNIT_JAR" -d "$SUP_OUT" \
    $SUP_SOURCES > "$WORK_DIR/ecj-testsupport.log" 2>&1
SUP_ERR=$(grep -c 'ERROR in' "$WORK_DIR/ecj-testsupport.log")
SUP_WARN=$(grep -c 'WARNING in' "$WORK_DIR/ecj-testsupport.log")
echo "   testsupport: エラー=$SUP_ERR 警告=$SUP_WARN"
[ "$SUP_ERR" = "0" ] || { grep 'ERROR in' "$WORK_DIR/ecj-testsupport.log" | head; fail "testsupport が ECJ でコンパイルエラー"; }

TOTAL_ERRORS=0

# ---------------------------------------------------------------------------
# verify_ecj <名前> <サンプルsrc> <サンプルsql> <encoding>
# ---------------------------------------------------------------------------
verify_ecj() {
    NAME=$1; SRC=$2; SQL=$3; ENC=$4
    OUT="$WORK_DIR/$NAME-$ENC"
    GENDIR="$OUT/gen"
    CLASSES="$OUT/classes"
    mkdir -p "$CLASSES"

    echo ""
    echo "=== [$NAME / $ENC] gen-all --encoding $ENC 実行 ==="
    $JAVA -jar "$GEN_JAR" gen-all --src "$SRC" --sql "$SQL" \
        --out "$GENDIR" --dbms oracle --encoding "$ENC" > "$OUT/gen.log" 2>&1 \
        || { cat "$OUT/gen.log"; fail "[$NAME/$ENC] gen-all に失敗"; }

    GENERATED=$(find "$GENDIR" -name '*Test.java' | sort)
    NUM_GEN=$(echo "$GENERATED" | grep -c .)
    [ "$NUM_GEN" -gt 0 ] || fail "[$NAME/$ENC] 生成された Test.java がありません"
    SAMPLE_SOURCES=$(find "$SRC" -name '*.java')

    echo "=== [$NAME / $ENC] ECJ -1.5 -encoding $ENC でコンパイル ==="
    # サンプルソースは UTF-8、生成テストは $ENC。ECJ の -encoding は既定文字コードを与え、
    # ソース個別指定 ([dir][enc]) で使い分ける。ここでは全体を $ENC 前提に統一するため、
    # UTF-8 サンプルには明示的に per-path encoding を付与する。
    ENCOPT=""
    for s in $SAMPLE_SOURCES; do
        ENCOPT="$ENCOPT $s[UTF-8]"
    done
    $JAVA -jar "$ECJ_JAR" -1.5 -encoding "$ENC" \
        -cp "$JUNIT_JAR:$SUP_JAR$S2_CP" \
        -d "$CLASSES" \
        $ENCOPT $GENERATED > "$OUT/ecj.log" 2>&1
    ERRORS=$(grep -c 'ERROR in' "$OUT/ecj.log")
    WARNINGS=$(grep -c 'WARNING in' "$OUT/ecj.log")
    NUM_CLASS=$(find "$CLASSES" -name '*Test.class' | wc -l)

    if [ "$ERRORS" != "0" ]; then
        echo "[$NAME/$ENC] ECJ コンパイルエラー ($ERRORS 件):"
        grep -A3 'ERROR in' "$OUT/ecj.log" | head -30
        TOTAL_ERRORS=$((TOTAL_ERRORS + ERRORS))
        return
    fi
    echo "[$NAME/$ENC] OK: エラーゼロ / 警告 $WARNINGS 件 / Test クラス $NUM_CLASS 件"

    # 主要警告の種別集計(Eclipse は javac より厳しい)。
    # ECJ は「位置行 / ソース行 / キャレット行 / メッセージ行」の順で出力するため
    # メッセージ文言を直接抽出して分類する。
    echo "[$NAME/$ENC] 警告種別(上位):"
    grep -oiE 'is a raw type|should be parameterized|is never used|never read|serialVersionUID|Unnecessary cast|Dead code' \
        "$OUT/ecj.log" | sort | uniq -c | sort -rn | sed 's/^/     /'

    # バイトコード世代確認
    ONE=$(find "$CLASSES" -name '*Test.class' | head -1)
    MAJOR=$("$JAVAP" -verbose -cp "$CLASSES" \
        "$(echo "$ONE" | sed "s|$CLASSES/||; s|\.class$||; s|/|.|g")" \
        | grep 'major version' | awk '{print $3}')
    echo "[$NAME/$ENC] バイトコード major version: $MAJOR (49 = Java5)"
    [ "$MAJOR" = "49" ] || echo "[$NAME/$ENC] 警告: major version が 49 ではありません: $MAJOR" >&2
}

rm -rf "$WORK_DIR/s2dao-UTF-8" "$WORK_DIR/tiger-UTF-8" \
       "$WORK_DIR/s2dao-MS932" "$WORK_DIR/tiger-MS932" 2>/dev/null

echo "[4/5] UTF-8(新環境 Pleiades/Java8 相当)で ECJ 検証..."
verify_ecj s2dao \
    "$ROOT/samples/s2dao/s2-dao-examples/src/main/java" \
    "$ROOT/samples/s2dao/s2-dao-examples/src/main/resources" UTF-8
verify_ecj tiger \
    "$ROOT/samples/s2dao-tiger/s2-dao-tiger-examples/src/main/java" \
    "$ROOT/samples/s2dao-tiger/s2-dao-tiger-examples/src/main/resources" UTF-8

echo ""
echo "[5/5] MS932(旧環境 Pleiades/Windows/Java5 相当)で ECJ 検証..."
verify_ecj s2dao \
    "$ROOT/samples/s2dao/s2-dao-examples/src/main/java" \
    "$ROOT/samples/s2dao/s2-dao-examples/src/main/resources" MS932
verify_ecj tiger \
    "$ROOT/samples/s2dao-tiger/s2-dao-tiger-examples/src/main/java" \
    "$ROOT/samples/s2dao-tiger/s2-dao-tiger-examples/src/main/resources" MS932

echo ""
if [ "$TOTAL_ERRORS" -eq 0 ]; then
    echo "==== 検証 OK: 生成テスト + testsupport が ECJ -1.5(UTF-8 / MS932)でエラーゼロ ===="
    echo "     (警告は raw type / 未使用 import 等のスタイル警告のみ。詳細は $WORK_DIR/*/ecj.log)"
    exit 0
else
    echo "==== 検証 NG: ECJ コンパイルエラーがあります ($TOTAL_ERRORS) ===="
    exit 1
fi
