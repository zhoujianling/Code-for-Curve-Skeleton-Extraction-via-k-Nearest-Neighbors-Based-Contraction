package cn.edu.cqu.graphics.math;

import org.apache.commons.math3.linear.EigenDecomposition;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.stat.correlation.Covariance;
import org.springframework.stereotype.Component;

import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;
import java.util.List;

@Component
public class EigenvalueCalculator {

    public EigenvalueCalculator() {}

    private Point3d meanPoint(List<Point3d> ps) {
        Point3d mean = new Point3d(0, 0, 0);
        for (Point3d p : ps) {
            mean.add(p);
        }
        mean.scale(1.0 / ps.size());
        return mean;
    }

    @SuppressWarnings("Duplicates")
    public Vector3d computeVec(List<Point3d> points) {
        Point3d xi = meanPoint(points);
        //create points in a double array
        double[][] covMatrixData = new double[][]{
                new double[]{0, 0, 0},
                new double[]{0, 0, 0},
                new double[]{0, 0, 0}
        };

        for (Point3d xj : points) {
            Vector3d xixj = new Vector3d(xj.x - xi.x, xj.y - xi.y, xj.z - xi.z);
            covMatrixData[0][0] += xixj.x * xixj.x;
            covMatrixData[0][1] += xixj.x * xixj.y;
            covMatrixData[0][2] += xixj.x * xixj.z;
            covMatrixData[1][0] += xixj.y * xixj.x;
            covMatrixData[1][1] += xixj.y * xixj.y;
            covMatrixData[1][2] += xixj.y * xixj.z;
            covMatrixData[2][0] += xixj.z * xixj.x;
            covMatrixData[2][1] += xixj.z * xixj.y;
            covMatrixData[2][2] += xixj.z * xixj.z;
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                covMatrixData[row][col] /= points.size();
            }
        }

        //create real matrix
        RealMatrix covMatrix = MatrixUtils.createRealMatrix(covMatrixData);

        //create covariance matrix of points, then find eigen vectors
        Covariance covariance = new Covariance(covMatrix);
        RealMatrix covarianceMatrix = covariance.getCovarianceMatrix();
        EigenDecomposition ed = new EigenDecomposition(covarianceMatrix);
        return new Vector3d(ed.getEigenvector(0).toArray());
    }

    @SuppressWarnings("Duplicates")
    public double[] compute(List<Point3d> points) {
        Point3d xi = meanPoint(points);
        //create points in a double array
        double[][] covMatrixData = new double[][]{
                new double[]{0, 0, 0},
                new double[]{0, 0, 0},
                new double[]{0, 0, 0}
        };

        for (Point3d xj : points) {
            Vector3d xixj = new Vector3d(xj.x - xi.x, xj.y - xi.y, xj.z - xi.z);
            covMatrixData[0][0] += xixj.x * xixj.x;
            covMatrixData[0][1] += xixj.x * xixj.y;
            covMatrixData[0][2] += xixj.x * xixj.z;
            covMatrixData[1][0] += xixj.y * xixj.x;
            covMatrixData[1][1] += xixj.y * xixj.y;
            covMatrixData[1][2] += xixj.y * xixj.z;
            covMatrixData[2][0] += xixj.z * xixj.x;
            covMatrixData[2][1] += xixj.z * xixj.y;
            covMatrixData[2][2] += xixj.z * xixj.z;
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                covMatrixData[row][col] /= points.size();
            }
        }

        //create real matrix
        RealMatrix covMatrix = MatrixUtils.createRealMatrix(covMatrixData);

        //create covariance matrix of points, then find eigen vectors
        Covariance covariance = new Covariance(covMatrix);
        RealMatrix covarianceMatrix = covariance.getCovarianceMatrix();
        EigenDecomposition ed = new EigenDecomposition(covarianceMatrix);
        double[] eigens = ed.getRealEigenvalues();
//            System.out.println("" + eigens[0] * 1000 + " " + eigens[1] * 1000 + " " + eigens[2] * 1000);
        return eigens;
    }


}
