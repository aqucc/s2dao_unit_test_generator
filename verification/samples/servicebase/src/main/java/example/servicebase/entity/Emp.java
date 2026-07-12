package example.servicebase.entity;

import example.servicebase.jpa.Column;
import example.servicebase.jpa.Entity;
import example.servicebase.jpa.Id;
import example.servicebase.jpa.Table;
import example.servicebase.jpa.Version;

/**
 * JPA/S2JDBC 風のエンティティ(@Entity/@Table/@Column/@Id/@Version)。
 * Service とは別フォルダだが同一プロジェクト内。ジェネレーターはこれを解析して
 * {@code Emp.entity.json} を出力し、テーブル逆引き辞書(EMP → Emp)を完全にする。
 */
@Entity
@Table(name = "EMP")
public class Emp {

    @Id
    @Column(name = "EMPNO")
    private int empno;

    @Column(name = "ENAME")
    private String ename;

    @Column(name = "JOB")
    private String job;

    @Column(name = "DEPTNO")
    private int deptno;

    @Column(name = "SAL")
    private int sal;

    @Version
    @Column(name = "VERSION_NO")
    private int versionNo;

    public int getEmpno() {
        return empno;
    }

    public void setEmpno(int empno) {
        this.empno = empno;
    }

    public String getEname() {
        return ename;
    }

    public void setEname(String ename) {
        this.ename = ename;
    }

    public String getJob() {
        return job;
    }

    public void setJob(String job) {
        this.job = job;
    }

    public int getDeptno() {
        return deptno;
    }

    public void setDeptno(int deptno) {
        this.deptno = deptno;
    }

    public int getSal() {
        return sal;
    }

    public void setSal(int sal) {
        this.sal = sal;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(int versionNo) {
        this.versionNo = versionNo;
    }
}
