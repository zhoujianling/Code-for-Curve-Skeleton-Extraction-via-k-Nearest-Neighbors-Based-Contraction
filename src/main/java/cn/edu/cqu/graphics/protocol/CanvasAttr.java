package cn.edu.cqu.graphics.protocol;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 如果一个 PipeOutput 被标注为 visualize = true,
 * 那么可以（可选）给这个字段再加一个 @CanvasAttr 的注解，来告诉 DataRender 应该用什么
 * 颜色来渲染图形
 *
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CanvasAttr {

    /**
     * {red, green, blue} 0-255
     * @return default color is BLUE
     */
    int[] primaryColor() default {0, 0, 255};

    int[] secondaryColor() default {125, 125, 125};

    int primarySize() default 3;
}
