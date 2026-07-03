package com.example.s2daotestgen.dao;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;

/**
 * 指定ディレクトリ配下の Java ソースを JavaParser で解析し、型宣言を索引する。
 */
public final class SourceRepository {

    /** 単純名 -> 型宣言(同名が複数ある場合は全て保持)。 */
    private final Map<String, List<TypeInfo>> bySimpleName = new HashMap<String, List<TypeInfo>>();

    /** 完全修飾名 -> 型宣言。 */
    private final Map<String, TypeInfo> byFqn = new HashMap<String, TypeInfo>();

    private final List<TypeInfo> allTypes = new ArrayList<TypeInfo>();

    /** 解析対象の型宣言とその所在情報。 */
    public static final class TypeInfo {
        public final TypeDeclaration<?> decl;
        public final String packageName;
        public final String simpleName;
        public final String fqn;
        public final File sourceFile;

        TypeInfo(final TypeDeclaration<?> decl, final String packageName,
                final String simpleName, final File sourceFile) {
            this.decl = decl;
            this.packageName = packageName;
            this.simpleName = simpleName;
            this.fqn = (packageName == null || packageName.isEmpty())
                    ? simpleName : packageName + "." + simpleName;
            this.sourceFile = sourceFile;
        }
    }

    public void addSourceRoot(final File root) throws IOException {
        if (root == null || !root.exists()) {
            return;
        }
        final JavaParser parser = new JavaParser();
        final List<File> javaFiles = new ArrayList<File>();
        collectJavaFiles(root, javaFiles);
        for (final File f : javaFiles) {
            parseFile(parser, f);
        }
    }

    private void parseFile(final JavaParser parser, final File f) throws IOException {
        final ParseResult<CompilationUnit> result =
                parser.parse(Files.newInputStream(f.toPath()));
        if (!result.isSuccessful() || !result.getResult().isPresent()) {
            return;
        }
        final CompilationUnit cu = result.getResult().get();
        final String pkg = cu.getPackageDeclaration().isPresent()
                ? cu.getPackageDeclaration().get().getNameAsString() : "";
        for (final TypeDeclaration<?> type : cu.getTypes()) {
            index(new TypeInfo(type, pkg, type.getNameAsString(), f));
        }
    }

    private void index(final TypeInfo info) {
        allTypes.add(info);
        byFqn.put(info.fqn, info);
        List<TypeInfo> list = bySimpleName.get(info.simpleName);
        if (list == null) {
            list = new ArrayList<TypeInfo>();
            bySimpleName.put(info.simpleName, list);
        }
        list.add(info);
    }

    private void collectJavaFiles(final File dir, final List<File> out) {
        final File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (final File c : children) {
            if (c.isDirectory()) {
                collectJavaFiles(c, out);
            } else if (c.getName().endsWith(".java")) {
                out.add(c);
            }
        }
    }

    public List<TypeInfo> getAllTypes() {
        return allTypes;
    }

    public TypeInfo findByFqn(final String fqn) {
        return byFqn.get(fqn);
    }

    /**
     * 型参照(単純名または FQN)から型宣言を解決する。
     * FQN 一致を優先し、無ければ単純名の最初の一致を返す。
     */
    public TypeInfo resolve(final String typeName) {
        if (typeName == null) {
            return null;
        }
        final TypeInfo fq = byFqn.get(typeName);
        if (fq != null) {
            return fq;
        }
        String simple = typeName;
        final int dot = simple.lastIndexOf('.');
        if (dot >= 0) {
            simple = simple.substring(dot + 1);
        }
        final List<TypeInfo> list = bySimpleName.get(simple);
        if (list != null && !list.isEmpty()) {
            return list.get(0);
        }
        return null;
    }
}
