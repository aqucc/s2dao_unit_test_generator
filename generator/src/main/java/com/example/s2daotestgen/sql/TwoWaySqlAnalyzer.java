package com.example.s2daotestgen.sql;

import java.util.ArrayList;
import java.util.List;

import org.seasar.extension.sql.Node;
import org.seasar.extension.sql.node.BeginNode;
import org.seasar.extension.sql.node.BindVariableNode;
import org.seasar.extension.sql.node.ElseNode;
import org.seasar.extension.sql.node.EmbeddedValueNode;
import org.seasar.extension.sql.node.IfNode;
import org.seasar.extension.sql.node.ParenBindVariableNode;
import org.seasar.extension.sql.node.PrefixSqlNode;
import org.seasar.extension.sql.node.SqlNode;
import org.seasar.extension.sql.parser.SqlParserImpl;

import com.example.s2daotestgen.model.MetaModel.BindVarMeta;

/**
 * ベンダリングした S2Container の {@code SqlParserImpl} で 2-way SQL を解析し、
 * バインド変数・IF/BEGIN 条件・埋め込み変数を抽出する。さらに「全条件真」で
 * 展開した SQL(実際に DB に渡る形)を生成する。
 */
public final class TwoWaySqlAnalyzer {

    /** 解析結果。 */
    public static final class Result {
        public final List<BindVarMeta> bindVariables = new ArrayList<BindVarMeta>();
        public final List<String> embeddedVariables = new ArrayList<String>();
        public final List<String> ifConditions = new ArrayList<String>();
        public boolean hasBegin;
        public String expandedSql;
        public final List<String> expandedBindOrder = new ArrayList<String>();
        public boolean twoWay;
    }

    public Result analyze(final String sql) {
        final Result r = new Result();
        final Node root = new SqlParserImpl(sql).parse();
        collect(root, r);
        final ExpandCtx ctx = new ExpandCtx();
        ctx.enabled = true;
        expand(root, ctx);
        r.expandedSql = ctx.sql.toString();
        r.expandedBindOrder.addAll(ctx.binds);
        r.twoWay = !r.bindVariables.isEmpty() || !r.embeddedVariables.isEmpty()
                || !r.ifConditions.isEmpty() || r.hasBegin;
        return r;
    }

    // ---------- 構造抽出 ----------

    private void collect(final Node node, final Result r) {
        if (node instanceof BindVariableNode) {
            r.bindVariables.add(toBind(((BindVariableNode) node).getExpression(), "BIND"));
        } else if (node instanceof ParenBindVariableNode) {
            r.bindVariables.add(toBind(((ParenBindVariableNode) node).getExpression(), "PAREN_BIND"));
        } else if (node instanceof EmbeddedValueNode) {
            r.embeddedVariables.add(((EmbeddedValueNode) node).getExpression());
        } else if (node instanceof IfNode) {
            final IfNode ifn = (IfNode) node;
            r.ifConditions.add(ifn.getExpression());
        } else if (node instanceof BeginNode) {
            r.hasBegin = true;
        }
        for (int i = 0; i < node.getChildSize(); i++) {
            collect(node.getChild(i), r);
        }
        if (node instanceof IfNode) {
            final ElseNode elseNode = ((IfNode) node).getElseNode();
            if (elseNode != null) {
                collect(elseNode, r);
            }
        }
    }

    private BindVarMeta toBind(final String expression, final String kind) {
        final BindVarMeta b = new BindVarMeta();
        b.expression = expression;
        b.kind = kind;
        final int dot = expression.indexOf('.');
        if (dot >= 0) {
            b.rootParam = expression.substring(0, dot);
            b.propertyPath = expression.substring(dot + 1);
        } else {
            b.rootParam = expression;
            b.propertyPath = "";
        }
        return b;
    }

    // ---------- 全条件真での展開 ----------

    private static final class ExpandCtx {
        final StringBuilder sql = new StringBuilder();
        final List<String> binds = new ArrayList<String>();
        boolean enabled;
    }

    private void expand(final Node node, final ExpandCtx ctx) {
        if (node instanceof SqlNode) {
            ctx.sql.append(((SqlNode) node).getSql());
            return;
        }
        if (node instanceof PrefixSqlNode) {
            final PrefixSqlNode p = (PrefixSqlNode) node;
            if (ctx.enabled) {
                ctx.sql.append(p.getPrefix());
            }
            ctx.sql.append(p.getSql());
            return;
        }
        if (node instanceof BindVariableNode) {
            ctx.sql.append("?");
            ctx.binds.add(((BindVariableNode) node).getExpression());
            return;
        }
        if (node instanceof ParenBindVariableNode) {
            // 代表として単一要素の IN を仮定する
            ctx.sql.append("(?)");
            ctx.binds.add(((ParenBindVariableNode) node).getExpression());
            return;
        }
        if (node instanceof EmbeddedValueNode) {
            // 埋め込み値は実行時の代表値に依存するため、展開 SQL では空文字とする
            return;
        }
        if (node instanceof BeginNode) {
            final ExpandCtx child = new ExpandCtx();
            child.enabled = false;
            for (int i = 0; i < node.getChildSize(); i++) {
                expand(node.getChild(i), child);
            }
            if (child.enabled) {
                ctx.sql.append(child.sql);
                ctx.binds.addAll(child.binds);
            }
            return;
        }
        if (node instanceof IfNode) {
            // 全条件真: IF 分岐を採用し、ELSE は採らない
            for (int i = 0; i < node.getChildSize(); i++) {
                expand(node.getChild(i), ctx);
            }
            ctx.enabled = true;
            return;
        }
        if (node instanceof ElseNode) {
            for (int i = 0; i < node.getChildSize(); i++) {
                expand(node.getChild(i), ctx);
            }
            ctx.enabled = true;
            return;
        }
        // ContainerNode(トップ)およびその他
        for (int i = 0; i < node.getChildSize(); i++) {
            expand(node.getChild(i), ctx);
        }
    }
}
