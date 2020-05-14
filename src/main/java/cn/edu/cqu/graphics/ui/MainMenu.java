package cn.edu.cqu.graphics.ui;

import cn.edu.cqu.graphics.config.SpringContext;
import cn.edu.cqu.graphics.platform.PlatformGlobalManager;
import cn.edu.cqu.graphics.ui.form.MainForm;
import cn.edu.cqu.graphics.ui.form.PreferenceForm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.logging.Logger;

@Component
public class MainMenu extends JMenuBar{

    @Autowired
    private MainForm window;

    @Autowired
    private Logger logger;

    public MainMenu() {
        JMenu files = new JMenu("File");
        JMenu tools = new JMenu("Tools");
        JMenu help = new JMenu("Help");
        JMenuItem importDataItem = new JMenuItem("Import Model");
        JMenuItem clearCacheItem = new JMenuItem("Clear Cache");
        JMenuItem settingsItem = new JMenuItem("Preference");
        JMenuItem aboutItem = new JMenuItem("About");
        importDataItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

            }
        });
        settingsItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                SpringContext.instance().getBean(PreferenceForm.class).setVisible(true);
            }
        });
        clearCacheItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    PlatformGlobalManager.clearAlgorithmCache();
                    logger.info("Clear app cache successfully.");
                } catch (IOException e1) {
                    e1.printStackTrace();
                    logger.warning("Encounter problems when clearing app cache.");
                }
            }
        });
        files.add(importDataItem);
        help.add(aboutItem);
        tools.add(settingsItem);
        tools.add(clearCacheItem);

        add(files);
        add(tools);
        add(help);

    }
}
