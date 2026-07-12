#!/usr/bin/env bash
#
# 新旧環境のエビデンス CSV とJUnit実行結果を突き合わせる。
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../../.." && pwd)"

echo "########## JUnit 実行結果(両環境) ##########"
for env in old-env new-env; do
    echo "== $env =="
    for f in "$ROOT/verification/results/$env/log/"*.log; do
        [ -e "$f" ] || continue
        line="$(grep -E '==== RESULT' "$f" | tail -1 || true)"
        echo "  $(basename "$f" .log): $line"
    done
done
echo ""

echo "########## エビデンス CSV 比較 ##########"
python3 "$HERE/compare.py" \
    "$ROOT/verification/results/old-env/evidence" \
    "$ROOT/verification/results/new-env/evidence" \
    "$@"
