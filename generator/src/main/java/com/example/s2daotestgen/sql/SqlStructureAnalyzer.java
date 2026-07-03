package com.example.s2daotestgen.sql;

import java.util.ArrayList;
import java.util.List;

import com.example.s2daotestgen.model.MetaModel.ColumnBinding;
import com.example.s2daotestgen.model.MetaModel.EntityMeta;
import com.example.s2daotestgen.model.MetaModel.PropertyMeta;
import com.example.s2daotestgen.model.MetaModel.SqlStructure;

/**
 * 展開後 SQL(2-way コメント除去済み)を自前トークナイザで構造解析する。
 *
 * <p>
 * 文字列リテラル({@code '...'} の {@code ''} エスケープ含む)とコメント
 * ({@code /* *}{@code /}, {@code --})を正しくスキップし、文種別・対象テーブル・
 * SELECT カラム・WHERE 句のカラム⇔バインド対応を抽出する。
 * </p>
 */
public final class SqlStructureAnalyzer {

    private static final String[] CLAUSE_KEYWORDS = {
            "where", "group", "order", "having", "union", "for", "limit",
            "offset", "start", "connect" };

    public SqlStructure analyze(final String rawExpandedSql,
            final EntityMeta entity, final List<String> bindOrder) {
        final SqlStructure s = new SqlStructure();
        final List<Tok> toks = tokenize(rawExpandedSql);
        if (toks.isEmpty()) {
            s.statementType = "OTHER";
            return s;
        }
        final String first = toks.get(0).text.toLowerCase();
        if (first.equals("select")) {
            s.statementType = "SELECT";
            analyzeSelect(toks, entity, s);
        } else if (first.equals("insert")) {
            s.statementType = "INSERT";
            analyzeInsert(toks, s);
        } else if (first.equals("update")) {
            s.statementType = "UPDATE";
            analyzeUpdate(toks, s);
        } else if (first.equals("delete")) {
            s.statementType = "DELETE";
            analyzeDelete(toks, s);
        } else {
            s.statementType = "OTHER";
        }
        analyzeWhereBindings(toks, bindOrder, s);
        return s;
    }

    // ---------- 各文種別 ----------

    private void analyzeSelect(final List<Tok> toks, final EntityMeta entity,
            final SqlStructure s) {
        final int fromIdx = indexOfKeyword(toks, "from", 1);
        // SELECT カラム(SELECT .. FROM の間)
        if (fromIdx > 1) {
            final List<List<Tok>> cols = splitTopLevelByComma(toks, 1, fromIdx);
            for (final List<Tok> col : cols) {
                addSelectColumn(col, entity, s);
            }
        }
        // テーブル(FROM .. 次の句)
        if (fromIdx >= 0) {
            collectTables(toks, fromIdx + 1, s);
        }
    }

    private void addSelectColumn(final List<Tok> col, final EntityMeta entity,
            final SqlStructure s) {
        final String text = join(col).trim();
        if (text.isEmpty()) {
            return;
        }
        if (text.equals("*") || text.matches("[A-Za-z0-9_$]+\\.\\*")) {
            final String prefix = text.equals("*") ? null
                    : text.substring(0, text.indexOf('.'));
            if (entity != null) {
                s.selectStarExpanded = true;
                for (final PropertyMeta pt : entity.properties) {
                    if (!pt.persistent) {
                        continue;
                    }
                    s.selectColumns.add(prefix != null
                            ? prefix + "." + pt.columnName : pt.columnName);
                }
            } else {
                s.selectColumns.add(text);
            }
        } else {
            s.selectColumns.add(text);
        }
    }

    private void analyzeInsert(final List<Tok> toks, final SqlStructure s) {
        // INSERT INTO <table>
        final int into = indexOfKeyword(toks, "into", 1);
        if (into >= 0 && into + 1 < toks.size()) {
            s.tables.add(stripAlias(toks.get(into + 1).text));
        }
    }

    private void analyzeUpdate(final List<Tok> toks, final SqlStructure s) {
        if (toks.size() > 1) {
            s.tables.add(stripAlias(toks.get(1).text));
        }
    }

    private void analyzeDelete(final List<Tok> toks, final SqlStructure s) {
        final int from = indexOfKeyword(toks, "from", 1);
        if (from >= 0 && from + 1 < toks.size()) {
            s.tables.add(stripAlias(toks.get(from + 1).text));
        }
    }

