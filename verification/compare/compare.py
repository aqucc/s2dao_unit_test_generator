#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
新旧環境のエビデンス CSV(DAO 戻り値・操作後データセット)を突き合わせる。

前提:
- 各 CSV は testsupport の EvidenceWriter が正規化済み(カラム名は大文字、数値は正準形、
  日付は yyyy-MM-dd HH:mm:ss、NULL はリテラル "NULL")。よって正規化後は素の文字列比較でよい。
- S2Dao が自動更新するカラム(タイムスタンプ列・バージョン列)は実行時刻や更新回数で
  値が変わりうるため比較から除外する(--exclude、既定は TSTAMP/TIMESTAMP/VERSIONNO/VERSION_NO)。

使い方:
    compare.py <old_evidence_root> <new_evidence_root> [--exclude COL,COL,...]
戻り値: 全ファイル一致で 0、不一致があれば 1。
"""
import csv
import os
import sys

DEFAULT_EXCLUDE = ["TSTAMP", "TIMESTAMP", "VERSIONNO", "VERSION_NO"]


def read_csv(path):
    with open(path, newline="", encoding="utf-8") as f:
        rows = list(csv.reader(f))
    if not rows:
        return [], []
    header = rows[0]
    return header, rows[1:]


def project(header, rows, exclude):
    keep_idx = [i for i, c in enumerate(header) if c.upper() not in exclude]
    kept_header = [header[i] for i in keep_idx]
    kept_rows = []
    for r in rows:
        kept_rows.append([r[i] if i < len(r) else "" for i in keep_idx])
    return kept_header, kept_rows


def compare_file(old_path, new_path, exclude):
    oh, orows = read_csv(old_path)
    nh, nrows = read_csv(new_path)
    oh2, orows2 = project(oh, orows, exclude)
    nh2, nrows2 = project(nh, nrows, exclude)
    diffs = []
    if oh2 != nh2:
        diffs.append("header: OLD=%s NEW=%s" % (oh2, nh2))
    if len(orows2) != len(nrows2):
        diffs.append("row count: OLD=%d NEW=%d" % (len(orows2), len(nrows2)))
    for i in range(min(len(orows2), len(nrows2))):
        if orows2[i] != nrows2[i]:
            diffs.append("row %d: OLD=%s NEW=%s" % (i, orows2[i], nrows2[i]))
    return diffs


def main():
    if len(sys.argv) < 3:
        print("usage: compare.py <old_root> <new_root> [--exclude COL,COL]")
        return 2
    old_root, new_root = sys.argv[1], sys.argv[2]
    exclude = set(DEFAULT_EXCLUDE)
    if "--exclude" in sys.argv:
        idx = sys.argv.index("--exclude")
        exclude = set(c.strip().upper() for c in sys.argv[idx + 1].split(",") if c.strip())

    print("== エビデンス比較 ==")
    print("  OLD: %s" % old_root)
    print("  NEW: %s" % new_root)
    print("  除外カラム: %s" % ", ".join(sorted(exclude)))
    print("")

    samples = sorted(d for d in os.listdir(old_root)
                     if os.path.isdir(os.path.join(old_root, d)))
    total_compared = total_match = total_mismatch = total_only = 0

    for sample in samples:
        old_dir = os.path.join(old_root, sample)
        new_dir = os.path.join(new_root, sample)
        old_files = set(os.listdir(old_dir)) if os.path.isdir(old_dir) else set()
        new_files = set(os.listdir(new_dir)) if os.path.isdir(new_dir) else set()
        common = sorted(old_files & new_files)
        only_old = sorted(old_files - new_files)
        only_new = sorted(new_files - old_files)

        s_match = s_mismatch = 0
        mismatch_detail = []
        for fn in common:
            diffs = compare_file(os.path.join(old_dir, fn),
                                 os.path.join(new_dir, fn), exclude)
            if diffs:
                s_mismatch += 1
                mismatch_detail.append((fn, diffs))
            else:
                s_match += 1

        print("[%s] 比較ファイル=%d 一致=%d 不一致=%d 片側のみ(OLD=%d, NEW=%d)"
              % (sample, len(common), s_match, s_mismatch, len(only_old), len(only_new)))
        for fn in only_old:
            print("    OLD にのみ存在: %s" % fn)
        for fn in only_new:
            print("    NEW にのみ存在: %s" % fn)
        for fn, diffs in mismatch_detail:
            print("    不一致: %s" % fn)
            for d in diffs:
                print("        - %s" % d)

        total_compared += len(common)
        total_match += s_match
        total_mismatch += s_mismatch
        total_only += len(only_old) + len(only_new)

    print("")
    print("== 合計: 比較=%d 一致=%d 不一致=%d 片側のみ=%d =="
          % (total_compared, total_match, total_mismatch, total_only))
    ok = (total_mismatch == 0 and total_only == 0)
    print("== 判定: %s ==" % ("一致(PASS)" if ok else "不一致あり(FAIL)"))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
