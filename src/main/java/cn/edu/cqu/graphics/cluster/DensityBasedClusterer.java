package cn.edu.cqu.graphics.cluster;

import cn.jimmiez.pcu.common.graphics.BoundingBox;
import net.sf.javaml.clustering.DensityBasedSpatialClustering;
import net.sf.javaml.core.Dataset;
import net.sf.javaml.core.DefaultDataset;
import net.sf.javaml.core.Instance;
import net.sf.javaml.core.SparseInstance;
import org.springframework.stereotype.Component;

import javax.vecmath.Point3d;
import java.util.List;
import java.util.Vector;

/**
 * Created by zjl on 2017/6/23.
 *
 */
@Component
public class DensityBasedClusterer {

    DensityBasedSpatialClustering clustering;


    public DensityBasedClusterer() {
    }

    public List<List<Integer>> cluster(List<Point3d> points) {
        BoundingBox box = BoundingBox.of(points);
        double diagonalLength = box.diagonalLength();
        return cluster(points, diagonalLength / 40);
    }

    public List<List<Integer>> cluster(List<Point3d> points, double epsilon) {
        clustering = new DensityBasedSpatialClustering(epsilon, 1);
        List<List<Integer>> result = new Vector<>();
        Dataset originalDataSet = points2DataSet(points);
        Dataset[] dataSets = clustering.cluster(originalDataSet);
        for (Dataset dataset : dataSets) {
            List<Integer> cluster = new Vector<>();
            for (Instance instance : dataset) {
                cluster.add((Integer)instance.classValue());
            }
            result.add(cluster);
        }
        return result;
    }

    public static Dataset points2DataSet(List<Point3d> points) {
        Dataset dataset = new DefaultDataset();
        for (Integer i = 0; i < points.size(); i ++) {
            Point3d point = points.get(i);
            SparseInstance instance = new SparseInstance(new double[] {point.x, point.y, point.z});
            instance.setClassValue(i);
            dataset.add(instance);
        }
        return dataset;
    }

}
