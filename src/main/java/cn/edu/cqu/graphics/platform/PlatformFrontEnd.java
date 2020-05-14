package cn.edu.cqu.graphics.platform;

import cn.edu.cqu.graphics.config.AlgorithmConfig;

public interface PlatformFrontEnd {
    void onError(Exception exception);
    void updateUI();
    void snapshot(String keyword);
    void updateConfigPanel(AlgorithmConfig externalConfig);
}
