package cn.edu.cqu.graphics.math;

import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunctionGradient;

import javax.vecmath.Point3d;
import java.util.Collection;

public interface OptimizerObject {
    ObjectiveFunction objFun(Collection<Point3d> data);
    ObjectiveFunctionGradient objFunGrad(Collection<Point3d> data);
}
