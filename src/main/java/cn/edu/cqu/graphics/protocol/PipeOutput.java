package cn.edu.cqu.graphics.protocol;

import cn.edu.cqu.graphics.Constants;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 指定管道的输出
 * 类型，用于指定一个Unique的类型
 * 默认启用diskcache
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PipeOutput {
    int type()default Constants.TYPE_DEFAULT;
    boolean visualize() default false;
    boolean visible() default true;
    String name() default DEFAULT_NAME;

    String DEFAULT_NAME = "未命名";
}
