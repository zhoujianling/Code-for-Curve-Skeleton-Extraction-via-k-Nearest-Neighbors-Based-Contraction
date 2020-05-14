package cn.edu.cqu.graphics.config;

/**
 * 由 {@link SpringBaseConfig 提供bean}
 * 其他可配置项：日志区文字，截屏选项（大小，存储位置等），APP目录
 * 
 */
public class PlatformConfig {

    public static final String JSON_NAME = "PlatformConfig.json";
    private String recentPath = null;
    private Boolean enableCache = false;

    public String getRecentPath() {
        return recentPath;
    }

    public void setRecentPath(String recentPath) {
        this.recentPath = recentPath;
    }

    public Boolean getEnableCache() {
        return enableCache;
    }

    public void setEnableCache(Boolean enableCache) {
        this.enableCache = enableCache;
    }
}
