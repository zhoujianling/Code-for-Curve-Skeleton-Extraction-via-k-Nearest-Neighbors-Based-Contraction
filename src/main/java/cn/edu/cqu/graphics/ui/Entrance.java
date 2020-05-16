package cn.edu.cqu.graphics.ui;


import cn.edu.cqu.graphics.config.SpringContext;
import cn.edu.cqu.graphics.platform.ExperimentPlatform;
import cn.edu.cqu.graphics.ui.form.MainForm;

import javax.swing.*;

import static javax.swing.UIManager.getSystemLookAndFeelClassName;

public class Entrance {

    public static void main(String[] args) {

        System.out.println("Current thread: " + Thread.currentThread().getName());
        try {
            System.out.println("Loading...");
            UIManager.setLookAndFeel(getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException
                | InstantiationException
                | IllegalAccessException
                | UnsupportedLookAndFeelException e) {
            e.printStackTrace();
        }

        SpringContext context = SpringContext.instance();

        ExperimentPlatform platform = SpringContext.instance().getBean(ExperimentPlatform.class);
        platform.init();

        MainForm form = context.getBean(MainForm.class);
        form.init();

    }
}
