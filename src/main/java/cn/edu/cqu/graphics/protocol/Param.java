package cn.edu.cqu.graphics.protocol;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Param {
    String comment() default "未定义";
    String key() default "null";
    int minVal() default 0;
    int maxVal() default 10;

}
