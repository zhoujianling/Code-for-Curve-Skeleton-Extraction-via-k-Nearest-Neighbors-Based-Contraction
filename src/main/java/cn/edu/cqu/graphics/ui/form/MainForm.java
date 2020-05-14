package cn.edu.cqu.graphics.ui.form;

import cn.edu.cqu.graphics.config.AlgorithmConfig;
import cn.edu.cqu.graphics.platform.*;
import cn.edu.cqu.graphics.protocol.OnPipelineFinishListener;
import cn.edu.cqu.graphics.protocol.Pipeline;
import cn.edu.cqu.graphics.renderer.MainRenderer;
import cn.edu.cqu.graphics.ui.ConfigUIGenerator;
import cn.edu.cqu.graphics.ui.DataInputUIGenerator;
import cn.edu.cqu.graphics.ui.MainCanvas;
import cn.edu.cqu.graphics.ui.MainMenu;
import cn.edu.cqu.graphics.util.MathUtils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sun.j3d.utils.behaviors.mouse.MouseBehaviorCallback;
import com.sun.j3d.utils.universe.SimpleUniverse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.media.j3d.Transform3D;
import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import javax.swing.text.DefaultCaret;
import javax.vecmath.Matrix4d;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.image.BufferedImage;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;


@Component
public class MainForm extends JFrame implements PlatformFrontEnd {
    private JTabbedPane tabbedPane1;
    private JPanel rightPanel;
    private JPanel container;
    private JPanel canvas;
    private JTable branchList;
    /**数据类型和数据格式做区分**/
    private JButton loadDataButton;
    private JButton deleteButton;
    private JButton importConfigButton;
    private JButton skeletonizeButton;
    private JButton importMatrix;
    private JButton exportButton;
    private JButton exportConfigButton;
    private JPanel middlePanel;
    private JButton stopButton;
    private JButton suspendButton;
    private JLabel scaleRatio;
    private JLabel jvmMemory;
    private JTextArea loggerArea;
    private JPanel statusContainer;
    private JTable table1;
    private JButton saveSnapshotButton;
    private JButton clearLogButton;
    private SelectDataDialog dialog;

    private MainCanvas mainCanvas;

    private List<String> messages = new ArrayList<>();

    @Autowired
    ExperimentPlatform platform;

    @Autowired
    private MainRenderer renderer;

    @Autowired
    private ConfigUIGenerator generator;

    @Autowired
    private PlatformGlobalManager manager;

    @Autowired
    private MainMenu menu;

    @Autowired
    private DataInputUIGenerator dataInputUIGenerator;

    @Autowired
    Logger logger;