    /** FROM 以降、次の句キーワードまでのテーブル(カンマ/JOIN 対応)を収集する。 */
    private void collectTables(final List<Tok> toks, final int start,
            final SqlStructure s) {
        int i = start;
        boolean expectTable = true;
        while (i < toks.size()) {
            final Tok t = toks.get(i);
            final String lw = t.text.toLowerCase();
            if (t.punct) {
                if (t.text.equals(",")) {
                    expectTable = true;
                }
                i++;
                continue;
            }
            if (isClauseKeyword(lw)) {
                break;
            }
            if (lw.equals("join")) {
                expectTable = true;
                i++;
                continue;
            }
            if (lw.equals("on")) {
                // JOIN 条件はスキップ(次の JOIN/句まで)
                i++;
                while (i < toks.size()) {
                    final String l2 = toks.get(i).text.toLowerCase();
                    if (!toks.get(i).punct && (l2.equals("join")
                            || l2.equals("left") || l2.equals("right")
                            || l2.equals("inner") || l2.equals("outer")
                            || isClauseKeyword(l2))) {
                        break;
                    }
                    i++;
                }
                continue;
            }
            if (lw.equals("left") || lw.equals("right") || lw.equals("inner")
                    || lw.equals("outer") || lw.equals("cross")) {
                i++;
                continue;
            }
            if (expectTable) {
                final String tbl = stripAlias(t.text);
                if (!tbl.isEmpty() && !s.tables.contains(tbl)) {
                    s.tables.add(tbl);
                }
                expectTable = false;
            }
            i++;
        }
    }

    // ---------- WHERE 句のカラム⇔バインド対応 ----------

    private void analyzeWhereBindings(final List<Tok> toks,
            final List<String> bindOrder, final SqlStructure s) {
        int qmark = 0;
        final int whereIdx = indexOfKeyword(toks, "where", 0);
        for (int i = 0; i < toks.size(); i++) {
            final Tok t = toks.get(i);
            if (t.punct && t.text.equals("?")) {
                // このバインドが WHERE 句内で "col op ?" の形なら対応を記録
                if (whereIdx >= 0 && i > whereIdx) {
                    tryRecordBinding(toks, i, qmark, bindOrder, s);
                }
                qmark++;
            }
        }
    }

    private void tryRecordBinding(final List<Tok> toks, final int qIdx,
            final int qmark, final List<String> bindOrder, final SqlStructure s) {
        // パターン: IDENT OP ?    または   IDENT IN ( ?
        int p = qIdx - 1;
        // IN (?
        if (p >= 1 && toks.get(p).punct && toks.get(p).text.equals("(")) {
            final int inIdx = p - 1;
            if (inIdx >= 1 && !toks.get(inIdx).punct
                    && toks.get(inIdx).text.equalsIgnoreCase("in")
                    && !toks.get(inIdx - 1).punct) {
                addBinding(toks.get(inIdx - 1).text, "IN", qmark, bindOrder, s);
                return;
            }
        }
        if (p < 1) {
            return;
        }
        final Tok op = toks.get(p);
        if (op.punct && isComparisonOp(op.text)) {
            final Tok col = toks.get(p - 1);
            if (!col.punct) {
                addBinding(col.text, op.text, qmark, bindOrder, s);
            }
        } else if (!op.punct && op.text.equalsIgnoreCase("like")) {
            final Tok col = toks.get(p - 1);
            if (!col.punct) {
                addBinding(col.text, "LIKE", qmark, bindOrder, s);
            }
        }
    }

    private void addBinding(final String column, final String op, final int qmark,
            final List<String> bindOrder, final SqlStructure s) {
        final ColumnBinding cb = new ColumnBinding();
        cb.column = column;
        cb.operator = op;
        if (bindOrder != null && qmark < bindOrder.size()) {
            cb.bindExpression = bindOrder.get(qmark);
        }
        s.whereBindings.add(cb);
    }

    // ---------- トークナイザ ----------

    private static final class Tok {
        final String text;
        final boolean punct;
        Tok(final String text, final boolean punct) {
            this.text = text;
            this.punct = punct;
        }
    }

