package example.servicebase.jpa;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * JPA/S2JDBC 風の {@code @Entity} を模した検証用アノテーション。
 * 実際の javax.persistence への依存を避け、フィクスチャを自己完結でコンパイル可能にする。
 * ジェネレーターはアノテーションの単純名(Entity/Table/Column/Id/Version 等)で判定する。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Entity {
}
