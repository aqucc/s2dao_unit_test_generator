package com.example.s2daotestgen.sql;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 2-way SQL ファイル(.sql)を索引する。
 *
 * <p>
 * S2Dao の規約 {@code <DaoName>_<methodName>[<dbmsSuffix>].sql} に従い、ファイル名の
 * 拡張子を除いたベース名で検索できるようにする(<b>完全一致索引</b>)。あわせて、
 * ファイル名を<b>最初の {@code _}</b> で「クラス名部」と「名前部」に割った
 * <b>逆引き台帳</b>を構築し、大文字小文字のゆらぎを吸収した緩い照合
 * ({@link #findRelaxed(String, String, String)})を可能にする。ディレクトリは再帰走査する。
 * </p>
 */
public final class SqlFileIndex {

    /** ベース名(拡張子なし)完全一致索引。先勝ち。 */
    private final Map<String, File> files = new LinkedHashMap<String, File>();

    /**
     * 逆引き台帳: クラス名部(小文字正規化) → { 名前部(小文字正規化) → File }。
     * ベース名を最初の {@code _} で分割して構築する。{@code _} を含まないベース名は対象外。
     * 同一キー衝突は先勝ち。
     */
    private final Map<String, Map<String, File>> ledger =
            new LinkedHashMap<String, Map<String, File>>();

    /** 走査順を保った全 .sql ファイル。 */
    private final List<File> allFiles = new ArrayList<File>();

    /** DaoAnalyzer が解決に採用したファイル(使用済み)。 */
    private final Set<File> claimed = new LinkedHashSet<File>();

    public void addRoot(final File root) {
        if (root == null || !root.exists()) {
            return;
        }
        scan(root);
    }

    private void scan(final File dir) {
        final File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (final File c : children) {
            if (c.isDirectory()) {
                scan(c);
            } else if (c.getName().endsWith(".sql")) {
                index(c);
            }
        }
    }

    private void index(final File c) {
        allFiles.add(c);
        final String base = c.getName().substring(0, c.getName().length() - 4);
        // 完全一致索引(先勝ち)
        if (!files.containsKey(base)) {
            files.put(base, c);
        }
        // 逆引き台帳(最初の '_' で分割。'_' が無ければ対象外)
        final String[] split = splitOnFirstUnderscore(base);
        if (split == null) {
            return;
        }
        final String classKey = split[0].toLowerCase(Locale.ENGLISH);
        final String nameKey = split[1].toLowerCase(Locale.ENGLISH);
        Map<String, File> byName = ledger.get(classKey);
        if (byName == null) {
            byName = new LinkedHashMap<String, File>();
            ledger.put(classKey, byName);
        }
        if (!byName.containsKey(nameKey)) {
            byName.put(nameKey, c);
        }
    }

    /** ベース名を最初の {@code _} で {クラス名部, 名前部} に割る。{@code _} 無し/空なら null。 */
    public static String[] splitOnFirstUnderscore(final String baseName) {
        if (baseName == null) {
            return null;
        }
        final int u = baseName.indexOf('_');
        if (u <= 0 || u >= baseName.length() - 1) {
            return null;
        }
        return new String[] { baseName.substring(0, u), baseName.substring(u + 1) };
    }

    /** ベース名完全一致で解決する(従来 API)。 */
    public File find(final String baseName) {
        return files.get(baseName);
    }

    /**
     * 逆引き台帳から大文字小文字を無視して緩く解決する。
     *
     * <p>照合順(いずれも ci): クラス名部が {@code classSimpleName} に一致する台帳内で、
     * 名前部 == {@code name + dialectSuffix} → 名前部 == {@code name}。見つからなければ null。</p>
     *
     * <p><b>方言サフィックスの意味論は完全一致解決と同じ</b>: サフィックス付き候補は
     * 「現方言のサフィックス」のみを試す。よって dialect=oracle のとき
     * {@code getCount_hsql} が {@code getCount} にマッチすることはない。</p>
     */
    public File findRelaxed(final String classSimpleName, final String name,
            final String dialectSuffix) {
        if (classSimpleName == null || name == null) {
            return null;
        }
        final Map<String, File> byName =
                ledger.get(classSimpleName.toLowerCase(Locale.ENGLISH));
        if (byName == null) {
            return null;
        }
        final String suffix = (dialectSuffix == null) ? "" : dialectSuffix;
        if (suffix.length() > 0) {
            final File withSuffix = byName.get(
                    (name + suffix).toLowerCase(Locale.ENGLISH));
            if (withSuffix != null) {
                return withSuffix;
            }
        }
        return byName.get(name.toLowerCase(Locale.ENGLISH));
    }

    /** 走査した全 .sql ファイル(走査順)。 */
    public java.util.Collection<File> allFiles() {
        return new ArrayList<File>(allFiles);
    }

    /** 解決に採用したファイルを使用済みとしてマークする。 */
    public void markClaimed(final File f) {
        if (f != null) {
            claimed.add(f);
        }
    }

    /** どの解決経路にも採用されなかった(取りこぼしの疑いがある).sql ファイル。 */
    public Set<File> unclaimedFiles() {
        final Set<File> out = new LinkedHashSet<File>();
        for (final File f : allFiles) {
            if (!claimed.contains(f)) {
                out.add(f);
            }
        }
        return out;
    }

    public String read(final File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), Charset.forName("UTF-8"));
    }
}
