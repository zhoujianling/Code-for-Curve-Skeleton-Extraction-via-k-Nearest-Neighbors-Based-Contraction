package cn.edu.cqu.graphics.platform;


import cn.edu.cqu.graphics.config.SpringContext;

import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;
import java.util.List;
import java.util.Observable;
import java.util.Vector;
import java.util.logging.Logger;

public class CanvasObjectListModel extends Observable implements TableModel {

    private List<CanvasObject> data = new Vector<>();

    private final Object lock = new Object();

    @Override
    public int getRowCount() {
//        System.out.println("count: " + data.size());
        return data.size();
    }

    @Override
    public int getColumnCount() {
        return 2;
    }

    @Override
    public String getColumnName(int columnIndex) {
        return "";
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        if (columnIndex == 0) {
            return String.class;
        }
        return Boolean.class;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        if (columnIndex == 0) {
            return false;
        }
        return true;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
//        System.out.println("data size: " + data.size() + " row index: " + rowIndex);
        if (data.get(rowIndex).getName() == null) {
            System.out.println("NullPointerException！！！！！！！");
        }
        if (columnIndex == 0) {
            return data.get(rowIndex).getName();
        }
        return data.get(rowIndex).isVisible();
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        if (columnIndex == 1) {
            if (aValue instanceof Boolean) {
                data.get(rowIndex).setVisible((Boolean) aValue);
                setChanged();
                notifyObservers();
            }
        }
    }

    @Override
    public void addTableModelListener(TableModelListener l) {

    }

    @Override
    public void removeTableModelListener(TableModelListener l) {

    }

    public void addCanvasObject(CanvasObject object) {
        data.add(object);
//        System.out.println("add object");
        setChanged();
        notifyObservers();
    }

    public void removeCanvasObject(CanvasObject object) {
        data.remove(object);
        setChanged();
        notifyObservers();
    }

    /**
     * @param canvasData the canvas data that is updated
     */
    public void updateCanvasObject(Object canvasData) {
        synchronized (CanvasObjectListModel.class) {
            if (canvasData instanceof CanvasObject) {
                SpringContext.instance().getBean(Logger.class).warning("传参类型错误，应为CanvasObject里面的数据！");
                throw new IllegalArgumentException("数据类型错误");
            }
            CanvasObject wrappedCanvasObject = canvasObjectOfData(canvasData);
            if (wrappedCanvasObject != null) {
                wrappedCanvasObject.setChanged(true);
            }
            setChanged();
            notifyObservers();
        }
    }

    public void clear() {
        data.clear();
        setChanged();
        notifyObservers();
    }

    public List<CanvasObject> getData() {
        return data;
    }

    /**
     *
     * @param canvasData 给定一个被标注为 @PipeInput 的数据
     * @return 返回这个数据的CanvasObject，如果没有，返回 null
     */
    public CanvasObject canvasObjectOfData(Object canvasData) {
        CanvasObject result = null;
        for (CanvasObject object : data) {
            if (object.getData() == canvasData) {
                result = object;
                break;
            }
        }
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CanvasObjectListModel that = (CanvasObjectListModel) o;

        return data.equals(that.data);
    }

    @Override
    public int hashCode() {
        return data.hashCode();
    }
}
