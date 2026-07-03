package com.example.s2daotestgen.dao;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;

/**
 * JavaParser AST から S2Dao 規約の値を取り出すためのユーティリティ。
 */
public final class AstUtil {

    private AstUtil() {
    }

    /** 指定名の static フィールドの文字列リテラル初期値を返す(無ければ null)。 */
    public static String getStaticStringField(final TypeDeclaration<?> type,
            final String fieldName) {
        final VariableDeclarator v = findStaticVariable(type, fieldName);
        if (v == null) {
            return null;
        }
        final Optional<Expression> init = v.getInitializer();
        if (!init.isPresent()) {
            return null;
        }
        return concatStringLiteral(init.get());
    }

    /** 指定名の static フィールドが存在するか(値の型は問わない)。 */
    public static boolean hasStaticField(final TypeDeclaration<?> type,
            final String fieldName) {
        return findStaticVariable(type, fieldName) != null;
    }

    /** 指定名の Class 型 static フィールド(例: BEAN = Foo.class)の型名を返す。 */
    public static String getStaticClassField(final TypeDeclaration<?> type,
            final String fieldName) {
        final VariableDeclarator v = findStaticVariable(type, fieldName);
        if (v == null || !v.getInitializer().isPresent()) {
            return null;
        }
        final Expression e = v.getInitializer().get();
        if (e instanceof ClassExpr) {
            return ((ClassExpr) e).getType().asString();
        }
        return null;
    }

    private static VariableDeclarator findStaticVariable(
            final TypeDeclaration<?> type, final String fieldName) {
        for (final BodyDeclaration<?> member : type.getMembers()) {
            if (!(member instanceof FieldDeclaration)) {
                continue;
            }
            final FieldDeclaration fd = (FieldDeclaration) member;
            if (!fd.isStatic()) {
                continue;
            }
            for (final VariableDeclarator v : fd.getVariables()) {
                if (v.getNameAsString().equals(fieldName)) {
                    return v;
                }
            }
        }
        return null;
    }

    /** "a" + "b" 形式の文字列連結リテラルを結合して返す。非文字列なら null。 */
    public static String concatStringLiteral(final Expression e) {
        if (e instanceof StringLiteralExpr) {
            return ((StringLiteralExpr) e).asString();
        }
        if (e.isBinaryExpr()) {
            final String l = concatStringLiteral(e.asBinaryExpr().getLeft());
            final String r = concatStringLiteral(e.asBinaryExpr().getRight());
            if (l != null && r != null) {
                return l + r;
            }
        }
        return null;
    }

    /** アノテーションの単一 value 文字列(@Sql("...") 等)を返す。 */
    public static String getAnnotationStringValue(
            final NodeWithAnnotations<?> node, final String annName) {
        final AnnotationExpr ann = getAnnotation(node, annName);
        if (ann == null) {
            return null;
        }
        return getAnnotationStringValue(ann, "value");
    }

    /** アノテーションの指定メンバの文字列値を返す。 */
    public static String getAnnotationStringValue(final AnnotationExpr ann,
            final String member) {
        final Expression e = getAnnotationMember(ann, member);
        if (e == null) {
            return null;
        }
        return concatStringLiteral(e);
    }

    /** アノテーションの指定メンバの int 値を返す(見つからなければ defaultValue)。 */
    public static int getAnnotationIntValue(final AnnotationExpr ann,
            final String member, final int defaultValue) {
        final Expression e = getAnnotationMember(ann, member);
        if (e != null && e.isIntegerLiteralExpr()) {
            return e.asIntegerLiteralExpr().asNumber().intValue();
        }
        return defaultValue;
    }

    /** アノテーションの String[] 値(@Arguments({"a","b"}) 等)を返す。 */
    public static List<String> getAnnotationStringArray(
            final NodeWithAnnotations<?> node, final String annName) {
        final AnnotationExpr ann = getAnnotation(node, annName);
        if (ann == null) {
            return null;
        }
        return getAnnotationStringArray(ann, "value");
    }

    public static List<String> getAnnotationStringArray(final AnnotationExpr ann,
            final String member) {
        final Expression e = getAnnotationMember(ann, member);
        if (e == null) {
            return null;
        }
        final List<String> out = new ArrayList<String>();
        if (e instanceof ArrayInitializerExpr) {
            final NodeList<Expression> values = ((ArrayInitializerExpr) e).getValues();
            for (final Expression v : values) {
                final String s = concatStringLiteral(v);
                if (s != null) {
                    out.add(s);
                }
            }
        } else {
            final String s = concatStringLiteral(e);
            if (s != null) {
                out.add(s);
            }
        }
        return out;
    }

    /** メンバ式を取り出す。単一メンバアノテーションは "value" として扱う。 */
    public static Expression getAnnotationMember(final AnnotationExpr ann,
            final String member) {
        if (ann instanceof SingleMemberAnnotationExpr) {
            if ("value".equals(member)) {
                return ((SingleMemberAnnotationExpr) ann).getMemberValue();
            }
            return null;
        }
        if (ann instanceof NormalAnnotationExpr) {
            for (final MemberValuePair p : ((NormalAnnotationExpr) ann).getPairs()) {
                if (p.getNameAsString().equals(member)) {
                    return p.getValue();
                }
            }
        }
        return null;
    }

    /** 単純名でアノテーションを取得(FQN 末尾一致も許容)。 */
    public static AnnotationExpr getAnnotation(final NodeWithAnnotations<?> node,
            final String annName) {
        for (final AnnotationExpr ann : node.getAnnotations()) {
            final String name = ann.getNameAsString();
            if (name.equals(annName) || name.endsWith("." + annName)) {
                return ann;
            }
        }
        return null;
    }

    public static boolean hasAnnotation(final NodeWithAnnotations<?> node,
            final String annName) {
        return getAnnotation(node, annName) != null;
    }
}
