package com.example.s2daotestgen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.seasar.extension.sql.Node;
import org.seasar.extension.sql.SqlContext;
import org.seasar.extension.sql.context.SqlContextImpl;
import org.seasar.extension.sql.parser.SqlParserImpl;
import org.seasar.framework.util.StringUtil;

import com.example.s2daotestgen.sql.TwoWaySqlAnalyzer;

/**
 * ベンダリングした 2-way SQL パーサが依存する {@code org.seasar.framework.*} を自前シムへ
 * 置き換えた際の「挙動同一性」を担保するテスト。
 *
 * <p>
 * 実際のパーサ + {@code SqlContextImpl#accept}(OGNL 評価 + BeanDesc プロパティアクセスの
 * シムを経由)で得られる展開結果が、本ジェネレーターの {@link TwoWaySqlAnalyzer} の
 * 「全条件真」展開と一致することを確認する。
 * </p>
 */
public class ShimConformanceTest {

    /** バインドのプロパティパス解決(BeanDesc シム)を通すための Bean。 */
    public static class Emp {
        private String ename;
        private int empno;

        public String getEname() {
            return ename;
        }

        public void setEname(final String ename) {
            this.ename = ename;
        }

        public int getEmpno() {
            return empno;
        }

        public void setEmpno(final int empno) {
            this.empno = empno;
        }
    }

    @Test
    public void stringUtilSplitMatchesSeasarSemantics() {
        assertEquals("[job, deptno]",
                java.util.Arrays.toString(StringUtil.split("job, deptno", " ,")));
        assertEquals("[a, b, c]",
                java.util.Arrays.toString(StringUtil.split("a.b.c", ".")));
        assertEquals("x?y?z", StringUtil.replace("x_y_z", "_", "?"));
        assertTrue(StringUtil.isEmpty(""));
        assertTrue(StringUtil.isEmpty(null));
    }

    @Test
    public void parserWithShimsEvaluatesIfAndBind() {
        final String sql = "SELECT * FROM emp\n"
                + "/*BEGIN*/WHERE\n"
                + "  /*IF job != null*/job = /*job*/'CLERK'/*END*/\n"
                + "  /*IF deptno != null*/AND deptno = /*deptno*/20/*END*/\n"
                + "/*END*/";

        // 実パーサ + SqlContextImpl.accept()(OGNL/BeanDesc シム経由)で全条件を真に評価
        final Node root = new SqlParserImpl(sql).parse();
        final SqlContext ctx = new SqlContextImpl();
        ctx.addArg("job", "CLERK", String.class);
        ctx.addArg("deptno", Integer.valueOf(20), Integer.class);
        root.accept(ctx);

        // ジェネレーターの全条件真展開
        final TwoWaySqlAnalyzer.Result r = new TwoWaySqlAnalyzer().analyze(sql);

        assertEquals("実パーサ評価とジェネレーター展開の SQL が一致すること",
                ctx.getSql(), r.expandedSql);
        assertEquals("バインド数が一致すること",
                ctx.getBindVariables().length, r.expandedBindOrder.size());
        assertEquals(2, ctx.getBindVariables().length);
    }

    @Test
    public void beanPropertyPathBindingViaShim() {
        // /*employee.ename*/ のプロパティパス解決に BeanDesc シムを使用
        final String sql = "UPDATE emp SET ename = /*employee.ename*/'X' "
                + "WHERE empno = /*employee.empno*/0";
        final Emp emp = new Emp();
        emp.setEname("SCOTT");
        emp.setEmpno(7788);

        final Node root = new SqlParserImpl(sql).parse();
        final SqlContext ctx = new SqlContextImpl();
        ctx.addArg("employee", emp, Emp.class);
        root.accept(ctx);

        assertEquals("UPDATE emp SET ename = ? WHERE empno = ?", ctx.getSql());
        final Object[] binds = ctx.getBindVariables();
        assertEquals(2, binds.length);
        assertEquals("SCOTT", binds[0]);
        assertEquals(Integer.valueOf(7788), binds[1]);
    }
}
