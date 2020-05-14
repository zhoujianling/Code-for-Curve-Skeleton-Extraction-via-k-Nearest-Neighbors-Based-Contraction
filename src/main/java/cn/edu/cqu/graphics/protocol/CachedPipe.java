package cn.edu.cqu.graphics.protocol;

import cn.edu.cqu.graphics.config.PlatformConfig;
import cn.edu.cqu.graphics.platform.CanvasObject;
import cn.edu.cqu.graphics.platform.MemCachePool;
import cn.edu.cqu.graphics.platform.PlatformGlobalManager;
import cn.edu.cqu.graphics.util.ReflectUtil;
import com.sun.istack.internal.Nullable;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.List;
import java.util.logging.Logger;


public abstract class CachedPipe implements Pipe {

    private boolean readFromCache = false;
    private List<Field> fields = ReflectUtil.fetchAllFields(this);
    protected Pipeline pipeline;

    @Autowired
    MemCachePool memCachePool;

    @Autowired
    PlatformConfig config;

    @Autowired
    PlatformGlobalManager generator;

    @Autowired
    Logger logger;

//    InputData pointCloud;

//    protected CanvasObjectListModel model;

    public void run() throws FileNotFoundException {
        before();
        if (! config.getEnableCache() || (! readFromCache)) {
            apply();
        }
        after();
    }

    /**
     * 检查 PipeOutput 是否已经被缓存，如果已经被缓存，跳过 loadPointCloud()
     * 另一个问题就是，重新实例化，会导致后续步骤引用的 JavaBean 还是旧的实例
     */
    public void before() {
        try {
            readAlgorithmParam();
            readPreviousPipeInput();
            checkInput();
            if (config.getEnableCache()) {
                readDiskCache();
            }
        } catch (IllegalAccessException | IOException | ClassNotFoundException e) {
            System.out.println("Exception: " + e.getMessage());
            readFromCache = false;
//            e.printStackTrace();
        }

    }

