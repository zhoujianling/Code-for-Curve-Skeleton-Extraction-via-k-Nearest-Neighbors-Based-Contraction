package cn.edu.cqu.graphics.ui;

import cn.edu.cqu.graphics.config.AlgorithmConfig;
import cn.edu.cqu.graphics.protocol.CachedPipe;
import cn.edu.cqu.graphics.protocol.Param;
import cn.edu.cqu.graphics.protocol.Pipeline;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.lang.reflect.Field;
import java.util.*;
import java.util.logging.Logger;

@Component
public class ConfigUIGenerator {

    @Autowired
    Logger logger;

    /**
     *
     * @param panel 用户导入数据或点击导入配置按钮时，这一刻的 configPanel
     * @param externalConfig 外部json，参见 CacheGenerator。
     */
    public void loadConfig(JPanel panel, AlgorithmConfig externalConfig) {
        if (externalConfig == null) return;
        for (java.awt.Component component : panel.getComponents()) {
            if (component instanceof JLabel) {
                continue;
            }
            if (component instanceof JComponent) {
                JComponent jc = ((JComponent) component);
                String key = jc.getToolTipText();
                if (key == null || key.length() < 1) continue;
                if (jc instanceof JSpinner) {
                    JSpinner js = ((JSpinner) jc);
                    Integer intVal = externalConfig.getIntegerParam(key);
                    Double doubleVal = externalConfig.getDoubleParam(key);
                    if (intVal != null) {
                        js.setValue(intVal);
                    } else if (doubleVal != null){
                        js.setValue(doubleVal);
                    } else {
                        logger.warning("发生了奇怪了事情。请检查 json 或代码。Config Item: " + key);
                    }
                } else if (jc instanceof JCheckBox) {
                    boolean val = externalConfig.getBooleanParam(key);
                    JCheckBox jcb = ((JCheckBox) jc);
                    jcb.setSelected(val);
                }
            }
        }
        logger.info("根据外部json修改了当前算法配置。");
    }

    /**
     * int shot long 类型全部转 Integer 的 JSpinner
     * float double 类型的参数全部转 Double 的 JSpinner
     * boolean 类型的参数转 JCheckBox
     * String 类型暂时没有适配
     * @param panel
     * @param pipeline
     */
    public void generate(JPanel panel, Pipeline pipeline) {
        Set<String> keys = new HashSet<>();
        try {
            for (CachedPipe pipe : pipeline.getSteps()) {
                List<Field> fields = pipe.getFields();
                for (Field field: fields) {
                    field.setAccessible(true);
                    if (isParamField(field)) {
                        Param param = field.getAnnotation(Param.class);
                        if (keys.contains(param.key())) {
                            continue;
                        } else {
                            keys.add(param.key());
                        }
                        if (field.getType() == Integer.class) {
                            generateIntegerSpinner(panel, field, pipe, pipeline);
                        } else if (field.getType() == Float.class || field.getType() == Double.class) {
                            double val = 0;
                            if (field.getType() == Float.class) {
                                Float floatVal = (Float) field.get(pipe);
                                val = floatVal.doubleValue();
                            } else if (field.getType() == Double.class) {
                                val = (Double) field.get(pipe);
                            }
                            generateDoubleSpinner(panel, param, val, pipeline);
                        } else if ( field.getType() == Double.class) {
                            Double val = (Double) field.get(pipe);
                            generateDoubleSpinner(panel, param, val, pipeline);
                        } else if (field.getType() == String.class) {

                        } else if (field.getType() == Boolean.class) {
                            generateCheckBox(panel, field, pipe, pipeline);
                        } else {
                            logger.warning("未识别的配置类字段。Config Item: " + param.comment());
                            logger.info("是否int而不是Integer?");
                        }
                    }
                }
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }


    private void generateCheckBox(JPanel panel, Field field, Object object, Pipeline pipeline) throws IllegalAccessException {
        boolean checked = (boolean) field.get(object);
        Param param = field.getAnnotation(Param.class);
        pipeline.getConfig().putBooleanParam(param.key(), checked);

        JCheckBox checkBox = new JCheckBox();
        checkBox.setSelected(checked);
        checkBox.setToolTipText(param.key());
        checkBox.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                pipeline.getConfig().putBooleanParam(param.key(), checkBox.isSelected());
            }
        });

        JLabel label = new JLabel(param.comment());
        label.setLabelFor(checkBox);
        panel.add(label);
        panel.add(checkBox);
    }

    private void generateDoubleSpinner(JPanel panel, Param param, double currentVal, Pipeline pipeline) {
        JSpinner spinner = new JSpinner();
        spinner.setModel(new SpinnerNumberModel(currentVal, param.minVal(), param.maxVal(), 0.05));
        spinner.setToolTipText(param.key());
        spinner.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                pipeline.getConfig().putDoubleParam(param.key(), (Double) spinner.getValue());
            }
        });

        JLabel label = new JLabel(param.comment());
        label.setLabelFor(spinner);

        panel.add(label);
        panel.add(spinner);
    }

    private void generateIntegerSpinner(JPanel panel, Field field, Object obj, Pipeline pipeline) throws IllegalAccessException {
        int currentVal = (int) field.get(obj);
        Param param = field.getAnnotation(Param.class);
        pipeline.getConfig().putIntegerParam(param.key(), currentVal);

        JSpinner spinner = new JSpinner();
        spinner.setToolTipText(param.key());
        spinner.setModel(new SpinnerNumberModel(currentVal, param.minVal(), param.maxVal(), 1));
        spinner.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                pipeline.getConfig().putIntegerParam(param.key(), (Integer) spinner.getValue());
            }
        });

        JLabel label = new JLabel(param.comment());
        label.setLabelFor(spinner);

        panel.add(label);
        panel.add(spinner);
    }

    private List<Field> fetchAllFields(AlgorithmConfig config) {
        List<Field> fieldList = new ArrayList<>() ;
        Class tempClass = config.getClass();
        while (tempClass != null) {//当父类为null的时候说明到达了最上层的父类(Object类).
            fieldList.addAll(Arrays.asList(tempClass.getDeclaredFields()));
            tempClass = tempClass.getSuperclass(); //得到父类,然后赋给自己
        }
        return fieldList;
    }

    private boolean isParamField(Field f) {
        return f.getAnnotation(Param.class) != null;
    }

}
