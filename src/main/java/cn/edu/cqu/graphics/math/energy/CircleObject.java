package cn.edu.cqu.graphics.math.energy;

import cn.edu.cqu.graphics.math.OptimizerObject2D;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunctionGradient;
import org.springframework.stereotype.Component;

import javax.vecmath.Point2d;
import java.util.Collection;

import static java.lang.Math.pow;

/**
 * 拟合一个圆
 * 参数：double[]
 * double[0] center.x
 * double[1] center.y
 * double[2] radius
 */
@Component
public class CircleObject implements OptimizerObject2D {


    @Override
    public ObjectiveFunction objFun(Collection<Point2d> data) {
        return new ObjectiveFunction(abc -> {
            double radius = abc[2];
            Point2d center = new Point2d(abc[0], abc[1]);
            double loss = .0;
            for (Point2d point : data) {
                double distance = point.distance(center);
                loss += (pow(distance * distance - radius * radius, 2.0));
            }
            return loss;
        });
    }

    @Override
    public ObjectiveFunctionGradient objFunGrad(Collection<Point2d> data) {
        return new ObjectiveFunctionGradient(abc -> {
            double[] grad = new double[3];
            grad[0] = 0;
            grad[1] = 0;
            grad[2] = 0;
            double radius = abc[2];
            Point2d center = new Point2d(abc[0], abc[1]);
            for (Point2d point : data) {
                double distance = point.distance(center);
                grad[0] += 2.0 * (pow(distance * distance - radius * radius, 2.0) * 2 * (point.x - center.x));
                grad[1] += 2.0 * (pow(distance * distance - radius * radius, 2.0) * 2 * (point.y - center.y));
                grad[2] += 2.0 * (pow(distance * distance - radius * radius, 2.0) * (-2 * radius));
            }
            return grad;
        });
    }
}
