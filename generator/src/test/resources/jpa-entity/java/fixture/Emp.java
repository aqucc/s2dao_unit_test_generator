package fixture;

import java.io.Serializable;
import java.sql.Timestamp;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.Version;

/**
 * S2JDBC 風(JPA アノテーション)エンティティのフィクスチャ。
 * フィールドアクセス方式。@Table/@Column/@Id/@Version/@GeneratedValue/@Transient を網羅する。
 */
@Entity
@Table(name = "EMP")
public class Emp implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "EMP_ID")
    private Integer empId;

    /** @Column の無いフィールドも永続対象(カラム名 = プロパティ名)。 */
    private String ename;

    @Column(name = "JOB_NAME")
    private String job;

    private Integer deptno;

    /** 楽観ロック列。 */
    @Version
    private Integer version;

    private Timestamp updated;

    /** 永続対象外。 */
    @Transient
    private String temp;

    public Integer getEmpId() {
        return empId;
    }

    public void setEmpId(Integer empId) {
        this.empId = empId;
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

    public Integer getDeptno() {
        return deptno;
    }

    public void setDeptno(Integer deptno) {
        this.deptno = deptno;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Timestamp getUpdated() {
        return updated;
    }

    public void setUpdated(Timestamp updated) {
        this.updated = updated;
    }

    public String getTemp() {
        return temp;
    }

    public void setTemp(String temp) {
        this.temp = temp;
    }
}