    private void readAlgorithmParam() {
        try {
            for (Field field : fields) {
                field.setAccessible(true);
                if (isParam(field)) {
                    Param param = field.getAnnotation(Param.class);
                    if (field.getType() == Integer.class) {
                        Integer val = pipeline.dynamicConfig.getIntegerParam(param.key());
                        if (val != null) {
                            field.set(this, val);
                        }
                    } else if (field.getType() == Boolean.class) {
                        Boolean val = pipeline.dynamicConfig.getBooleanParam(param.key());
                        if (val != null) {
                            field.set(this, val);
                        }
                    } else if (field.getType() == Double.class) {
                        Double val = pipeline.dynamicConfig.getDoubleParam(param.key());
                        if (val != null) {
                            field.set(this, val);
                        }
                    }
                }
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    private void readDiskCache() throws IllegalAccessException, IOException, ClassNotFoundException {
        for (Field field : fields) {
            if (isOutput(field)) {
                if (! hasEnabledDiskCache(field)) {
                    readFromCache = false;
                    break;
                }
                field.setAccessible(true);
                PipeOutput pipeOutput = field.getAnnotation(PipeOutput.class);
                Object val = generator.readCache(pipeline.getPrimaryInput(), pipeline,
                        pipeOutput.name(), field.getType());
                field.set(this, val);
                readFromCache = true;
            } else if (isInput(field)) {
                if (field.getAnnotation(PipeInput.class).willBeModified()) {
                    readFromCache = false;
                    break;
                }
            }
        }

    }


    private void readPreviousPipeInput() throws IllegalAccessException {
        for (Field field : fields) {
            if (isMemCache(field)) {
                field.setAccessible(true);
                FromPreviousOutput cache = field.getAnnotation(FromPreviousOutput.class);
                field.set(this, memCachePool.get(cache.name().hashCode(), pipeline.getConfig()));
            }
        }
    }

    private void checkInput() throws IllegalAccessException {
        for (Field field : fields) {
            if (isInput(field)) {
                if (field == null) {
                    throw new RuntimeException("当前管道检测输入项不通过。");
                }
            }
        }
    }

    /**
     * 计算完成，写入cache
     * 所有Output全部写入MemCache?
     */
    public void after() {

        try {
            writeOutput();

            if (config.getEnableCache() && (!readFromCache)) {
                writeDiskCache();
            }
        } catch (IllegalAccessException | IOException e) {
            e.printStackTrace();
        }
    }

    private void writeDiskCache() throws IllegalAccessException, IOException {
        for (Field field : fields) {
            if (isOutput(field)) {
                if (! hasEnabledDiskCache(field)) {
                    break;
                }
                field.setAccessible(true);
                Object obj = field.get(this);
                PipeOutput pipeOutput = field.getAnnotation(PipeOutput.class);
                generator.makeCache(pipeline, pipeline.getPrimaryInput(), obj, pipeOutput.name());
            }
        }

    }

    /**
     * 写管道输出
     * 将需要渲染的物体写到CanvasObjectModel里
     * @throws IllegalAccessException
     */
    private void writeOutput() throws IllegalAccessException {
        for (Field field : fields) {
            if (isOutput(field)) {
                field.setAccessible(true);
                PipeOutput output = field.getAnnotation(PipeOutput.class);

                memCachePool.put(output.name().hashCode(), pipeline.getConfig(), field.get(this));
                if (output.visualize()) {
                    CanvasObject object = new CanvasObject(output.name(), output.type(), field.get(this), pipeline.getConfig());
                    object.setVisible(output.visible());

                    if (field.getAnnotation(CanvasAttr.class) != null) {
                        CanvasAttr attr = field.getAnnotation(CanvasAttr.class);
                        object.setAttr(attr);
                    } else {
                        object.setAttr(defaultAttr());
                    }

                    pipeline.getModel().addCanvasObject(object);
                    pipeline.getPipelineRenderList().add(object);
//                    model.addCanvasObject(object);
                }
            } else if (isInput(field)) {
                PipeInput input = field.getAnnotation(PipeInput.class);
                if (input.willBeModified()) { //如果数据被修改了，又已经被渲染了，就需要修改数据！
                    pipeline.getModel().updateCanvasObject(field.get(this));
                }
            }
        }
    }

    private CanvasAttr defaultAttr() {
        return attr(new int[] {0, 0, 255}, new int[] {125, 125, 125});
    }

    protected CanvasAttr attr(@Nullable int[] primaryColor) {
        return attr(primaryColor, null);
    }

    protected CanvasAttr attr(@Nullable int[] primaryColor, @Nullable int[] secondaryColor) {
        return attr(primaryColor, secondaryColor, null);
    }
    /**
     * 如果用户想在代码中构造 CanvasObject，又想指定一些属性的话，可以调用这个方法。
     * @return
     */
    protected CanvasAttr attr(@Nullable int[] primaryColor, @Nullable int[] secondaryColor, @Nullable Integer primarySize) {
        int[] defaultPrimaryColor = new int[] {0, 0, 255};
        int[] defaultSecondaryColor = new int[] {255, 0, 0};
        int[] defaultSize = new int[] {3};
        if (primaryColor != null) {
            defaultPrimaryColor = primaryColor;
        }
        if (secondaryColor != null) {
            defaultSecondaryColor = secondaryColor;
        }
        if (primarySize != null) {
            defaultSize[0] = primarySize;
        }
        int[] finalDefaultPrimaryColor = defaultPrimaryColor;
        int[] finalDefaultSecondaryColor = defaultSecondaryColor;
        return new CanvasAttr() {
            @Override
            public boolean equals(Object obj) {
                return false;
            }

            @Override
            public int hashCode() {
                return 0;
            }

            @Override
            public String toString() {
                return "用户自定义的CanvasAttr";
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return CanvasAttr.class;
            }

            @Override
            public int[] primaryColor() {
                return finalDefaultPrimaryColor;
            }

            @Override
            public int[] secondaryColor() {
                return finalDefaultSecondaryColor;
            }

            @Override
            public int primarySize() {
                return defaultSize[0];
            }
        };
    }

    private boolean isMemCache(Field field) {
        return field.getAnnotation(FromPreviousOutput.class) != null;
    }

    private boolean isInput(Field field) {
        return field.getAnnotation(PipeInput.class) != null;
    }

    private boolean isOutput(Field field) {
        return field.getAnnotation(PipeOutput.class) != null;
    }

    private boolean isParam(Field field) {
        return field.getAnnotation(Param.class) != null;
    }

    private boolean hasEnabledDiskCache(Field field) {
        return field.getAnnotation(EnableDiskCache.class) != null;
    }

    public void setPipeline(Pipeline pipeline) {this.pipeline = pipeline;}

    public List<Field> getFields() {
        if (fields == null) fields = ReflectUtil.fetchAllFields(this);
        return fields;
    }
}
