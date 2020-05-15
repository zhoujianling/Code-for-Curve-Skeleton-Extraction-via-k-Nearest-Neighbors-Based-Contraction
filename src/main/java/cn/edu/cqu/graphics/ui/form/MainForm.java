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
import java.awt.event.WindowEvent;
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
    /**
     * 数据类型和数据格式做区分
     **/
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
                if (!stopSuccess) {
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
                        List<Double> matrixList = gson.fromJson(reader, new TypeToken<List<Double>>() {
                        }.getType());
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
        setMinimumSize(new Dimension((int) (dimen.width * 0.85), (int) (dimen.height * 0.85)));
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
        public void windowClosing(WindowEvent windowEvent) {
            System.exit(0);
        }
    };

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        container = new JPanel();
        container.setLayout(new GridBagLayout());
        rightPanel = new JPanel();
        rightPanel.setLayout(new GridBagLayout());
        rightPanel.setPreferredSize(new Dimension(320, 428));
        GridBagConstraints gbc;
        gbc = new GridBagConstraints();
        gbc.gridx = 3;
        gbc.gridy = 0;
        gbc.gridheight = 3;
        gbc.fill = GridBagConstraints.BOTH;
        container.add(rightPanel, gbc);
        branchList = new JTable();
        branchList.setShowHorizontalLines(false);
        branchList.setShowVerticalLines(false);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 0.7;
        gbc.fill = GridBagConstraints.BOTH;
        rightPanel.add(branchList, gbc);
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridBagLayout());
        panel1.setPreferredSize(new Dimension(296, 155));
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        rightPanel.add(panel1, gbc);
        loadDataButton = new JButton();
        loadDataButton.setText("Import Model");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel1.add(loadDataButton, gbc);
        deleteButton = new JButton();
        deleteButton.setText("Delete Objects");
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel1.add(deleteButton, gbc);
        importMatrix = new JButton();
        importMatrix.setText("Import Camera");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel1.add(importMatrix, gbc);
        saveSnapshotButton = new JButton();
        saveSnapshotButton.setText("Save Snapshot");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel1.add(saveSnapshotButton, gbc);
        exportButton = new JButton();
        exportButton.setText("Export Camera");
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel1.add(exportButton, gbc);
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridBagLayout());
        panel2.setPreferredSize(new Dimension(296, 95));
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 0.3;
        gbc.fill = GridBagConstraints.BOTH;
        rightPanel.add(panel2, gbc);
        table1 = new JTable();
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel2.add(table1, gbc);
        final JLabel label1 = new JLabel();
        label1.setMaximumSize(new Dimension(-1, -1));
        label1.setPreferredSize(new Dimension(200, 24));
        label1.setText("Variables Watch");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel2.add(label1, gbc);
        statusContainer = new JPanel();
        statusContainer.setLayout(new GridBagLayout());
        statusContainer.setPreferredSize(new Dimension(296, 115));
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        rightPanel.add(statusContainer, gbc);
        final JLabel label2 = new JLabel();
        label2.setText("Status");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        statusContainer.add(label2, gbc);
        final JLabel label3 = new JLabel();
        label3.setText("Scale Ratio：");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        statusContainer.add(label3, gbc);
        scaleRatio = new JLabel();
        scaleRatio.setText("100.0 %");
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        statusContainer.add(scaleRatio, gbc);
        final JLabel label4 = new JLabel();
        label4.setText("X-Axis Rotation：");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        statusContainer.add(label4, gbc);
        final JLabel label5 = new JLabel();
        label5.setText("Y-Axis Rotation：");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        statusContainer.add(label5, gbc);
        final JLabel label6 = new JLabel();
        label6.setText("JVM Memory：");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        statusContainer.add(label6, gbc);
        jvmMemory = new JLabel();
        jvmMemory.setText("NaN");
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        statusContainer.add(jvmMemory, gbc);
        middlePanel = new JPanel();
        middlePanel.setLayout(new GridBagLayout());
        middlePanel.setMinimumSize(new Dimension(200, 65));
        middlePanel.setPreferredSize(new Dimension(-1, -1));
        gbc = new GridBagConstraints();
        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.gridheight = 2;
        gbc.weightx = 0.3;
        gbc.weighty = 0.7;
        gbc.fill = GridBagConstraints.BOTH;
        container.add(middlePanel, gbc);
        tabbedPane1 = new JTabbedPane();
        tabbedPane1.setTabLayoutPolicy(0);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 0.6;
        gbc.fill = GridBagConstraints.BOTH;
        middlePanel.add(tabbedPane1, gbc);
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));
        panel3.setEnabled(true);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.weighty = 0.4;
        gbc.fill = GridBagConstraints.BOTH;
        middlePanel.add(panel3, gbc);
        skeletonizeButton = new JButton();
        skeletonizeButton.setText("RUN!");
        panel3.add(skeletonizeButton);
        suspendButton = new JButton();
        suspendButton.setText("Suspend");
        panel3.add(suspendButton);
        stopButton = new JButton();
        stopButton.setText("Stop");
        panel3.add(stopButton);
        importConfigButton = new JButton();
        importConfigButton.setText("Read Config");
        panel3.add(importConfigButton);
        exportConfigButton = new JButton();
        exportConfigButton.setText("Save Config");
        panel3.add(exportConfigButton);
        canvas = new JPanel();
        canvas.setLayout(new BorderLayout(0, 0));
        canvas.setPreferredSize(new Dimension(-1, -1));
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.gridheight = 2;
        gbc.weightx = 0.7;
        gbc.weighty = 0.7;
        gbc.fill = GridBagConstraints.BOTH;
        container.add(canvas, gbc);
        final JPanel panel4 = new JPanel();
        panel4.setLayout(new com.intellij.uiDesigner.core.GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.fill = GridBagConstraints.BOTH;
        container.add(panel4, gbc);
        clearLogButton = new JButton();
        clearLogButton.setText("ClearLog");
        clearLogButton.setToolTipText("Clear Log");
        panel4.add(clearLogButton, new com.intellij.uiDesigner.core.GridConstraints(0, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final com.intellij.uiDesigner.core.Spacer spacer1 = new com.intellij.uiDesigner.core.Spacer();
        panel4.add(spacer1, new com.intellij.uiDesigner.core.GridConstraints(1, 0, 1, 1, com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER, com.intellij.uiDesigner.core.GridConstraints.FILL_VERTICAL, 1, com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final JPanel panel5 = new JPanel();
        panel5.setLayout(new BorderLayout(0, 0));
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 0.3;
        gbc.fill = GridBagConstraints.BOTH;
        container.add(panel5, gbc);
        final JScrollPane scrollPane1 = new JScrollPane();
        scrollPane1.setMinimumSize(new Dimension(19, 20));
        scrollPane1.setPreferredSize(new Dimension(14, -1));
        scrollPane1.setVerifyInputWhenFocusTarget(true);
        scrollPane1.setVerticalScrollBarPolicy(22);
        panel5.add(scrollPane1, BorderLayout.CENTER);
        loggerArea = new JTextArea();
        loggerArea.setAutoscrolls(true);
        loggerArea.setEditable(false);
        Font loggerAreaFont = UIManager.getFont("ColorChooser.font");
        if (loggerAreaFont != null) loggerArea.setFont(loggerAreaFont);
        loggerArea.setLineWrap(true);
        loggerArea.setMinimumSize(new Dimension(-1, -1));
        loggerArea.setPreferredSize(new Dimension(-1, 6000));
        loggerArea.setRows(250);
        loggerArea.setWrapStyleWord(true);
        scrollPane1.setViewportView(loggerArea);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return container;
    }
}
