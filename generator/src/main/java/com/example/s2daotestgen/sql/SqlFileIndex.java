package com.example.s2daotestgen.sql;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * 2-way SQL ファイル(.sql)をベース名で索引する。
 *
 * <p>
 * S2Dao の規約 {@code <DaoName>_<methodName>[<dbmsSuffix>].sql} に従い、ファイル名の
 * 拡張子を除いたベース名で検索できるようにする。ディレクトリは再帰走査する。
 * </p>
 */
public final class SqlFileIndex {

    private final Map<String, File> files = new HashMap<String, File>();

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
                final String base = c.getName().substring(0,
                        c.getName().length() - 4);
                // 同名は最初に見つかったものを優先(先勝ち)
                if (!files.containsKey(base)) {
                    files.put(base, c);
                }
            }
        }
    }

    public File find(final String baseName) {
        return files.get(baseName);
    }

    public String read(final File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), Charset.forName("UTF-8"));
    }
}
