import java.io.*;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.seasar.framework.container.S2Container;
import org.seasar.framework.container.factory.S2ContainerFactory;
import examples.dao.EmployeeDao;
import examples.dao.Employee;

/**
 * Seasar2 / S2Dao runtime smoke test.
 * usage: SmokeTest <dicon> <ddlFile> <label>
 */
public class SmokeTest {
    static int failures = 0;
    static void check(boolean cond, String msg) {
        System.out.println((cond ? "  [OK]  " : "  [NG]  ") + msg);
        if (!cond) failures++;
    }

    public static void main(String[] args) throws Exception {
        String dicon = args[0];
        String ddlFile = args[1];
        String label = args[2];
        System.out.println("==== S2Dao smoke test: " + label + " (dicon=" + dicon + ") ====");

        S2Container container = S2ContainerFactory.create(dicon);
        container.init();
        try {
            DataSource ds = (DataSource) container.getComponent(DataSource.class);
            setupSchema(ds, ddlFile);

            EmployeeDao dao = (EmployeeDao) container.getComponent(EmployeeDao.class);

            // 1) getAllEmployees()
            List all = dao.getAllEmployees();
            System.out.println("getAllEmployees() -> " + all.size() + " rows");
            check(all.size() == 14, "getAllEmployees returns 14 rows");
            Employee first = (Employee) all.get(0);
            check(first.getEmpno() == 7369, "first employee empno == 7369 (ORDER BY empno)");
            check(first.getDepartment() != null && "RESEARCH".equals(first.getDepartment().getDname()),
                    "N:1 relation dept mapped (7369 -> RESEARCH)");

            // 2) getEmployeeByJobDeptno("CLERK", 20)  -- 2-way SQL /*BEGIN*//*IF*/
            List clerks20 = dao.getEmployeeByJobDeptno("CLERK", new Integer(20));
            System.out.println("getEmployeeByJobDeptno(CLERK,20) -> " + clerks20.size() + " rows");
            check(clerks20.size() == 2, "CLERK in dept 20 -> 2 rows (SMITH,ADAMS)");
            // 2b) null job -> only deptno filter (IF branch skipped)
            List dept20 = dao.getEmployeeByJobDeptno(null, new Integer(20));
            System.out.println("getEmployeeByJobDeptno(null,20) -> " + dept20.size() + " rows");
            check(dept20.size() == 5, "dept 20 (job IF skipped) -> 5 rows");

            // 3) update()
            Employee e = new Employee(7369);
            e.setEname("SMITH2");
            int updated = dao.update(e);
            System.out.println("update(7369 -> SMITH2) -> " + updated + " row(s)");
            check(updated == 1, "update affects 1 row");
            List clerks20after = dao.getEmployeeByJobDeptno("CLERK", new Integer(20));
            boolean renamed = false;
            for (Iterator it = clerks20after.iterator(); it.hasNext();) {
                Employee x = (Employee) it.next();
                if (x.getEmpno() == 7369) renamed = "SMITH2".equals(x.getEname());
            }
            check(renamed, "update persisted (7369 ename == SMITH2)");
        } finally {
            container.destroy();
        }
        System.out.println("==== " + label + ": " + (failures == 0 ? "ALL PASSED" : (failures + " FAILURES")) + " ====");
        if (failures != 0) System.exit(1);
    }

    static void setupSchema(DataSource ds, String ddlFile) throws Exception {
        Connection con = ds.getConnection();
        con.setAutoCommit(true);
        Statement st = con.createStatement();
        // drop first (ignore errors)
        for (int i = 0; i < 2; i++) {
            String t = (i == 0) ? "EMP" : "DEPT";
            try { st.execute("DROP TABLE " + t); } catch (SQLException ignore) {}
        }
        String sql = readFile(ddlFile);
        String[] stmts = sql.split(";");
        for (int i = 0; i < stmts.length; i++) {
            String s = stmts[i].trim();
            if (s.length() == 0) continue;
            String u = s.toUpperCase();
            if (u.startsWith("DROP") || u.startsWith("COMMIT")) continue;
            st.execute(s);
        }
        st.close();
        con.close();
        System.out.println("schema created and seeded from " + ddlFile);
    }

    static String readFile(String p) throws IOException {
        StringBuffer sb = new StringBuffer();
        BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(p), "UTF-8"));
        String line;
        while ((line = r.readLine()) != null) sb.append(line).append("\n");
        r.close();
        return sb.toString();
    }
}
