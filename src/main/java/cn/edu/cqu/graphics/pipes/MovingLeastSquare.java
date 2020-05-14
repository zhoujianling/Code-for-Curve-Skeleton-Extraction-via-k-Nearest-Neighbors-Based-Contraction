package cn.edu.cqu.graphics.pipes;

import cn.edu.cqu.graphics.Constants;
import cn.edu.cqu.graphics.alg.Octree;
import cn.edu.cqu.graphics.math.Optimizer;
import cn.edu.cqu.graphics.math.OptimizerObject;
import cn.edu.cqu.graphics.math.OptimizerObject2D;
import cn.edu.cqu.graphics.math.energy.QuadraticCurveObject;
import cn.edu.cqu.graphics.model.common.OsculatingPlane;
import cn.edu.cqu.graphics.protocol.*;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.SimpleValueChecker;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunctionGradient;
import org.apache.commons.math3.optim.nonlinear.scalar.gradient.NonLinearConjugateGradientOptimizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.vecmath.Matrix3d;
import javax.vecmath.Matrix4d;
import javax.vecmath.Point2d;
import javax.vecmath.Point3d;
import java.io.FileNotFoundException;
import java.util.*;

@Component
public class MovingLeastSquare extends CachedPipe {

//    @PipeInput
//    @FromPreviousOutput(type = Constants.TYPE_POINT_CLOUD)
//    private PointCloud data;

    private Octree octree = new Octree();

    @PipeInput
    @FromPreviousOutput(name = "数据点")
    private List<Point3d> points = null;

    @Param(comment = "MLS k近邻", key = "MLSnearestK", minVal = 3, maxVal = 324)
    private Integer k = 13;//K近邻的k取值

    @Param(comment = "k近邻数", key = "nearestK", minVal = 3, maxVal = 124)
    private Integer k0 = 124;//K近邻的k取值

    @Temp
    private List<OsculatingPlane> planes = new ArrayList<>();

    @PipeOutput(type = Constants.TYPE_MOVED_POINTS, name="MLS输出点", visualize = true)
    private List<Point3d> movedPoints = new ArrayList<>();

    // 使用3NN图上的最短路径寻找MLS邻域
    private List<int[]> nearestIndicesOn3NN = new ArrayList<>();
    private List<int[]> threeNNIndices = new ArrayList<>();

    @Autowired
    Optimizer optimizer;

    private NonLinearConjugateGradientOptimizer op3 = new NonLinearConjugateGradientOptimizer(NonLinearConjugateGradientOptimizer.Formula.POLAK_RIBIERE,
            new SimpleValueChecker(1 * 1e-9, 1 * 1e-9));
    private NonLinearConjugateGradientOptimizer op2 = new NonLinearConjugateGradientOptimizer(NonLinearConjugateGradientOptimizer.Formula.POLAK_RIBIERE,
            new SimpleValueChecker(1 * 1e-6, 1 * 1e-6));

//    @PipeOutput(type = Constants.TYPE_NEIGHBOR_INDICES, name = "近邻图", visualize = true, visible = false)
//    private List<Point3d> curvePoints = new ArrayList<>();


    @Override
    public String getName() {
        return "移动最小二乘法";
    }


    /**
     * 对点云的所有点，求一个邻域，对邻域做密切面
     */
    private void calculateOsculatingPlanes() {
        planes.clear();

        OsculatingPlaneObject osculatingPlaneObject = new OsculatingPlaneObject();

//        System.out.println("center: " + plane.center.x + " " + plane.center.y + " " + plane.center.z);
//        Point3d p = plane.transformFromWorld(new Point3d(0, 0, 0));
//        System.out.println("p: " + p.x + " " + p.y + " " + p.z);

        for (int i = 0; i < points.size(); i ++) {
//            Point3d p = points.get(i);
            int[] neighbors = nearestIndicesOn3NN.get(i);
            osculatingPlaneObject.neighbor = neighbors;
            OsculatingPlane plane = new OsculatingPlane();
            List<Point3d> list = new ArrayList<>();

            for (int index : neighbors) {
                list.add(points.get(index));
            }

            double[] abc = optimize(list, osculatingPlaneObject);
            plane.A = abc[0];
            plane.B = abc[1];
            plane.C = abc[2];
            plane.auxPoint = points.get(neighbors[neighbors.length - 1]);
            plane.center = points.get(i);
            planes.add(plane);
        }
    }

