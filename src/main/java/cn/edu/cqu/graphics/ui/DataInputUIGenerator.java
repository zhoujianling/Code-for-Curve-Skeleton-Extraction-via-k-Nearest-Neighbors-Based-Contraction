package cn.edu.cqu.graphics.ui;

import cn.edu.cqu.graphics.platform.ExperimentPlatform;
import cn.edu.cqu.graphics.protocol.AlgorithmInput;
import cn.edu.cqu.graphics.protocol.Pipeline;
import cn.edu.cqu.graphics.util.ReflectUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.lang.reflect.Field;
import java.util.List;

@Component
public class DataInputUIGenerator {


    @Autowired
    ExperimentPlatform platform;

    @Autowired
    ConfigUIGenerator configUIGenerator;

    public void generate(JPanel panel, Pipeline pipeline) {
        List<Field> fields = ReflectUtil.fetchAllFields(pipeline);
        for (Field field : fields) {
            if (isAlgorithmInput(field)) {
                AlgorithmInput input = field.getAnnotation(AlgorithmInput.class);
                generateInput(panel, input, field, pipeline);
            }
        }
    }

    private void generateInput(JPanel panel, AlgorithmInput input, Field field, Pipeline pipeline) {
        JLabel label = new JLabel(input.name());
        JTextField textField = new JTextField();
        label.setLabelFor(textField);

        JButton button = new JButton("...");
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser chooser = new JFileChooser();
                String dirName = platform.getConfig().getRecentPath();
                if (dirName != null) {
                    File dir = new File(dirName);
                    if (dir.exists()) {
                        chooser.setCurrentDirectory(dir);
                    }
                }
                chooser.setDialogType(JFileChooser.OPEN_DIALOG);
                chooser.addChoosableFileFilter(new FileFilter() {
                    @Override
                    public boolean accept(File f) {
                        return true;
                    }

                    @Override
                    public String getDescription() {
                        return "*.txt";
                    }
                });
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    File file = chooser.getSelectedFile();
                    platform.loadPipelineInput(file, input, pipeline, field);

                }

            }
        });

        panel.add(label);
        panel.add(textField);
        panel.add(button);
    }

    private boolean isAlgorithmInput(Field f) {
        return f.getAnnotation(AlgorithmInput.class) != null;
    }

}
