package cn.edu.cqu.graphics.platform;

import cn.edu.cqu.graphics.config.AlgorithmConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.logging.Logger;

@Component
public class MemCachePool {

    @Autowired
    Logger logger;

    //type_id  ->  config(object) -> object
    private HashMap<Integer, HashMap<AlgorithmConfig, Object>> cacheMap = new HashMap<>();

    public static final AlgorithmConfig NO_CONFIG = new AlgorithmConfig() {
        @Override
        public String toString() {
            return "如果不在Pipeline框架内，也想写全局config，用此对象";
        }
    };

    public void put(Integer type,  Object obj) {
        put(type, NO_CONFIG, obj);
    }

    public void put(Integer type, AlgorithmConfig config, Object obj) {
        if (cacheMap.get(type) == null) cacheMap.put(type, new HashMap<>());
        HashMap<AlgorithmConfig, Object> qua2Object = cacheMap.get(type);
        if (qua2Object.get(config) != null) {
            logger.warning("当且仅当两个不同的Pipeline使用了同一数据对象才有此警告？");
        }
        qua2Object.put(config, obj);
    }

    public Object get(Integer type) {
        return get(type, NO_CONFIG);
    }

    public Object get(Integer type, AlgorithmConfig config) {
        if (cacheMap.get(type) == null) return null;
        if (cacheMap.get(type).get(config) == null) return null;
        return cacheMap.get(type).get(config);
    }

}
