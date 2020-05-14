package cn.edu.cqu.graphics.math;

import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunctionGradient;

import javax.vecmath.Point2d;
import java.util.Collection;

/**
 * 使用MLS算法时，需要对二维平面上的数据求解最优化问题
 */
public interface OptimizerObject2D {
    ObjectiveFunction objFun(Collection<Point2d> data);
    ObjectiveFunctionGradient objFunGrad(Collection<Point2d> data);
}