    /**
     * 给定平面上的数据点，最小二乘法拟合一维直线
     * 作为后续的二次函数的坐标系x轴
     * @param list
     * @return
     */
    private double[] calculatingOsculatingLine(List<Point2d> list) {
        if (list.size() < 1) return new double[] {0, 0};
        double[] initialGuess = new double[2];
        initialGuess[0] = 0.5;
        initialGuess[1] = list.get(0).y - list.get(0).x * initialGuess[0];
        OsculatingLineObject object = new OsculatingLineObject();
        PointValuePair pvp = op3.optimize(
                GoalType.MINIMIZE,
                new InitialGuess(initialGuess),
                object.objFun(list),
                object.objFunGrad(list),
                new MaxEval(2000)// 1000 -> 1500
        );
        return new double[]{pvp.getPoint()[0], pvp.getPoint()[1]};
    }

    /**
     * 把邻域内的三维点投影到密切面
     * @param plane
     * @return
     */
    private List<Point2d> projectToOsculatingPlane(OsculatingPlane plane, int[] pointIndices) {
        List<Point2d> result = new ArrayList<>();
        Matrix4d observeMatrix = plane.observeMatrix();
        for (int i = 0; i < pointIndices.length; i += 4) {
            Matrix4d o = new Matrix4d(observeMatrix);
            if (i + 3 >= pointIndices.length) break;
            int i0 = pointIndices[i + 0];
            int i1 = pointIndices[i + 1];
            int i2 = pointIndices[i + 2];
            int i3 = pointIndices[i + 3];
            Matrix4d temp = new Matrix4d(new double[] {
                points.get(i0).x, points.get(i1).x, points.get(i2).x, points.get(i3).x,
                    points.get(i0).y, points.get(i1).y, points.get(i2).y, points.get(i3).y,
                    points.get(i0).z, points.get(i1).z, points.get(i2).z, points.get(i3).z,
                    1, 1, 1, 1
            });
            o.mul(temp);
            result.add(new Point2d(o.m00, o.m10));
            result.add(new Point2d(o.m01, o.m11));
            result.add(new Point2d(o.m02, o.m12));
            result.add(new Point2d(o.m03, o.m13));
        }
        for (int i = pointIndices.length / 4 * 4; i < pointIndices.length; i ++) {
            Matrix4d o = new Matrix4d(observeMatrix);
            Point3d p = points.get(pointIndices[i]);
            Matrix4d temp = new Matrix4d(new double[] {
                    p.x, 0, 0, 0,
                    p.y, 0, 0, 0,
                    p.z, 0, 0, 0,
                    1, 0, 0, 0
            });
            o.mul(temp);
            result.add(new Point2d(o.m00, o.m10));
        }
        return result;
    }

    private double[] quadraticCurveRegression(List<Point2d> list) {
        if (list.size() < 1) return new double[] {0, 0, 0};
        double[] initialGuess = new double[3];
        initialGuess[0] = 0.5;
        initialGuess[1] = 0.5;
        initialGuess[2] = list.get(0).y - list.get(0).x * list.get(0).x * initialGuess[0] - list.get(0).x * initialGuess[1];
        QuadraticCurveObject object = new QuadraticCurveObject();
        PointValuePair pvp = op3.optimize(
                GoalType.MINIMIZE,
                new InitialGuess(initialGuess),
                object.objFun(list),
                object.objFunGrad(list),
                new MaxEval(2000)// 1000 -> 1500
        );
        return new double[]{pvp.getPoint()[0], pvp.getPoint()[1], pvp.getPoint()[2]};
    }

