package cn.edu.cqu.graphics.protocol;

import cn.edu.cqu.graphics.config.AlgorithmConfig;
import cn.edu.cqu.graphics.platform.CanvasObject;
import cn.edu.cqu.graphics.platform.CanvasObjectListModel;
import cn.edu.cqu.graphics.platform.MemCachePool;
import cn.edu.cqu.graphics.platform.PlatformGlobalManager;
import cn.edu.cqu.graphics.util.ReflectUtil;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public abstract class Pipeline {

    protected CanvasObjectListModel model = new CanvasObjectListModel();

    protected AlgorithmConfig dynamicConfig = new AlgorithmConfig();

    protected List<CachedPipe> steps = new ArrayList<>();

    //暂存一下结果，实际所有渲染仍然读取model,初衷是clear model，但是这样会把pipeline input一起清掉，所以用个笨办法
    private List<CanvasObject> pipelineRenderList = new Vector<>();

    private Thread thread = null;

    private AtomicBoolean suspended = new AtomicBoolean(false);

    @Autowired
    MemCachePool pool;

    @Autowired
    Logger logger;

    @Autowired
    PlatformGlobalManager generator;

    public boolean stop() {
        if (thread == null) return false;
        thread.stop();
        logger.info("Pipeline已终止");
        thread = null;
        return true;
    }

    public void suspend() {
        if (thread == null) return;
        thread.suspend();
        suspended.set(true);
        logger.info("Pipeline已挂起，请勿切换 Pipeline");
    }

    public void resume() {
        if (thread == null) return;
        thread.resume();
        suspended.set(false);
        logger.info("Pipeline已恢复");
    }

    public void start(OnPipelineFinishListener listener) {
        if (thread != null) {
            logger.warning("点击 RUN 时已存在线程。");
            stop();
        }

        thread = new Thread(() -> {
            Pipeline.this.apply(listener);
            thread = null;
        });
        thread.start();
    }

    public void apply(OnPipelineFinishListener listener) {

        try {
            clearOutput();
            writeDataSource();

            for (CachedPipe p : steps) {
                p.setPipeline(this);
            }

            for (Pipe s : steps) {
                long start = System.currentTimeMillis();
                s.run(); // step真正执行
                long end = System.currentTimeMillis();
                String l = String.format(Locale.getDefault(),"The step: %s, the time cost: %.4f s", s.getName(), (end - start) / 1000.0f);
                logger.info(l);
            }

            listener.onFinished();
        } catch (Exception e) {
            e.printStackTrace();
            listener.onError(e);
        }
    }

    /**
     * 先初始化，再生成 configPanel
     */
    public void init() {
        makeSteps();
    }

    /**
     * pipeline有可能不在“干净”的状态下开始
     * 不清空MemCachePool
     *
     */
    public void clearEveryThing() {
        steps.clear();
        model.clear();
        pipelineRenderList.clear();
    }

    private void clearOutput() {
        for (CanvasObject object : pipelineRenderList) {
            model.removeCanvasObject(object);
        }
        pipelineRenderList.clear();
    }

    /**
     * 把算法所需要的输入数据全部写进去
     */
    private void writeDataSource() {
        List<Field> list = ReflectUtil.fetchAllFields(this);
        try {
            for (Field field : list) {
                field.setAccessible(true);
                AlgorithmInput path = field.getAnnotation(AlgorithmInput.class);
                if (path != null) {
                    Object val = field.get(this);
                    if (val == null) {
                        throw new IllegalStateException("数据不对头。");
                    } else {
                        pool.put(path.name().hashCode(), getConfig(), val);
                    }
                }
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    public void cacheConfig() throws IOException {
        generator.cacheOptimalConfig(getPrimaryInput(), this);
    }


    /**
     * 装填pipeline
     */
    protected abstract void makeSteps();

    public AlgorithmConfig getConfig() {
        return dynamicConfig;
    }

    public CanvasObjectListModel getModel() {
        return model;
    }

    public abstract InputData getPrimaryInput();

    public List<CachedPipe> getSteps() {
        return steps;
    }

    public abstract String name();

    public List<CanvasObject> getPipelineRenderList() {
        return pipelineRenderList;
    }

    public Boolean isSuspended() {
        if (thread == null) return null;
        return suspended.get();
    }
}
