package cn.edu.cqu.graphics.platform;

import cn.edu.cqu.graphics.protocol.AlgorithmInput;
import cn.edu.cqu.graphics.protocol.Pipeline;

import java.io.File;
import java.lang.reflect.Field;

/**
 * 自动根据文件后缀名识别文件格式，但是还是要保留指定格式的API
 * 格式的相关常数也要保留
 */
public interface DataImporter {
    void loadData(File file, int type, int format);
    void loadPipelineInput(File file, AlgorithmInput input, Pipeline pipeline, Field field);

}
