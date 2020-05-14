package cn.edu.cqu.graphics.model.common;

import javax.vecmath.Matrix4d;
import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;
import java.io.Serializable;

/**
 * 密切平面
 * Ax + By + C = z
 * Ax + By + (-1)z = -C
 * n = (A, B, -1)
 */
public class OsculatingPlane implements Serializable {
    public double A;
    public double B;
    public double C;
    public Point3d auxPoint;
    public Point3d center;

    private Vector3d basisX;
    private Vector3d basisY;
    private Vector3d basisZ;

    public OsculatingPlane() {
    }

    public OsculatingPlane(Vector3d normal, Point3d center) {
        normal.normalize();
        this.A = normal.x;
        this.B = normal.y;
        this.C = normal.z;
        this.center = center;
//        this.d = - (a * point.x + b * point.y + c * point.z);

        this.basisZ = new Vector3d(A, B, C);
        basisZ.normalize();
        double x = center.x * 1.5;
        if (Math.abs(x - center.x) < 1e-6) x += 1e-3;

//        this.basisX = new Vector3d(center.x * 1.5 + center.y * 0.1,)
    }

    public Matrix4d rx() {
        double a = A;
        double b = B;
        double c = -1.0;
        double d = Math.sqrt(b * b + c * c);
        double[] val = new double[] {
                1, 0, 0, 0,
                0, c / d, - b / d, 0,
                0, b / d, c / d, 0,
                0, 0, 0, 1
        };
        Matrix4d matrix4d = new Matrix4d(val);
        return matrix4d;
    }

    public Matrix4d ry() {
        double a = A;
        double b = B;
        double c = -1.0;
        double d = Math.sqrt(b * b + c * c);
        double[] val = new double[] {
                d, 0, -a, 0,
                0, 1, 0, 0,
                a, 0, d, 0,
                0, 0, 0, 0,
        };
        Matrix4d matrix4d = new Matrix4d(val);
        return matrix4d;
    }

    /**
     * 从世界坐标变换到观察坐标的矩阵
     * @return
     */
    public Matrix4d observeMatrix() {
        Matrix4d rx = rx();
        Matrix4d ry = ry();
        rx.mul(ry);
//            System.out.println("u length: " + Math.sqrt(rx.m00 * rx.m00 + rx.m01 * rx.m01 + rx.m02 * rx.m02));
        double up0 = center.x * rx.m00 + center.y * rx.m01  + center.z * rx.m02;
        double vp0 = center.x * rx.m10 + center.y * rx.m11  + center.z * rx.m12;
        double np0 = center.x * rx.m20 + center.y * rx.m21  + center.z * rx.m22;
        double[] vals = new double[] {
                rx.m00, rx.m01, rx.m02, -up0,
                rx.m10, rx.m11, rx.m12, -vp0,
                rx.m20, rx.m21, rx.m22, -np0,
                0, 0, 0, 1
        };
        Matrix4d m = new Matrix4d(vals);
        return m;
    }

    public Point3d transformToWorld(Point3d observePoint) {
        double[] fakeMatrix = new double[] {
                observePoint.x, 0, 0, 0,
                observePoint.y, 0, 0, 0,
                observePoint.z, 0, 0, 0,
                1, 0, 0, 0
        };
        Matrix4d m = observeMatrix();
        m.invert();
        m.mul(new Matrix4d(fakeMatrix));
        return new Point3d(m.m00, m.m10, m.m20);

    }

    public Point3d transformFromWorld(Point3d worldPoint) {
        double[] fakeMatrix = new double[] {
            worldPoint.x, 0, 0, 0,
            worldPoint.y, 0, 0, 0,
                worldPoint.z, 0, 0, 0,
                1, 0, 0, 0
        };
        Matrix4d m = observeMatrix();
        m.mul(new Matrix4d(fakeMatrix));
        return new Point3d(m.m00, m.m10, m.m20);
    }
}
