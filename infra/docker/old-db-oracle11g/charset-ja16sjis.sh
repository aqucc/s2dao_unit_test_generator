#!/usr/bin/env bash
# =====================================================================
# Oracle XE 11g コンテナの DB キャラクタセットを JA16SJIS 系へ張り替える
#
# 【前提・警告】
#  ・Oracle 非サポート操作(ALTER DATABASE CHARACTER SET INTERNAL_USE)。
#    使い捨てのテスト用コンテナ専用。本番系では絶対に使用しないこと。
#  ・初回起動直後(データ辞書が ASCII のみ、日本語データ投入前)に一度だけ実行する。
#    ASCII 域は AL32UTF8 / JA16SJIS でバイト表現が同一のため、この時点の
#    張り替えなら既存データの不整合は実質発生しない。
#  ・やり直しは docker compose down -v でボリュームごと作り直すこと。
#  ・張り替え後、thin JDBC での接続には ojdbc と同版の orai18n.jar が必要
#    (thin 組み込みの変換は ASCII/ISO8859-1/UTF-8 系のみのため)。
#
# 使い方:
#   docker compose up -d で起動し、healthy になってから
#     ./charset-ja16sjis.sh                  # 既定 JA16SJIS(チルダ無し)
#     ./charset-ja16sjis.sh JA16SJISTILDE    # 旧本番が TILDE 版の場合
#   ※ 旧本番実機で確認して合わせること:
#     SELECT value FROM nls_database_parameters WHERE parameter='NLS_CHARACTERSET';
# =====================================================================
set -euo pipefail

CHARSET="${1:-JA16SJIS}"
CONTAINER="${2:-old-db-oracle11g}"

case "$CHARSET" in
  JA16SJIS|JA16SJISTILDE) ;;
  *) echo "ERROR: 指定できるのは JA16SJIS / JA16SJISTILDE のみ: $CHARSET" >&2; exit 1 ;;
esac

echo "== [$CONTAINER] DB キャラクタセットを $CHARSET へ張り替えます(非サポート操作) =="

docker exec -i "$CONTAINER" bash -c 'sqlplus -s / as sysdba' <<SQL
WHENEVER SQLERROR EXIT SQL.SQLCODE
PROMPT -- 現在のキャラクタセット --
SELECT value FROM nls_database_parameters WHERE parameter = 'NLS_CHARACTERSET';
SHUTDOWN IMMEDIATE
STARTUP RESTRICT
-- ジョブ系プロセスを止めてから張り替える(定石)
ALTER SYSTEM SET JOB_QUEUE_PROCESSES=0;
ALTER SYSTEM SET AQ_TM_PROCESSES=0;
ALTER DATABASE CHARACTER SET INTERNAL_USE ${CHARSET};
SHUTDOWN IMMEDIATE
STARTUP
PROMPT -- 張り替え後のキャラクタセット(必ず確認すること) --
SELECT value FROM nls_database_parameters WHERE parameter = 'NLS_CHARACTERSET';
EXIT
SQL

echo "== 完了。上の SELECT が $CHARSET になっていることを確認してください =="
echo "   接続確認例: 全角5文字が VARCHAR2(10) に入り 6文字目で ORA-12899 になれば"
echo "   SJIS バイトセマンティクスが再現できています。"
echo "   (thin JDBC 側には orai18n.jar の追加を忘れずに)"