    /**
     * 给定一堆密切面，计算密切面的MLS坐标(0, c)，并还原到三维空间
     */
    private void osculatingPlaneMLS() {
        // 随机取一个平面放到 excel 上看效果
//        Random r = new Random();
//        int i = r.nextInt(planes.size());
//        List<Point2d> ps = projectToOsculatingPlane(planes.get(i), neighborList.get(i));
//        for (Point2d p2d : ps) {
//            System.out.println(String.format("%f\t%f", p2d.x, p2d.y));
//        }
//        System.out.println("///////////////////////////////////////");
//        double[] ab = calculatingOsculatingLine(ps);
//        Matrix3d m = regressionPlaneMatrix(ab[0], ab[1]);
//        List<Point2d> regressionPlanePoints = new ArrayList<>();
//        for (Point2d p2d : ps) {
//            Matrix3d result = new Matrix3d(m);
//            Matrix3d mm = new Matrix3d(new double[] {
//                    p2d.x, 0, 0,
//                    p2d.y, 0, 0,
//                    1, 0, 0
//            });
//            result.mul(mm);
//            regressionPlanePoints.add(new Point2d(result.m00, result.m10));
//            System.out.println(String.format("%f\t%f", result.m00, result.m10));
//        }
//        System.out.println("///////////////////////////////////////");
//        double[] abc = quadraticCurveRegression(regressionPlanePoints);
//        Matrix3d mat = new Matrix3d(m);
//        mat.invert();
//        Matrix3d fake = new Matrix3d(new double[] {
//                0, 0, 0,
//                abc[2], 0, 0,
//                1, 0, 0
//        });
//        mat.mul(fake);
//        Point2d movedPointOnRegressionPlane = new Point2d(mat.m00, mat.m10);
////        System.out.println(String.format("%f\t%f", mat.m00, mat.m10));
//        System.out.println(String.format("%f\t%f\t%f", abc[0], abc[1], abc[2]));
//        System.out.flush();

        movedPoints.clear();

        for (int i = 0; i < planes.size(); i ++) {
            List<Point2d> list = projectToOsculatingPlane(planes.get(i), nearestIndicesOn3NN.get(i));
            double[] ab = calculatingOsculatingLine(list);
            Matrix3d matrix3d = regressionPlaneMatrix(ab[0], ab[1]);
            List<Point2d> regressionPlanePoints = new ArrayList<>();
            // 下面针对旋转后的坐标系计算新坐标
            for (int j = 0; j < list.size(); j += 3) {
                Matrix3d o = new Matrix3d(matrix3d);
                if (j + 2 >= list.size()) break;
                Point2d p0 = list.get(j + 0);
                Point2d p1 = list.get(j + 1);
                Point2d p2 = list.get(j + 2);
                Matrix3d temp = new Matrix3d(new double[] {
                        p0.x, p1.x, p2.x,
                        p0.y, p1.y, p2.y,
                        1, 1, 1
                });
                o.mul(temp);
                regressionPlanePoints.add(new Point2d(o.m00, o.m10));
                regressionPlanePoints.add(new Point2d(o.m01, o.m11));
                regressionPlanePoints.add(new Point2d(o.m02, o.m12));
            }
            // 对 regressionPlanePoints 做二次曲线回归
            double[] abc = quadraticCurveRegression(regressionPlanePoints);
            Matrix3d mat = new Matrix3d(matrix3d);
            mat.invert();
            Matrix3d fake = new Matrix3d(new double[] {
                0, 0, 0,
                abc[2], 0, 0,
                1, 0, 0
            });
            mat.mul(fake);
            Point2d movedPointOnRegressionPlane = new Point2d(mat.m00, mat.m10);
            Matrix4d mat3 = planes.get(i).observeMatrix();
            mat3.invert();
            Matrix4d fake3 = new Matrix4d(new double[] {
                movedPointOnRegressionPlane.x, 0, 0, 0,
                movedPointOnRegressionPlane.y, 0, 0, 0,
                0, 0, 0, 0,
                1, 0, 0, 0
            });
            mat3.mul(fake3);
            Point3d movedPointInOriginalSpace = new Point3d(mat3.m00, mat3.m10, mat3.m20);
            movedPoints.add(movedPointInOriginalSpace);
        }
    }


    /**
     * 给定直线的斜率 a，截距 b
     * 以这条直线为新的 x 轴，原坐标系(0, 0) 为 x 轴的原点，做新的坐标系用于二次曲线回归
     * 新轴的方向向量 (1, a)，相当于逆时针旋转 <(1, 0), (1, a)>
     * @param a
     * @param b
     * @return 返回一个变换矩阵，给定三维向量 (x, y, 1) 变换到回归平面坐标 (x', y', ?)
     */
    private Matrix3d regressionPlaneMatrix(double a, double b) {
        double c = Math.sqrt(1 + a * a);
        double[] val = new double[] {
                1.0 / c, a / c, 0,
                -a / c, 1.0 / c, 0,
                0, 0, 1
        };

        Matrix3d matrix3d = new Matrix3d(val);
        return matrix3d;
    }

