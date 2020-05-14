package cn.edu.cqu.graphics.math;

import cn.edu.cqu.graphics.config.SpringContext;
import cn.edu.cqu.graphics.model.Vertex;
import org.apache.commons.math3.optim.*;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.gradient.NonLinearConjugateGradientOptimizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.vecmath.Point2d;
import javax.vecmath.Point3d;
import java.util.*;
import java.util.logging.Logger;

import static java.util.stream.Collectors.toList;

/**
 * Paper:2013.L1-Medial skeleton of Point Cloud
 * <p>
 * 最小化欧氏距离和
 * <p>
 * 最小化目标函数使用的是Fletcher-Reeves算法(一种非线性共轭梯度方法)，一般而言收敛速度慢于牛顿法和拟牛顿法（？）
 * 不需要数值线性函数处理，每个步骤都相当快
 * // TODO: 2017/7/10 List -> Set
 */
@Component
public class Optimizer {
    private List<Point3d> points;

    @Autowired
    private Logger logger;

    private NonLinearConjugateGradientOptimizer op = new NonLinearConjugateGradientOptimizer(NonLinearConjugateGradientOptimizer.Formula.FLETCHER_REEVES,
            new SimplePointChecker<>(1e-10, 1e-10));
    private NonLinearConjugateGradientOptimizer op2 = new NonLinearConjugateGradientOptimizer(NonLinearConjugateGradientOptimizer.Formula.POLAK_RIBIERE,
            new SimpleValueChecker(1 * 1e-6, 1 * 1e-6));


    public Optimizer() {
        points = new ArrayList<>();
    }

    public double[] optimize2d(Collection<Point2d> data, double[] initialGuess, Class<? extends OptimizerObject2D> clazz) {
        OptimizerObject2D object = SpringContext.instance().getBean(clazz);
        PointValuePair pvp = op2.optimize(
                GoalType.MINIMIZE,
                new InitialGuess(initialGuess),
                object.objFun(data),
                object.objFunGrad(data),
                new MaxEval(2000)// 1000 -> 1500
        );
        return pvp.getPoint();
    }

    public Point3d computeMedian3d(Set<Long> points, HashMap<Long, Vertex> index2Vertices, Class<? extends OptimizerObject> clazz) {
        return computeMedian3d(points.stream().map((id) -> index2Vertices.get(id).getPosition()).collect(toList()), clazz);
    }

    public double[] computeMedian3d(Collection<Point3d> data, OptimizerObject optimizerObject) {
        points.clear();
        points.addAll(data);

        PointValuePair pvp = op.optimize(
                GoalType.MINIMIZE,
                getInitialGuess(),
                optimizerObject.objFun(data),
                optimizerObject.objFunGrad(data),
                new MaxEval(1000)// 1000 -> 1500
        );
        return new double[]{pvp.getPoint()[0], pvp.getPoint()[1], pvp.getPoint()[2]};
    }

    public Point3d computeMedian3d(Collection<Point3d> data, Class<? extends OptimizerObject> clazz) {
        points.clear();
        points.addAll(data);

        OptimizerObject obj = SpringContext.instance().getBean(clazz);
        PointValuePair pvp = op.optimize(
                GoalType.MINIMIZE,
                getInitialGuess(),
                obj.objFun(data),
                obj.objFunGrad(data),
                new MaxEval(1000)// 1000 -> 1500
        );
        return new Point3d(pvp.getPoint()[0], pvp.getPoint()[1], pvp.getPoint()[2]);
    }


    public Point3d[] computeMedians3d(List<Point3d> data, OptimizerObject obj) {
        Point3d[] result = new Point3d[data.size()];
        double[] initialGuess = new double[data.size() * 3];
        for (int i = 0; i < data.size(); i ++) {
            Point3d p = data.get(i);
            initialGuess[i * 3 + 0] = p.x;
            initialGuess[i * 3 + 1] = p.y;
            initialGuess[i * 3 + 2] = p.z;
        }

        PointValuePair pvp = op2.optimize(
                GoalType.MINIMIZE,
                new InitialGuess(initialGuess),
                obj.objFun(data),
                obj.objFunGrad(data),
                new MaxEval(60300)// 1000 -> 1500
        );
        for (int i = 0; i < initialGuess.length / 3; i ++) {
            result[i] = new Point3d(
                    pvp.getPoint()[i * 3 + 0],
                    pvp.getPoint()[i * 3 + 1],
                    pvp.getPoint()[i * 3 + 2]
            );
        }
        return result;
    }

