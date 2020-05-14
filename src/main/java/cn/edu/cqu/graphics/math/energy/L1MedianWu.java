package cn.edu.cqu.graphics.math.energy;

import cn.edu.cqu.graphics.math.OptimizerObject;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunctionGradient;
import org.springframework.stereotype.Component;

import javax.vecmath.Point3d;
import java.util.Collection;

import static java.lang.Math.E;
import static java.lang.Math.pow;

/**
 * 实现 2013.Huang L1-Medial 里面的 目标函数
 */
@Component
public class L1MedianWu implements OptimizerObject {

    /**size of neighborhood**/
    /**简化为 Σ||xi-qi||e^(-sr^2), s = 1/h^2**/
    private static float S = 100f;  // s 越大， 整个平滑项就越没用

    @Override
    public ObjectiveFunction objFun(Collection<Point3d> data) {
        return new ObjectiveFunction(xyz -> {
            double ret = 0;
            int n = xyz.length / 3;
            for (int i = 0; i < n; i++) {
                double x = xyz[i * 3 + 0];
                double y = xyz[i * 3 + 1];
                double z = xyz[i * 3 + 2];
                double normSum = 0.0;
                for (Point3d p : data) {
                    if (Double.isNaN(x)) continue;
                    double norm = norm2(x, y, z, p);
                    double smoothTerm = pow(E, -(S * norm * norm));
                    norm *= smoothTerm;
                    normSum += norm;
                }
                ret += normSum;
            }
            return ret;
        });
    }

    private double norm2(double x, double y, double z, Point3d p) {
        return Math.sqrt(Math.pow(x - p.x, 2) + Math.pow(y - p.y, 2) + Math.pow(z - p.z, 2));
    }

    @Override
    public ObjectiveFunctionGradient objFunGrad(Collection<Point3d> data) {
        return new ObjectiveFunctionGradient(xyz -> {
            double[] grad = new double[xyz.length];
            for (int i = 0; i < xyz.length / 3; i ++) {
                double x = xyz[i * 3 + 0];
                double y = xyz[i * 3 + 1];
                double z = xyz[i * 3 + 2];
                double dx = 0;
                double dy = 0;
                double dz = 0;
                for (Point3d p : data) {
                    double norm = norm2(x, y, z, p);
                    double smoothTerm = pow(E, -(S * norm * norm));
                    if (norm != 0) {
                        dx += (x - p.x) / norm * smoothTerm + norm * smoothTerm * (- 2 * S * (x - p.x));
                        dy += (y - p.y) / norm * smoothTerm + norm * smoothTerm * (- 2 * S * (y - p.y));
                        dz += (z - p.z) / norm * smoothTerm + norm * smoothTerm * (- 2 * S * (z - p.z)) ;
                    }
                }
                grad[i * 3 + 0] = dx;
                grad[i * 3 + 1] = dy;
                grad[i * 3 + 2] = dz;

            }
            return grad;
        });
    }
}
