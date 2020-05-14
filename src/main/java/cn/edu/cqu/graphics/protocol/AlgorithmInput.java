package cn.edu.cqu.graphics.protocol;

import cn.edu.cqu.graphics.Constants;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AlgorithmInput {

    String[] supportedType() default {"txt"};
    String name() default "输入数据";
    int type() default Constants.TYPE_DEFAULT;
}
