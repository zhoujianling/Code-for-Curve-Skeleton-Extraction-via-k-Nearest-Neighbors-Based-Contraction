package cn.edu.cqu.graphics.platform;

import cn.edu.cqu.graphics.config.AlgorithmConfig;
import cn.edu.cqu.graphics.config.PlatformConfig;
import cn.edu.cqu.graphics.config.SpringContext;
import cn.edu.cqu.graphics.pipeline.*;
import cn.edu.cqu.graphics.protocol.AlgorithmInput;
import cn.edu.cqu.graphics.protocol.Pipeline;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Logger;

@Component
public class ExperimentPlatform implements DataImporter, Observer{

    @Autowired
    private PlatformConfig config = null;

    @Autowired
    private PlatformGlobalManager generator;

    @Autowired
    DataLoader loader;

    @Autowired
    private Logger logger;

    private List<Pipeline> pipelineList = new ArrayList<>();
    private List<PlatformFrontEnd> frontEnds = new ArrayList<>();
    private int currentPipelineIndex = 0;

    private void initPipeline() {
        WLOPDemoPipeline wlop = SpringContext.instance().getBean(WLOPDemoPipeline.class);
        IterativeUpdatePipeline cosinePipeline = SpringContext.instance().getBean(IterativeUpdatePipeline.class);
        pipelineList.add(wlop);
        pipelineList.add(cosinePipeline);

        for (Pipeline p : pipelineList) {
            p.init();
        }
    }

    public void init() {
        initPipeline();
        for (Pipeline pipeline : pipelineList) {
            pipeline.getModel().addObserver(this);
        }
    }

    public void addFrontEnd(PlatformFrontEnd frontEnd) {
        frontEnds.add(frontEnd);
    }

    public void removeObjects(List<CanvasObject> objects) {
        for (CanvasObject object : objects) {
            getCurrentModel().getData().remove(object);
        }
        notifyFrontEndUpdate();
    }

    public int getCurrentPipelineIndex() {
        return currentPipelineIndex;
    }

    public void setCurrentPipelineIndex(int index) {
        this.currentPipelineIndex = index;
        notifyFrontEndUpdate();
    }

    public Pipeline getCurrentPipeline() {
        return pipelineList.get(currentPipelineIndex);
    }

    public List<Pipeline> getPipelineList() {
        return pipelineList;
    }

    public PlatformConfig getConfig() {
        return config;
    }

    public CanvasObjectListModel getCurrentModel() {
        return getCurrentPipeline().getModel();
    }

    /**
     * 往pipeline中导入数据
     * 前端指定文件、数据类型(点云、骨架还是mesh?)和数据格式(什么格式的点云？ply,energy?)即可
     */
    @Override
    public void loadData(File file, int type, int format) {
        loader.loadData(file, type, new OnLoadDataListener() {
            @Override
            public void onLoadData(Object data) {
                CanvasObject object = new CanvasObject(file.getName(), type, data, getCurrentPipeline().getConfig());
                getCurrentModel().addCanvasObject(object);
            }

            @Override
            public void onError(String message) {
                for (PlatformFrontEnd end : frontEnds) {
                    end.onError(new IOException(message));
                }
            }
        });

    }


    @Override
    public void loadPipelineInput(File file, AlgorithmInput input, Pipeline pipeline, Field field) {
        loader.loadData(file, input.type(), new OnLoadDataListener() {
            @Override
            public void onLoadData(Object data) {
                //从文件读取数据
                try {
                    boolean primaryInputIsNullBefore = (pipeline.getPrimaryInput() == null);
                    field.setAccessible(true);
                    field.set(pipeline, data);
                    boolean primaryInputIsNotNullNow = (pipeline.getPrimaryInput() != null);

                    //渲染数据
                    CanvasObject object = new CanvasObject(input.name(), input.type(), data, pipeline.getConfig());
                    pipeline.getModel().addCanvasObject(object);

                    //缓存最近的路径
                    config.setRecentPath(file.getParent());
                    generator.cachePlatformConfig(config, logger);
                    //自动读取之前的json配置
                    if (primaryInputIsNullBefore && primaryInputIsNotNullNow) {
                        loadPreviousAlgorithmConfig(pipeline);
                    }
                } catch (IllegalAccessException | FileNotFoundException e) {
                    e.printStackTrace();
                }

            }

            @Override
            public void onError(String message) {
                for (PlatformFrontEnd end : frontEnds) {
                    end.onError(new IOException(message));
                }
            }
        });
    }

    private void loadPreviousAlgorithmConfig(Pipeline pipeline) throws FileNotFoundException {
        AlgorithmConfig previousConfig = generator.extractPreviousOptimalConfig(pipeline.getPrimaryInput(), pipeline);
        for (PlatformFrontEnd end : frontEnds) {
            end.updateConfigPanel(previousConfig);
        }
    }

    /**
     * 实验平台后端在这里处于一个中间层的位置，前端完全只负责呈现。
     * @param o
     * @param arg
     */
    @Override
    public void update(Observable o, Object arg) {
        if (o != getCurrentPipeline().getModel()) {
            return;
        }
        notifyFrontEndUpdate();
    }

    private void notifyFrontEndUpdate() {
        for (PlatformFrontEnd end : frontEnds) {
            end.updateUI();
        }
    }

    public void notifyFrontEndSaveSnapshot(String keyword) {
        for (PlatformFrontEnd end : frontEnds) {
            end.snapshot(keyword);
        }

    }

}
