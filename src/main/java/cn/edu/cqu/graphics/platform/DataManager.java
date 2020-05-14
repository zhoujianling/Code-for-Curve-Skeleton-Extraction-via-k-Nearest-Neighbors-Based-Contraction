package cn.edu.cqu.graphics.platform;

import cn.edu.cqu.graphics.config.AlgorithmConfig;
import cn.edu.cqu.graphics.protocol.FromPreviousOutput;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;
import java.util.logging.Logger;

@Component
public class DataManager {

    @Autowired
    MemCachePool pool;

    @Autowired
    Logger logger;

//    public void writeCache(Object object) {
//        List<Field> fields = fetchAllFields(object.getClass());
//        try {
//            for (Field f : fields) {
//                if (isMemWriteCache(f)) {
//                    f.setAccessible(true);
//                    WriteOutput cache = f.getAnnotation(WriteOutput.class);
//                    Object val = f.get(object);
//                    pool.put(f.getType(), cache.value(), val);
//                }
//            }
//        } catch (IllegalAccessException e) {
//            e.printStackTrace();
//            logger.warning("自动写Cache失败。" + e.getMessage());
//        }
//    }

    public void searchCache(Object object) {
        this.searchCache(object, MemCachePool.NO_CONFIG);
    }

    public void searchCache(Object object, AlgorithmConfig config) {
        List<Field> fields = fetchAllFields(object.getClass());
        try {
            for (Field f : fields) {
                if (isMemReadCache(f)) {
                    f.setAccessible(true);
                    FromPreviousOutput cache = f.getAnnotation(FromPreviousOutput.class);
                    f.set(object, pool.get(cache.name().hashCode(), config));
                }
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            logger.warning("警告，查找Cache失败。" + e.getMessage());
        }
    }


    private List<Field> fetchAllFields(Class<?> tempClass) {
        List<Field> fieldList = new Vector<>() ;
        while (tempClass != null) {//当父类为null的时候说明到达了最上层的父类(Object类).
            fieldList.addAll(Arrays.asList(tempClass.getDeclaredFields()));
            tempClass = tempClass.getSuperclass(); //得到父类,然后赋给自己
        }
        return fieldList;
    }

    public Object fetchObject(int type, AlgorithmConfig config) {
        return pool.get(type, config);
    }

    private boolean isMemReadCache(Field field) {
        return field.getAnnotation(FromPreviousOutput.class) != null;
    }

}
