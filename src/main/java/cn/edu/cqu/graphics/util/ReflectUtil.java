package cn.edu.cqu.graphics.util;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ReflectUtil {


    public static List<Field> fetchAllFields(Object object) {
        List<Field> fieldList = new ArrayList<>() ;
        Class tempClass = object.getClass();
        while (tempClass != null) {//当父类为null的时候说明到达了最上层的父类(Object类).
            fieldList.addAll(Arrays.asList(tempClass.getDeclaredFields()));
            tempClass = tempClass.getSuperclass(); //得到父类,然后赋给自己
        }
        return fieldList;
    }

    public static void deepCopy(Object dst, Object src) {
        if (dst.getClass() == src.getClass()) {
            System.err.println("cn.edu.cqu.lvp.util.ReflectUtil::deepCopy():");
            System.err.println("错误：类型冲突");
        }
        List<Field> list = fetchAllFields(dst);
        try {
            for (Field field : list) {
                field.setAccessible(true);
                field.set(dst, field.get(src));
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }
}
