package com.example.s2daotestgen.gen;

import java.util.ArrayList;
import java.util.List;

/**
 * テストコード生成の集計レポート。
 */
public final class GenerationReport {

    /** 生成したテストクラス数。 */
    public int classes;
    /** 生成したテストメソッド数。 */
    public int testMethods;
    /** スキップしたメソッド数。 */
    public int skipped;
    /** スキップ理由の明細。 */
    public final List skipReasons = new ArrayList();

    /** クラス単位の内訳。 */
    public final List classSummaries = new ArrayList();

    public static final class ClassSummary {
        public String daoSimpleName;
        public int testMethods;
        public int skipped;
    }

    public void addSkip(String daoSimpleName, String method, String reason) {
        skipped++;
        skipReasons.add(daoSimpleName + "#" + method + " : " + reason);
    }

    public String toText() {
        StringBuffer sb = new StringBuffer();
        sb.append("生成テストクラス数: ").append(classes).append("\n");
        sb.append("生成テストメソッド数: ").append(testMethods).append("\n");
        sb.append("スキップ数: ").append(skipped).append("\n");
        for (int i = 0; i < classSummaries.size(); i++) {
            ClassSummary cs = (ClassSummary) classSummaries.get(i);
            sb.append("  - ").append(cs.daoSimpleName)
              .append(" : tests=").append(cs.testMethods)
              .append(", skipped=").append(cs.skipped).append("\n");
        }
        if (!skipReasons.isEmpty()) {
            sb.append("スキップ理由:\n");
            for (int i = 0; i < skipReasons.size(); i++) {
                sb.append("  * ").append(skipReasons.get(i)).append("\n");
            }
        }
        return sb.toString();
    }
}
