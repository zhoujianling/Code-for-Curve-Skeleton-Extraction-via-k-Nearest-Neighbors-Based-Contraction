package cn.edu.cqu.graphics.ui;


import cn.edu.cqu.graphics.config.SpringContext;
import cn.edu.cqu.graphics.platform.ExperimentPlatform;
import cn.edu.cqu.graphics.ui.form.MainForm;

import javax.swing.*;

import static javax.swing.UIManager.getSystemLookAndFeelClassName;

public class Entrance {

    public static void main(String[] args) {
        /**
        Map<Long, Long> map = new HashMap<>();
        List<Long> keys = new Vector<>();
        Random r = new Random(System.currentTimeMillis());
        for (int j = 0; j < 500000; j ++) {
            keys.add(r.nextLong());
        }
        for (int i = 0; i < 3; i ++) {
            new Thread(() -> {
                map.clear();
                for (Long k : keys) {
                    map.put(k, Long.valueOf(k + 1));
                }
                int cnt = 5;
                for (Long key : map.keySet()) {
                    if (cnt -- > 0) {
                        System.out.println("key: " + key);
                    }
                }
                for (int j = 0; j < 300; j ++) {
                    map.remove(keys.get(j));
                }
                System.out.println(">>>>>>>>>>>>>>>>>");
                System.out.flush();
            }).start();
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
         **/


//        new Scanner(System.in).nextLine(); // in order to attach profiler
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

//        LvpPipeline lvpPipeline = context.getBean(LvpPipeline.class);
//        lvpPipeline.loadPointCloud();
        ExperimentPlatform platform = SpringContext.instance().getBean(ExperimentPlatform.class);
        platform.init();

        MainForm form = context.getBean(MainForm.class);
        form.init();

//        ShowSkeleton skeleton = context.getBean(ShowSkeleton.class);
//        skeleton.init3DConfig();
//        skeleton.setVisible(true);

//        ShowTree tree = context.getBean(ShowTree.class);
//        tree.init3DConfig();
//        tree.setVisible(true);

//        ShowFission fission = context.getBean(ShowFission.class);
//        fission.init3DConfig();
//        fission.setVisible(true);

//        ShowVertex vertex = context.getBean(ShowVertex.class);
//        vertex.init3dConfig();
//        vertex.setVisible(true);


    }
}