    private double[] optimize(List<Point3d> data, OptimizerObject optimizerObject) {
        double[] initialGuess = new double[3];
        initialGuess[0] = 0.5;
        initialGuess[1] = 0.2;
        initialGuess[2] = data.get(0).z - initialGuess[0] * data.get(0).x - initialGuess[1] * data.get(0).y;
        PointValuePair pvp = op2.optimize(
                GoalType.MINIMIZE,
                new InitialGuess(initialGuess),
                optimizerObject.objFun(data),
                optimizerObject.objFunGrad(data),
                new MaxEval(2000)// 1000 -> 1500
        );
        return new double[]{pvp.getPoint()[0], pvp.getPoint()[1], pvp.getPoint()[2]};
    }

    /**
     * 通过DFS k次拿一个假数据
     */
    private void buildFakeShortestPathGraph() {
        nearestIndicesOn3NN.clear();
        threeNNIndices.clear();
        for (int i = 0; i < points.size(); i ++) {
            threeNNIndices.add(octree.searchNearestNeighbors(k0 + 1, i));
        }
        for (int i = 0; i < points.size(); i ++) {
            Set<Integer> visited = new HashSet<>();
            buildFakeShortestPathImpl(i, visited, 0, k);
            int[] neighbor = new int[visited.size()];
            int cnt = 0;
            for (Integer index : visited) {
                neighbor[cnt ++] = index;
            }
            nearestIndicesOn3NN.add(neighbor);
        }
    }

    private void buildFakeShortestPathImpl(int rootIndex, Set<Integer> visited, int iter, int k) {
        if (iter >= k) return;
        for (int index : threeNNIndices.get(rootIndex)) {
            if (! visited.contains(index)) {
                visited.add(index);
                buildFakeShortestPathImpl(index, visited, iter + 1, k);
            }
        }
    }

    @Override
    public void apply() throws FileNotFoundException {
        octree.buildIndex(points);
        buildFakeShortestPathGraph();
        calculateOsculatingPlanes();
        osculatingPlaneMLS();
    }

    /**
     * 对平面上的数据点，用最小二乘法拟合直线 y = ax + b
     * a, b = argmin Σ(ax + b - y)^2
     */
    private class OsculatingLineObject implements OptimizerObject2D {

        @Override
        public ObjectiveFunction objFun(Collection<Point2d> data) {
            return new ObjectiveFunction(ab -> {
                double loss = 0;
                for (Point2d p : data) {
                    loss += Math.pow(ab[0] * p.x + ab[1] - p.y, 2);
                }
                return loss;
            });
        }

        @Override
        public ObjectiveFunctionGradient objFunGrad(Collection<Point2d> data) {
            return new ObjectiveFunctionGradient(ab -> {
                double[] grad = new double[2];
                grad[0] = 0;
                grad[1] = 0;
                for (Point2d p : data) {
                    grad[0] += 2.0 * p.x * (ab[0] * p.x + ab[1] - p.y);
                    grad[1] += 2.0 * (ab[0] * p.x + ab[1] - p.y);
                }
                return grad;
            });

        }
    }

    private class OsculatingPlaneObject implements OptimizerObject{

        int[] neighbor = null;

        @Override
        public ObjectiveFunction objFun(Collection<Point3d> data1) {
            return new ObjectiveFunction(abc -> {
                double loss = 0;
                double A = abc[0];
                double B = abc[1];
                double C = abc[2];
                for (int index : neighbor) {
                    Point3d p = points.get(index);
                    loss += Math.pow(A * p.x + B * p.y + C - p.z, 2);
                }
                return loss;
            });
        }

        @Override
        public ObjectiveFunctionGradient objFunGrad(Collection<Point3d> data) {
            return new ObjectiveFunctionGradient(abc -> {
                double[] grad = new double[3];
                double A = abc[0];
                double B = abc[1];
                double C = abc[2];
                for (int index : neighbor) {
                    Point3d p = points.get(index);
                    grad[0] += 2 * p.x * (A * p.x + B * p.y + C - p.z);
                    grad[1] += 2 * p.y * (A * p.x + B * p.y + C - p.z);
                    grad[2] += 2 * 1.0 * (A * p.x + B * p.y + C - p.z);
                }

                return grad;
            });
        }
    }
}
