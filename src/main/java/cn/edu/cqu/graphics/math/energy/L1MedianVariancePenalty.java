package cn.edu.cqu.graphics.math.energy;

import cn.edu.cqu.graphics.math.OptimizerObject;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunctionGradient;
import org.springframework.stereotype.Component;

import javax.vecmath.Point3d;
import java.util.Collection;

@Component
public class L1MedianVariancePenalty implements OptimizerObject {

    private static double K = 30;

    public static void setK(int k ) {
        K = k;
    }

    @Override
    public ObjectiveFunction objFun(Collection<Point3d> data) {
        return new ObjectiveFunction(xyz -> {
            double x = xyz[0];
            double y = xyz[1];
            double z = xyz[2];
            double ret = 0;
            double normSum = 0.0;
            for (Point3d p : data) {
                if (Double.isNaN(x)) continue;
                double norm = norm2(x, y, z, p);
                normSum += norm;
            }
            ret += normSum;
            double mean = normSum / data.size();
            for (Point3d p : data) {
                double norm = norm2(x, y, z, p);
                ret += K * (Math.pow((norm - mean), 2));
            }
            return ret;
        });
    }

    @Override
    public ObjectiveFunctionGradient objFunGrad(Collection<Point3d> data) {
        return new ObjectiveFunctionGradient(xyz -> {
            double x = xyz[0];
            double y = xyz[1];
            double z = xyz[2];
            double dx = 0;
            double dy = 0;
            double dz = 0;
            double mean, normSum = 0.0;
            for (Point3d p : data) {
                double n = norm2(x, y, z, p);
                normSum += n;
            }
            mean = normSum / data.size();
            for (Point3d p : data) {
                double n = norm2(x, y, z, p);
                if (n != 0) {
                    dx += (x - p.x) / n * (1 - 2 * mean * K) + 2 * K * (x - p.x);
                    dy += (y - p.y) / n * (1 - 2 * mean * K) + 2 * K * (y - p.y) ;
                    dz += (z - p.z) / n * (1 - 2 * mean * K) + 2 * K * (z - p.z) ;
                }
            }
            return new double[]{dx, dy, dz};
        });
    }

    private double norm2(double x, double y, double z, Point3d p) {
        return Math.sqrt(Math.pow(x - p.x, 2) + Math.pow(y - p.y, 2) + Math.pow(z - p.z, 2));
    }
}