    public Point3d[] computeMedians3d(List<Point3d> data, Class<? extends OptimizerObject> clazz) {
        points.clear();
        points.addAll(data);
        Point3d[] result = new Point3d[data.size()];
        double[] initialGuess = new double[data.size() * 3];
        for (int i = 0; i < data.size(); i ++) {
            Point3d p = data.get(i);
            initialGuess[i * 3 + 0] = p.x;
            initialGuess[i * 3 + 1] = p.y;
            initialGuess[i * 3 + 2] = p.z;
        }

//        logger.info("N is " + n);
        OptimizerObject obj = SpringContext.instance().getBean(clazz);
        PointValuePair pvp = op2.optimize(
                GoalType.MINIMIZE,
                new InitialGuess(initialGuess),
                obj.objFun(data),
                obj.objFunGrad(data),
                new MaxEval(1350)// 1000 -> 1500
        );
        for (int i = 0; i < initialGuess.length / 3; i ++) {
            result[i] = new Point3d(
                    pvp.getPoint()[i * 3 + 0],
                    pvp.getPoint()[i * 3 + 1],
                    pvp.getPoint()[i * 3 + 2]
            );
        }
        return result;
    }



    private double sphereRadius(List<Point3d> points) {
        double radius;
        double minX = points.get(0).x;
        double maxX = points.get(0).x;
        double minY = points.get(0).y;
        double maxY = points.get(0).y;
        double minZ = points.get(0).z;
        double maxZ = points.get(0).z;
        for (Point3d p : points) {
            if (p.x < minX) minX = p.x;
            if (p.x > maxX) maxX = p.x;
            if (p.y < minY) minY = p.y;
            if (p.y > maxY) maxY = p.y;
            if (p.z < minZ) minZ = p.z;
            if (p.z > maxZ) maxZ = p.z;
        }
        return new Point3d(minX, minY, minZ).distance(new Point3d(maxX, maxY, maxZ)) / 55;
    }

    private InitialGuess getInitialGuess() {
        double x = 0;
        double y = 0;
        double z = 0;
        for (Point3d p : points) {
            if (Double.isNaN(p.x)) continue;
            x += p.x;
            y += p.y;
            z += p.z;
        }
        int n = points.size();
        x /= n;
        y /= n;
        z /= n;
        return new InitialGuess(new double[]{x, y, z});
    }

    private InitialGuess getInitialGuesses(int n) {
        double[] coords = new double[n * 3];
        Random random = new Random(System.currentTimeMillis());
        double x = 0;
        double y = 0;
        double z = 0;
        double xSpan = 0.0;
        double ySpan = 0.0;
        double zSpan = 0.0;
        for (Point3d p : points) {
            if (Double.isNaN(p.x)) continue;
            x += p.x;
            y += p.y;
            z += p.z;
        }
        x /= points.size();
        y /= points.size();
        z /= points.size();
        for (Point3d p : points) {
            if (Double.isNaN(p.x)) continue;
            xSpan += Math.abs(p.x - x);
            ySpan += Math.abs(p.y - y);
            zSpan += Math.abs(p.z - z);
        }
        xSpan /= points.size();
        ySpan /= points.size();
        zSpan /= points.size();

        for (int i = 0; i < n; i ++) {
            coords[i * 3 + 0] = x + 3 * (random.nextDouble() - 1) * xSpan;
            coords[i * 3 + 1] = y + 3 * (random.nextDouble() - 1) * ySpan;
            coords[i * 3 + 2] = z + 3 * (random.nextDouble() - 1) * zSpan;
        }
        return new InitialGuess(coords);
    }

//    public ObjectiveFunction getObjectiveFunction2() {
//        return new ObjectiveFunction(xyz -> {
//            double x = xyz[0];
//            double y = xyz[1];
//            double z = xyz[2];
//            double ret = 0;
//            for (Point3d p : points) {
//                if (Double.isNaN(x)) continue;
//                double n2 = norm2(x, y, z, p);
//                double k = Math.exp((n2 * K));
//                ret += (n2 * k);
//            }
//            return ret;
//        });
//    }

//    public ObjectiveFunction getObjectiveFunction() {
//        return new ObjectiveFunction(xyz -> {
//            double x = xyz[0];
//            double y = xyz[1];
//            double z = xyz[2];
//            double ret = 0;
//            for (Point3d p : points) {
//                if (Double.isNaN(x)) continue;
//                ret += norm2(x, y, z, p);
//            }
//            return ret;
//        });
//    }


    private double norm2(double x, double y, double z, Point3d p) {
        return Math.sqrt(Math.pow(x - p.x, 2) + Math.pow(y - p.y, 2) + Math.pow(z - p.z, 2));
    }

//    /**
//     * 梯度函数
//     * @return
//     */
//    public ObjectiveFunctionGradient getObjectiveFunctionGradient() {
//
//    }

//    public ObjectiveFunctionGradient getObjectiveFunctionGradient2() {
//
//        return new ObjectiveFunctionGradient(xyz -> {
//            double x = xyz[0];
//            double y = xyz[1];
//            double z = xyz[2];
//            double dx = 0;
//            double dy = 0;
//            double dz = 0;
//            for (Point3d p : points) {
//                double n = norm2(x, y, z, p);
//                double theta = Math.exp(n * K);
//                if (n != 0) {
//                    dx += (x - p.x) / n * theta - K * theta * (x - p.x);
//                    dy += (y - p.y) / n * theta - K * theta * (y - p.y);
//                    dz += (z - p.z) / n * theta - K * theta * (z - p.z);
//                }
//            }
//            return new double[]{dx, dy, dz};
//        });
//    }

}
