package cn.edu.cqu.graphics.model;

import cn.edu.cqu.graphics.protocol.InputData;
import org.springframework.stereotype.Component;

import javax.vecmath.Point3d;
import java.io.Serializable;
import java.util.ArrayList;

@Component
public class PointCloud implements Serializable, InputData {
    private ArrayList<Point3d> points = new ArrayList<>();
    private String md5;//原始数据的 md5

    public ArrayList<Point3d> getPoints() {
        return points;
    }

    public void setPoints(ArrayList<Point3d> points) {
        this.points = points;
    }

    public void setMd5(String m) {
        this.md5 = m;
    }

    public String getMd5() {
        return md5;
    }

}
