package cn.edu.cqu.graphics.protocol;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 从两个输出里读数据：@PipeOutput、@WriteOutput
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FromPreviousOutput {
//    int type()default Constants.TYPE_DEFAULT;
    String name()default PipeOutput.DEFAULT_NAME;
}
