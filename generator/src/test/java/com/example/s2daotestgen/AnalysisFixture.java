package com.example.s2daotestgen;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import com.example.s2daotestgen.dao.DaoAnalyzer;
import com.example.s2daotestgen.dao.Dialect;
import com.example.s2daotestgen.dao.SourceRepository;
import com.example.s2daotestgen.model.MetaModel.DaoMeta;
import com.example.s2daotestgen.model.MetaModel.MethodMeta;
import com.example.s2daotestgen.sql.SqlFileIndex;

/**
 * サンプルプロジェクトを解析して DAO メタを取得するテスト用フィクスチャ。
 */
public final class AnalysisFixture {

    public static final String S2DAO_SRC =
            "../samples/s2dao/s2-dao-examples/src/main/java";
    public static final String S2DAO_SQL =
            "../samples/s2dao/s2-dao-examples/src/main/resources";
    public static final String TIGER_SRC =
            "../samples/s2dao-tiger/s2-dao-tiger-examples/src/main/java";
    public static final String TIGER_SQL =
            "../samples/s2dao-tiger/s2-dao-tiger-examples/src/main/resources";

    private AnalysisFixture() {
    }

    /** src/sql ディレクトリを解析し、DAO 単純名 -> メタ のマップを返す。 */
    public static Map<String, DaoMeta> analyze(final String srcDir,
            final String sqlDir, final Dialect dialect) throws Exception {
        final SourceRepository repo = new SourceRepository();
        repo.addSourceRoot(new File(srcDir));
        final SqlFileIndex idx = new SqlFileIndex();
        idx.addRoot(new File(sqlDir));
        final DaoAnalyzer analyzer = new DaoAnalyzer(repo, idx, dialect);
        final Map<String, DaoMeta> out = new HashMap<String, DaoMeta>();
        for (final SourceRepository.TypeInfo info : repo.getAllTypes()) {
            if (analyzer.isDao(info)) {
                out.put(info.simpleName, analyzer.analyze(info));
            }
        }
        return out;
    }

    public static MethodMeta method(final DaoMeta dao, final String name) {
        for (final MethodMeta m : dao.methods) {
            if (m.name.equals(name)) {
                return m;
            }
        }
        return null;
    }
}
