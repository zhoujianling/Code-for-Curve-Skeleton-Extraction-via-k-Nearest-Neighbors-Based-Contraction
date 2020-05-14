package cn.edu.cqu.graphics.ui.form;

import cn.edu.cqu.graphics.config.PlatformConfig;
import cn.edu.cqu.graphics.platform.PlatformGlobalManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.logging.Logger;

@Component
public class PreferenceForm extends JFrame {

    PlatformConfig config;

    private JCheckBox enableDiskCacheCheckBox;
    private JButton cancelButton;
    private JButton OKButton;
    private JButton applyButton;
    private JPanel container;

    @Autowired
    private Logger logger;

    @Autowired
    public PreferenceForm(PlatformConfig config) {
        this.config = config;
        init();
    }

    public void init() {
        initWindow();
        initEvent();
    }

    private void initWindow() {
        Dimension dimen = Toolkit.getDefaultToolkit().getScreenSize();
        setSize(new Dimension((int)(dimen.getWidth() * 0.5), (int)(dimen.getHeight() * 0.5)));
        setContentPane(container);
        enableDiskCacheCheckBox.setSelected(config.getEnableCache());
    }

    private void initEvent() {
        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                PreferenceForm.this.setVisible(false);
            }
        });
        applyButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                config.setEnableCache(enableDiskCacheCheckBox.isSelected());
                try {
                    saveConfig();
                } catch (IOException e1) {
                    e1.printStackTrace();
                    JOptionPane.showMessageDialog(PreferenceForm.this, e1.getMessage());
                }
            }
        });
        OKButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                config.setEnableCache(enableDiskCacheCheckBox.isSelected());
                try {
                    saveConfig();
                } catch (IOException e1) {
                    e1.printStackTrace();
                    JOptionPane.showMessageDialog(PreferenceForm.this, e1.getMessage());
                }
                PreferenceForm.this.setVisible(false);
            }
        });

    }

    private void saveConfig() throws IOException {
        PlatformGlobalManager.cachePlatformConfig(config, logger);
    }

}
