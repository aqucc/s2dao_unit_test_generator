package com.example.s2daotestgen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import com.example.s2daotestgen.gen.TestClassGenerator;

/**
 * 生成テストの整形: ブロック先頭コメントの手前に空行を入れる
 * {@link TestClassGenerator#separateBlockComments(String)} の回帰テスト。
 */
public class SeparateBlockCommentsTest {

    @Test
    public void insertsBlankLineBeforeBlockComment() {
        String src = "a();\n// block1\nb();\n";
        assertEquals("a();\n\n// block1\nb();\n",
                TestClassGenerator.separateBlockComments(src));
    }

    @Test
    public void noBlankAfterOpeningBrace() {
        String src = "try {\n// first\nc();\n";
        // 波括弧直後は空行を入れない
        assertEquals("try {\n// first\nc();\n",
                TestClassGenerator.separateBlockComments(src));
    }

    @Test
    public void groupedCommentsGetSingleBlank() {
        String src = "x();\n// line1\n// line2\ny();\n";
        assertEquals("x();\n\n// line1\n// line2\ny();\n",
                TestClassGenerator.separateBlockComments(src));
    }

    @Test
    public void doesNotDoubleBlank() {
        String src = "x();\n\n// already spaced\ny();\n";
        assertEquals("x();\n\n// already spaced\ny();\n",
                TestClassGenerator.separateBlockComments(src));
    }

    @Test
    public void ignoresTrailingInlineComments() {
        String src = "foo(1), // inline\nbar();\n";
        // 行末尾のインラインコメントは対象外(変化なし)
        assertEquals(src, TestClassGenerator.separateBlockComments(src));
    }

    @Test
    public void appliedToGeneratedServiceSource() throws Exception {
        // 実生成物でも「文 → 空行 → 行頭コメント」の並びになっていること
        java.util.Map<String, com.example.s2daotestgen.model.MetaModel.DaoMeta> daos =
                AnalysisFixture.analyze(
                        "src/test/resources/crud-from-sql/java",
                        "src/test/resources/crud-from-sql/sql",
                        com.example.s2daotestgen.dao.Dialect.ORACLE);
        // 少なくとも 1 つ、行頭コメントの直前が空行になっている箇所があること
        // (crud-from-sql の ExecDao は SELECT/UPDATE を持つ通常 DAO 経路)
        final com.example.s2daotestgen.gen.TestClassGenerator gen =
                new com.example.s2daotestgen.gen.TestClassGenerator();
        final com.example.s2daotestgen.gen.TestClassGenerator.Result r =
                gen.generate(daos.get("ExecDao"), null,
                        new com.example.s2daotestgen.gen.GenerationReport());
        assertTrue("行頭コメントの手前に空行があるはず",
                r.source.indexOf("\n\n            //") >= 0
                        || r.source.indexOf("\n\n        //") >= 0);
        assertFalse("波括弧直後に空行+コメントは入れない",
                r.source.indexOf("{\n\n            //") >= 0);
    }
}
