package cn.edu.cqu.graphics.model.tree;

import javax.vecmath.Point3d;
import java.util.ArrayList;
import java.util.List;

public class Triangle {
    private Point3d a;
    private Point3d b;
    private Point3d c;

    public Triangle(Point3d a, Point3d b, Point3d c) {
        this.a = a;
        this.b = b;
        this.c = c;
    }

    public Point3d getA() {
        return a;
    }

    public void setA(Point3d a) {
        this.a = a;
    }

    public Point3d getB() {
        return b;
    }

    public void setB(Point3d b) {
        this.b = b;
    }

    public Point3d getC() {
        return c;
    }

    public void setC(Point3d c) {
        this.c = c;
    }

    public List<Point3d> pointlist() {
        List<Point3d> list = new ArrayList<>();
        list.add(a);
        list.add(b);
        list.add(c);
        return list;

    }

}