    private void initEvent() {
        addWindowListener(windowEventListener);
        dialog = new SelectDataDialog(this, platform);

        exportConfigButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    platform.getCurrentPipeline().cacheConfig();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        });

        skeletonizeButton.addActionListener(e -> {
            JPanel configPanel = (JPanel) tabbedPane1.getSelectedComponent();
            for (java.awt.Component c : configPanel.getComponents()) {
                c.setEnabled(false);
            }
            skeletonizeButton.setEnabled(false);
            suspendButton.setEnabled(true);
            stopButton.setEnabled(true);
            new Thread(() -> platform.getCurrentPipeline().start(new OnPipelineFinishListener() {
                @Override
                public void onFinished() {

                    for (java.awt.Component c : configPanel.getComponents()) {
                        c.setEnabled(true);
                    }
                    skeletonizeButton.setEnabled(true);
                    suspendButton.setEnabled(false);
                    stopButton.setEnabled(false);

                }

                @Override
                public void onError(Exception exception) {
                    MainForm.this.onError(exception);
                    for (java.awt.Component c : configPanel.getComponents()) {
                        c.setEnabled(true);
                    }
                    skeletonizeButton.setEnabled(true);
                    suspendButton.setEnabled(false);
                    stopButton.setEnabled(false);

                }
            })).start();
        });

        stopButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                boolean stopSuccess = platform.getCurrentPipeline().stop();
                if (! stopSuccess) {
                    return;
                }
                suspendButton.setEnabled(false);
                suspendButton.setText("Suspend");
                stopButton.setEnabled(false);
                skeletonizeButton.setEnabled(true);
                JPanel configPanel = (JPanel) tabbedPane1.getSelectedComponent();
                for (java.awt.Component c : configPanel.getComponents()) {
                    c.setEnabled(true);
                }
            }
        });

        suspendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Pipeline pipeline = platform.getCurrentPipeline();
                if (pipeline.isSuspended() == null) return;
                if (pipeline.isSuspended()) {
                    pipeline.resume();
                    suspendButton.setText("Suspend");
                } else {
                    pipeline.suspend();
                    suspendButton.setText("Resume");
                }
            }
        });

        loadDataButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dialog.setVisible(true);
            }
        });

        deleteButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int[] rows = branchList.getSelectedRows();
                List<CanvasObject> tobeDeleted = new Vector<>();
                CanvasObjectListModel model = platform.getCurrentModel();
                for (int i : rows) {
                    tobeDeleted.add(model.getData().get(i));
                }

                platform.removeObjects(tobeDeleted);
            }
        });

        tabbedPane1.addChangeListener(e -> platform.setCurrentPipelineIndex(tabbedPane1.getSelectedIndex()));

        double[] mat = new double[16];
        mainCanvas.setEvent(new MouseBehaviorCallback() {
            @Override
            public void transformChanged(int i, Transform3D transform3D) {
                transform3D.get(mat);
                double scale = (mainCanvas.getTz()) / (mainCanvas.getTz() - mat[11]);
                scaleRatio.setText(String.format("%.1f%%", scale * 100));
            }
        });

        exportButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Matrix4d matrix4d = mainCanvas.getCamera();
                List<Double> list = MathUtils.matrix2List(matrix4d);
                String json = new Gson().toJson(list);
                JFileChooser chooser = new JFileChooser();
                chooser.setDialogType(JFileChooser.SAVE_DIALOG);
                chooser.addChoosableFileFilter(new FileFilter() {
                    @Override
                    public boolean accept(File f) {
                        return f.getName().toLowerCase().endsWith(".matrix");
                    }

                    @Override
                    public String getDescription() {
                        return "*.matrix";
                    }
                });
                if (chooser.showSaveDialog(MainForm.this) == JFileChooser.APPROVE_OPTION) {
                    File file = chooser.getSelectedFile();
                    String fileName = chooser.getCurrentDirectory().getAbsolutePath() + "/" + file.getName() + (file.getName().endsWith(".matrix") ? "" : ".matrix");
                    try {
                        PrintStream ps = new PrintStream(new FileOutputStream(fileName));
                        ps.print(json);
                        ps.flush();
                        ps.close();
                        MainForm.this.logger.info("The matrix of camera has been written into " + fileName + ".");
                    } catch (FileNotFoundException e1) {
                        e1.printStackTrace();
                    }

                }
            }
        });

        clearLogButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                loggerArea.setText("");
            }
        });

        importMatrix.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser chooser = new JFileChooser();
                chooser.setDialogType(JFileChooser.OPEN_DIALOG);
                chooser.addChoosableFileFilter(new FileFilter() {
                    @Override
                    public boolean accept(File f) {
                        return f.getName().toLowerCase().endsWith(".matrix");
                    }

                    @Override
                    public String getDescription() {
                        return "*.matrix";
                    }
                });
                if (chooser.showOpenDialog(MainForm.this) == JFileChooser.APPROVE_OPTION) {
                    File file = chooser.getSelectedFile();
                    FileReader reader = null;

                    try {
                        reader = new FileReader(file);
                        Gson gson = new Gson();
                        List<Double> matrixList = gson.fromJson(reader, new TypeToken<List<Double>>(){}.getType());
                        Matrix4d matrix4d = MathUtils.list2Matrix(matrixList);
                        mainCanvas.setCamera(matrix4d);
                        MainForm.this.logger.info("Recover camera successfully.");
                    } catch (FileNotFoundException e1) {
                        e1.printStackTrace();
                    }

                }
            }
        });

        saveSnapshotButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                snapshot("From-Button");
            }
        });
    }

    @Override
    public void snapshot(String keyword) {
        new Thread(() -> {
            BufferedImage image = mainCanvas.snapshot(mainCanvas.getWidth(), mainCanvas.getHeight());
            if (image != null) {
                try {
                    manager.saveSnapshot(image, keyword);
                } catch (IOException e1) {
                    e1.printStackTrace();
                    logger.warning("Something wrong happened when writing image file.");
                    MainForm.this.onError(e1);
                }
                logger.info("Write image file successfully.");
            } else {
                logger.warning("Something wrong happened when capturing screen.");
            }
        }).start();

    }

    private void initData() {
        messages.clear();
    }

    public void init() {
        initData();
        initWindows();
//        initPipeline();
        platform.addFrontEnd(this);
        initTabPanel();


        platform.setCurrentPipelineIndex(tabbedPane1.getSelectedIndex());
        renderer.setModel(platform.getCurrentModel());
        branchList.setModel(platform.getCurrentModel());

        mainCanvas = new MainCanvas(SimpleUniverse.getPreferredConfiguration(), renderer.render());
        canvas.add(mainCanvas, BorderLayout.CENTER);

        initEvent();
        setVisible(true);
    }

    private void initTabPanel() {
        for (Pipeline pipeline : platform.getPipelineList()) {
            JPanel configPanel = new JPanel();
            configPanel.setLayout(new FlowLayout());
            dataInputUIGenerator.generate(configPanel, pipeline);
            generator.generate(configPanel, pipeline);

            tabbedPane1.add(pipeline.name(), configPanel);
        }

    }


    private void appendMessage(String message) {
        messages.add(message);
        loggerArea.append(message);
        int cnt = loggerArea.getLineCount();
//        double ratio = cnt * 1.0 / loggerArea.getRows();
        if (loggerArea.getRows() - cnt < 3) {
            loggerArea.setRows(loggerArea.getRows() + 20);
        }

    }

    private void initWindows() {
        setTitle("3D Model Processing Environment V1.0.0 Beta");
        try {
            ClassPathResource resource = new ClassPathResource("icon.png");
            ImageIcon imageIcon = new ImageIcon(resource.getURL());
            setIconImage(imageIcon.getImage());
        } catch (IOException e) {
            e.printStackTrace();
        }
        setContentPane(container);
        Dimension dimen = Toolkit.getDefaultToolkit().getScreenSize();
        setMinimumSize(new Dimension((int)(dimen.width * 0.85), (int)(dimen.height * 0.85)));
        setJMenuBar(menu);

        try {
            ClassPathResource resource = new ClassPathResource("delete.png");
            clearLogButton.setIcon(new ImageIcon(resource.getURL()));
        } catch (IOException e) {
            e.printStackTrace();
        }
        clearLogButton.setText("");
        clearLogButton.setBorder(BorderFactory.createEmptyBorder());
        clearLogButton.setContentAreaFilled(false);
        branchList.setRowHeight((int) (branchList.getRowHeight() * 1.3));
        branchList.setRowMargin(3);

        suspendButton.setEnabled(false);
        stopButton.setEnabled(false);

        loggerArea.setBackground(new Color(235, 235, 235));
        Font currentFont = loggerArea.getFont();
        Font font = new Font(currentFont.getName(), currentFont.getStyle(), 20);
        loggerArea.setFont(font);
        DefaultCaret caret = (DefaultCaret) loggerArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

        SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                String time = format.format(new Date(record.getMillis()));
                String packageMessage = record.getSourceClassName();
                String method = record.getSourceMethodName();
                String level = record.getLevel().getLocalizedName();
                String logMessage = String.format("%s %s %s\n%s: %s\n", time, packageMessage, method, level, record.getMessage());
                appendMessage(logMessage);
//                loggerArea.append(logMessage);
            }

            @Override
            public void flush() {

            }

            @Override
            public void close() throws SecurityException {

            }
        };
        logger.addHandler(handler);
    }


    @Override
    public void onError(Exception exception) {
        String exceptionName = exception.getClass().getName();
        StringBuilder details = new StringBuilder();
        int cnt = 0;
        for (StackTraceElement element : exception.getStackTrace()) {
            if (cnt + 1 == exception.getStackTrace().length) {
                details.append("\n  == ");
            } else {
                details.append("\n  ↓→ ");
            }
            cnt += 1;
            details.append(element.getClassName());
            details.append("::");
            details.append(element.getMethodName());
            details.append(String.format("[%s:%d]", element.getFileName(), element.getLineNumber()));
        }
        String message = exception.getMessage();
        String messageString = String.format("Exception happened:\nException: %s\nDetails: %s\nMessage:%s\n", exceptionName, details.toString(), message);
        appendMessage(messageString);
//        loggerArea.append(messageString);

        JOptionPane.showMessageDialog(MainForm.this, "Catch an exception, see logger below for more details.");
    }

    boolean first = true;
    @Override
    public void updateUI() {
        renderer.setModel(platform.getCurrentModel());

        branchList.setModel(platform.getCurrentModel());
        try {
            branchList.updateUI();
        } catch (NullPointerException e) {
        }
        renderer.updateUI();

        long totalMem = Runtime.getRuntime().totalMemory();
        String showText = String.format("%.1f MB", totalMem / 1024 / 1024.0);
        jvmMemory.setText(showText);

    }

    @Override
    public void updateConfigPanel(AlgorithmConfig externalConfig) {
        JPanel configPanel = (JPanel) tabbedPane1.getSelectedComponent();
        generator.loadConfig(configPanel, externalConfig);
    }

    private WindowAdapter windowEventListener = new WindowAdapter() {
        @Override
        public void windowClosing(java.awt.event.WindowEvent windowEvent) {
            System.exit(0);
        }
    };
}