    private List<Tok> tokenize(final String sql) {
        final List<Tok> out = new ArrayList<Tok>();
        final int n = sql.length();
        int i = 0;
        while (i < n) {
            final char c = sql.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            // 行コメント
            if (c == '-' && i + 1 < n && sql.charAt(i + 1) == '-') {
                while (i < n && sql.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }
            // ブロックコメント
            if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(sql.charAt(i) == '*' && sql.charAt(i + 1) == '/')) {
                    i++;
                }
                i += 2;
                continue;
            }
            // 文字列リテラル
            if (c == '\'') {
                i++;
                final StringBuilder sb = new StringBuilder();
                while (i < n) {
                    final char d = sql.charAt(i);
                    if (d == '\'') {
                        if (i + 1 < n && sql.charAt(i + 1) == '\'') {
                            sb.append('\'');
                            i += 2;
                            continue;
                        }
                        i++;
                        break;
                    }
                    sb.append(d);
                    i++;
                }
                out.add(new Tok("'" + sb.toString() + "'", false));
                continue;
            }
            // 識別子(修飾名はドットを含める)
            if (Character.isLetterOrDigit(c) || c == '_' || c == '$') {
                final int start = i;
                while (i < n) {
                    final char d = sql.charAt(i);
                    if (Character.isLetterOrDigit(d) || d == '_' || d == '$' || d == '.') {
                        i++;
                    } else {
                        break;
                    }
                }
                String text = sql.substring(start, i);
                // 末尾のドットは切り離す(例: emp.* の "emp.")
                while (text.endsWith(".")) {
                    text = text.substring(0, text.length() - 1);
                    out.add(new Tok(text, false));
                    out.add(new Tok(".", true));
                    text = null;
                    break;
                }
                if (text != null) {
                    out.add(new Tok(text, false));
                }
                continue;
            }
            // 2 文字演算子
            if (i + 1 < n) {
                final String two = sql.substring(i, i + 2);
                if (two.equals("<>") || two.equals("!=") || two.equals(">=")
                        || two.equals("<=")) {
                    out.add(new Tok(two, true));
                    i += 2;
                    continue;
                }
            }
            out.add(new Tok(String.valueOf(c), true));
            i++;
        }
        return out;
    }

    // ---------- helper ----------

    private int indexOfKeyword(final List<Tok> toks, final String kw, final int from) {
        int depth = 0;
        for (int i = from; i < toks.size(); i++) {
            final Tok t = toks.get(i);
            if (t.punct) {
                if (t.text.equals("(")) {
                    depth++;
                } else if (t.text.equals(")")) {
                    depth--;
                }
                continue;
            }
            if (depth == 0 && t.text.equalsIgnoreCase(kw)) {
                return i;
            }
        }
        return -1;
    }

    private List<List<Tok>> splitTopLevelByComma(final List<Tok> toks,
            final int start, final int end) {
        final List<List<Tok>> out = new ArrayList<List<Tok>>();
        List<Tok> cur = new ArrayList<Tok>();
        int depth = 0;
        for (int i = start; i < end; i++) {
            final Tok t = toks.get(i);
            if (t.punct && t.text.equals("(")) {
                depth++;
            } else if (t.punct && t.text.equals(")")) {
                depth--;
            }
            if (depth == 0 && t.punct && t.text.equals(",")) {
                out.add(cur);
                cur = new ArrayList<Tok>();
            } else {
                cur.add(t);
            }
        }
        if (!cur.isEmpty()) {
            out.add(cur);
        }
        return out;
    }

    private String join(final List<Tok> toks) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < toks.size(); i++) {
            if (i > 0 && !toks.get(i).punct
                    && !toks.get(i - 1).text.equals(".")
                    && !toks.get(i).text.equals(".")) {
                sb.append(" ");
            }
            sb.append(toks.get(i).text);
        }
        return sb.toString();
    }

    private boolean isClauseKeyword(final String lw) {
        for (final String k : CLAUSE_KEYWORDS) {
            if (k.equals(lw)) {
                return true;
            }
        }
        return false;
    }

    private boolean isComparisonOp(final String s) {
        return s.equals("=") || s.equals("<>") || s.equals("!=")
                || s.equals(">") || s.equals("<") || s.equals(">=")
                || s.equals("<=");
    }

    /** "emp e" のような別名付き参照からテーブル名だけ返す(修飾名はそのまま)。 */
    private String stripAlias(final String s) {
        return s;
    }
}
