package cn.edu.cqu.graphics.model.common;

import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;

/**
 * 三维空间平面 ax + by + cz + d = 0
 */
public class Plane3d {

    private double a;

    private double b;

    private double c;

    private double d;

    public Plane3d(double a, double b, double c, double d) {
        this.a = a;
        this.b = b;
        this.c = c;
        this.d = d;
    }

    public Plane3d(Vector3d normal, Point3d point) {
        normal.normalize();
        this.a = normal.x;
        this.b = normal.y;
        this.c = normal.z;
        this.d = - (a * point.x + b * point.y + c * point.z);

    }

    /**
     * 计算一个点到平面的距离
     * @param point 三维空间的点
     * @return 到平面的距离
     */
    public double distance(Point3d point) {
        double divident = Math.sqrt(a * a + b * b + c * c);
        if (divident == 0) return 0;
        return Math.abs(a * point.x + b * point.y + c * point.z + d) / divident;
    }

//    /**
//     * 将一个三维空间的点投影到这个平面
//     * @param center 选取平面上的一个点为坐标原点
//     * @return 投影后的二维坐标
//     */
//    @SuppressWarnings("Duplicates")
//    public Matrix4d projectMatrix(Point3d center) {
//        double c = -1.0;
//        double d = Math.sqrt(b * b + c * c);
//        double[] valRx = new double[] {
//                1, 0, 0, 0,
//                0, c / d, - b / d, 0,
//                0, b / d, c / d, 0,
//                0, 0, 0, 1
//        };
//        Matrix4d rx = new Matrix4d(valRx);
//
//        double[] valRy = new double[] {
//                d, 0, -a, 0,
//                0, 1, 0, 0,
//                a, 0, d, 0,
//                0, 0, 0, 0,
//        };
//        Matrix4d ry = new Matrix4d(valRy);
//        rx.mul(ry);
//        double up0 = center.x * rx.m00 + center.y * rx.m01  + center.z * rx.m02;
//        double vp0 = center.x * rx.m10 + center.y * rx.m11  + center.z * rx.m22;
//        double np0 = center.x * rx.m20 + center.y * rx.m21  + center.z * rx.m22;
//        double[] vals = new double[] {
//                rx.m00, rx.m01, rx.m02, -up0,
//                rx.m10, rx.m11, rx.m12, -vp0,
//                rx.m20, rx.m21, rx.m22, -np0,
//                0, 0, 0, 1
//        };
//        return new Matrix4d(vals);
//    }

    public double getA() {
        return a;
    }

    public void setA(double a) {
        this.a = a;
    }

    public double getB() {
        return b;
    }

    public void setB(double b) {
        this.b = b;
    }

    public double getC() {
        return c;
    }

    public void setC(double c) {
        this.c = c;
    }

    public double getD() {
        return d;
    }

    public void setD(double d) {
        this.d = d;
    }

}
