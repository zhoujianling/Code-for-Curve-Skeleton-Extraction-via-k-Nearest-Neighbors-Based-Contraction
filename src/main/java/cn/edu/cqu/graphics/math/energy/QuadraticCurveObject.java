package cn.edu.cqu.graphics.math.energy;

import cn.edu.cqu.graphics.math.OptimizerObject2D;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunctionGradient;
import org.springframework.stereotype.Component;

import javax.vecmath.Point2d;
import java.util.Collection;

/**
 * 拟合一个二次曲线
 */
@Component
public class QuadraticCurveObject implements OptimizerObject2D {

    @Override
    public ObjectiveFunction objFun(Collection<Point2d> data) {
        return new ObjectiveFunction(abc -> {
            double loss = 0.0;
            for (Point2d p : data) {
                loss += Math.pow(abc[0] * p.x * p.x + abc[1] * p.x + abc[2] - p.y, 2);
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
            for (Point2d p : data) {
                grad[0] += 2 * p.x * p.x * (abc[0] * p.x * p.x + abc[1] * p.x + abc[2] - p.y);
                grad[1] += 2 * p.x * (abc[0] * p.x * p.x + abc[1] * p.x + abc[2] - p.y);
                grad[2] += 2 * (abc[0] * p.x * p.x + abc[1] * p.x + abc[2] - p.y);
            }
            return grad;
        });
    }
}
