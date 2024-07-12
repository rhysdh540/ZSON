package dev.nolij.zson;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ZsonField {
	String comment() default ZsonValue.NO_COMMENT;
	boolean include() default false;
	boolean exclude() default false;
}
