package cn.edu.cqu.graphics.ui.form;

import cn.edu.cqu.graphics.Constants;
import cn.edu.cqu.graphics.platform.DataImporter;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class SelectDataDialog extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JComboBox typeCombox;
    private JComboBox formatCombox;
    private JButton selectFileButton;
    private JTextField filePathText;
    private DataImporter listener = null;
    private Map<DataTypeItem, ArrayList<DataFormatItem>> map = new HashMap<>();
    private File selectedFile = null;

    public SelectDataDialog(JFrame father, DataImporter listener) {
        super(father);
        this.listener = listener;

        initData();
        initWindow();
        initComponent();

        buttonOK.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onOK();
            }
        });

        buttonCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        });

        // call onCancel() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

        // call onCancel() on ESCAPE
        contentPane.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }

    private void initData() {
        ArrayList<DataFormatItem> pointCloudFormats = new ArrayList<>();
        ArrayList<DataFormatItem> skeletonFormats = new ArrayList<>();
        pointCloudFormats.add(new DataFormatItem(Constants.FORMAT_POINT_CLOUD_LIUJI, "刘氏点云"));
        pointCloudFormats.add(new DataFormatItem(Constants.FORMAT_POINT_CLOUD_PLY, "ply格式"));
        pointCloudFormats.add(new DataFormatItem(Constants.FORMAT_POINT_CLOUD_ROSA_OFF, "off格式"));
        skeletonFormats.add(new DataFormatItem(Constants.FORMAT_SKELETON_ZJL, "简单骨架"));
        skeletonFormats.add(new DataFormatItem(Constants.FORMAT_SKELETON_L1_MEDIAN, "L1-median骨架"));
        map.put(new DataTypeItem(Constants.TYPE_POINT_CLOUD, "点云"), pointCloudFormats);
        map.put(new DataTypeItem(Constants.TYPE_COMMON_SKELETON_CURVE, "骨架"), skeletonFormats);
    }

    private void initComponent() {
        DefaultComboBoxModel<DataTypeItem> model = new DefaultComboBoxModel(map.keySet().toArray());
        typeCombox.setModel(model);
        formatCombox.setModel(new DefaultComboBoxModel(map.get(typeCombox.getSelectedItem()).toArray()));
        typeCombox.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                DataTypeItem item = (DataTypeItem) typeCombox.getSelectedItem();
                formatCombox.setModel(new DefaultComboBoxModel(map.get(item).toArray()));
            }
        });

        selectFileButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser chooser = new JFileChooser();
                chooser.setDialogType(JFileChooser.OPEN_DIALOG);
                chooser.addChoosableFileFilter(new FileFilter() {
                    @Override
                    public boolean accept(File f) {
                        return true;
                    }

                    @Override
                    public String getDescription() {
                        return "*";
                    }
                });
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    File file = chooser.getSelectedFile();
//                    selectedFile = file;
                    filePathText.setText(file.getAbsolutePath());
                }

            }
        });
    }

    private void initWindow() {
        setMinimumSize(new Dimension(500, 300));
        setPreferredSize(new Dimension(500, 300));
        setTitle("导入数据");

        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(buttonOK);

    }

    private void onOK() {
        selectedFile = new File(filePathText.getText());
        if (!selectedFile.exists()) {
            System.err.println("所选文件不存在...");
            return;
        }
        // add your code here
        if (selectedFile != null && listener != null) {
            listener.loadData(selectedFile,
                    ((DataTypeItem) typeCombox.getSelectedItem()).dataType,
                    ((DataFormatItem) formatCombox.getSelectedItem()).dataFormat);
        }
        dispose();
    }

    private void onCancel() {
        // add your code here if necessary
        dispose();
    }

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
        contentPane = new JPanel();
        contentPane.setLayout(new GridLayoutManager(2, 1, new Insets(10, 10, 10, 10), -1, -1));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, 1, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        panel1.add(spacer1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1, true, false));
        panel1.add(panel2, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        buttonOK = new JButton();
        buttonOK.setText("OK");
        panel2.add(buttonOK, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        buttonCancel = new JButton();
        buttonCancel.setText("Cancel");
        panel2.add(buttonCancel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(5, 2, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel3, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setText("数据格式");
        panel3.add(label1, new GridConstraints(2, 0, 2, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        typeCombox = new JComboBox();
        panel3.add(typeCombox, new GridConstraints(0, 1, 2, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("数据类型");
        panel3.add(label2, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        formatCombox = new JComboBox();
        panel3.add(formatCombox, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        selectFileButton = new JButton();
        selectFileButton.setText("选择文件");
        panel3.add(selectFileButton, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        filePathText = new JTextField();
        panel3.add(filePathText, new GridConstraints(4, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return contentPane;
    }

    public static class DataTypeItem {
        int dataType;
        String typeName;

        public DataTypeItem(int dataType, String typeName) {
            this.dataType = dataType;
            this.typeName = typeName;
        }

        @Override
        public String toString() {
            return typeName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            DataTypeItem that = (DataTypeItem) o;

            if (dataType != that.dataType) return false;
            return typeName != null ? typeName.equals(that.typeName) : that.typeName == null;
        }

        @Override
        public int hashCode() {
            int result = dataType;
            result = 31 * result + (typeName != null ? typeName.hashCode() : 0);
            return result;
        }
    }

    public static class DataFormatItem {
        int dataFormat;
        String formatName;

        public DataFormatItem(int dataFormat, String formatName) {
            this.dataFormat = dataFormat;
            this.formatName = formatName;
        }

        @Override
        public String toString() {
            return formatName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            DataFormatItem that = (DataFormatItem) o;

            if (dataFormat != that.dataFormat) return false;
            return formatName != null ? formatName.equals(that.formatName) : that.formatName == null;
        }

        @Override
        public int hashCode() {
            int result = dataFormat;
            result = 31 * result + (formatName != null ? formatName.hashCode() : 0);
            return result;
        }
    }
}
