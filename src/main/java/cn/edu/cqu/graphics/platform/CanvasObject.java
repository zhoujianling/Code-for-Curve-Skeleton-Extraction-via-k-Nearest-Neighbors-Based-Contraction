package cn.edu.cqu.graphics.platform;

import cn.edu.cqu.graphics.config.AlgorithmConfig;
import cn.edu.cqu.graphics.protocol.CanvasAttr;

import java.lang.annotation.Annotation;

public class CanvasObject {
    private String name;
    private Integer type;
    private Object data;
    private Object secondaryData;
    private AlgorithmConfig config = MemCachePool.NO_CONFIG;
    private boolean visible = true;
    private CanvasAttr attr = null;
    private transient boolean changed = false;

    public CanvasObject(String name) {
        this.name = name;
    }

    public CanvasObject(String name, Integer type, Object data, AlgorithmConfig c ) {
        this.name = name;
        this.type = type;
        this.data = data;
        this.config = c;
        this.attr = new CanvasAttr() {

            @Override
            public Class<? extends Annotation> annotationType() {
                return CanvasAttr.class;
            }

            @Override
            public int[] primaryColor() {
                return new int[] {0, 0, 255};
            }

            @Override
            public int[] secondaryColor() {
                return new int[] {255, 0, 0};
            }

            @Override
            public int primarySize() {
                return 3;
            }
        };
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }


    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public boolean hasChanged() {return changed;}

    public void setChanged(boolean c) {this.changed = c;}

    public void setConfig(AlgorithmConfig config) {
        this.config = config;
    }

    public AlgorithmConfig getConfig() {
        return config;
    }

    public void setAttr(CanvasAttr attr) {
        this.attr = attr;
    }

    public CanvasAttr getAttr() {
        return attr;
    }

    public Object getSecondaryData() {
        return secondaryData;
    }

    public void setSecondaryData(Object secondaryData) {
        this.secondaryData = secondaryData;
    }

}
