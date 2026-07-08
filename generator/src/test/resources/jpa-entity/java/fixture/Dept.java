package fixture;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

/**
 * getter アクセス方式・@Table 省略(既定テーブル名 = 単純名)の JPA 風エンティティ。
 */
@Entity
public class Dept {

    private Integer deptno;

    private String dname;

    private String loc;

    @Id
    @Column(name = "DEPTNO")
    public Integer getDeptno() {
        return deptno;
    }

    public void setDeptno(Integer deptno) {
        this.deptno = deptno;
    }

    public String getDname() {
        return dname;
    }

    public void setDname(String dname) {
        this.dname = dname;
    }

    public String getLoc() {
        return loc;
    }

    public void setLoc(String loc) {
        this.loc = loc;
    }
}
