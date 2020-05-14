package cn.edu.cqu.graphics.pipes.collapse;

import cn.edu.cqu.graphics.Constants;
import cn.edu.cqu.graphics.alg.Octree;
import cn.edu.cqu.graphics.cluster.DensityBasedClusterer;
import cn.edu.cqu.graphics.math.EigenvalueCalculator;
import cn.edu.cqu.graphics.math.Optimizer;
import cn.edu.cqu.graphics.model.PointCloud;
import cn.edu.cqu.graphics.platform.CanvasObject;
import cn.edu.cqu.graphics.protocol.*;
import cn.jimmiez.pcu.common.graphics.BoundingBox;
import cn.jimmiez.pcu.util.VectorUtil;
import org.apache.commons.math3.optim.SimpleValueChecker;
import org.apache.commons.math3.optim.nonlinear.scalar.gradient.NonLinearConjugateGradientOptimizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@Component
public class PointsRedistribution extends CachedPipe {

    @PipeInput
    @FromPreviousOutput(name = "输入点云")
    private PointCloud pointCloud;

    @PipeInput
    @FromPreviousOutput(name = "降采样点")
    private List<Point3d> samplePoints;

    @PipeInput
    @FromPreviousOutput(name = "移动后点")
    private List<Point3d> collapsedPoints = new ArrayList<>();

//    @PipeInput
//    @FromPreviousOutput(type = Constants.TYPE_COLLAPSED_POINTS_2)
    @Temp
    private List<Point3d> bridgePointsCandidates = new ArrayList<>();

    private NonLinearConjugateGradientOptimizer op2 = new NonLinearConjugateGradientOptimizer(NonLinearConjugateGradientOptimizer.Formula.POLAK_RIBIERE,
            new SimpleValueChecker(1 * 1e-6, 1 * 1e-6));

    @Param(comment = "每轮迭代次数", key = "iterationNum", minVal = 2, maxVal = 20)
    private Integer iterationNum = 12;

    /** 是否是静止点 **/
    @PipeInput
    @FromPreviousOutput(name = "是否收敛")
    private List<Boolean> okay;

//    @PipeInput
//    @FromPreviousOutput(name = "局部k")
//    private List<Integer> ks;

    @Autowired
    private EigenvalueCalculator calculator;

    @Autowired
    private DensityBasedClusterer clusterer;

    @PipeOutput(type = Constants.TYPE_BRIDGE_POINTS, name = "Bridge-Points", visualize = false)
    @CanvasAttr(primaryColor = {195, 43, 13}, primarySize = 9)
    @EnableDiskCache
    private List<Point3d> bridgePoints = new ArrayList<>();

    @PipeOutput(type = Constants.TYPE_BRIDGE_POINTS, name = "Branch-Points", visualize = true, visible = false)
    @EnableDiskCache
    private List<Point3d> branchPoints = new ArrayList<>();

    @PipeOutput(type = Constants.TYPE_INT_MAP_TO_INT, name = "bp索引")
    @EnableDiskCache
    private Map<Integer, Integer> i2i = new HashMap<>();

    @Autowired
    private Logger logger;

    @Autowired
    private Optimizer optimizer;

    @Param(comment = "划分粒度", key = "diagonCnt", minVal = 20, maxVal = 50)
    private Integer diagonCnt = 30;

    private double radius;

    /**
     * DB-SCAN 过滤bridge points
     */
    private void filterBridgePoints() {
        BoundingBox box = BoundingBox.of(collapsedPoints);
        List<List<Integer>> indexClusters = clusterer.cluster(bridgePointsCandidates, radius);
        for (List<Integer> list : indexClusters) {
            bridgePoints.add(meanPoint(list));
        }
        logger.info("bridge points: " + bridgePoints.size());
    }

    private Point3d meanPoint(List<Integer> indices) {
        double x = 0;
        double y = 0;
        double z = 0;
        for (Integer index : indices)  {
            x += bridgePointsCandidates.get(index).x;
            y += bridgePointsCandidates.get(index).y;
            z += bridgePointsCandidates.get(index).z;
        }
        return new Point3d(x / indices.size(), y / indices.size(), z / indices.size());
    }

    @Override
    public String getName() {
        return "收缩点重新分布";
    }

    @Override
    public void apply() throws FileNotFoundException {
        BoundingBox box = BoundingBox.of(pointCloud.getPoints());
        radius = box.diagonalLength() / diagonCnt;
        bridgePoints.clear();
        branchPoints.clear();
        i2i.clear();
        bridgePointsCandidates.clear();
        for (int i = 0; i < collapsedPoints.size(); i ++) {
            if (! okay.get(i) && VectorUtil.validPoint(collapsedPoints.get(i))) {
                bridgePoints.add(collapsedPoints.get(i));
//                bridgePointsCandidates.add(collapsedPoints.get(i));
            }
        }
//        filterBridgePoints();

        reContract();
        int cnt = 0;
        for (int i = 0; i < collapsedPoints.size(); i ++) {
            if (
//                    okay.get(i) &&
                            VectorUtil.validPoint(collapsedPoints.get(i))) {
                branchPoints.add(collapsedPoints.get(i));
                i2i.put(cnt, i);
                cnt += 1;
            }
        }
        logger.info("SamplePoints: " + samplePoints.size() + " CollapsedPoints: " + collapsedPoints.size() + " BranchPoints: " + branchPoints.size());
    }

    @SuppressWarnings("Duplicates")
    private void reContract() {

        List<int[]> dynamicNNIndices = new ArrayList<>();
        CanvasObject object = new CanvasObject("移动点", Constants.TYPE_COLLAPSED_POINTS, branchPoints, pipeline.getConfig());
        object.setAttr(attr(new int[] {0, 125, 88}, new int[] {0, 0, 255}));
        List<Boolean> list = new ArrayList<>();
        for (int i = 0; i < bridgePoints.size(); i ++) list.add(false);
        object.setSecondaryData(list);
        pipeline.getModel().addCanvasObject(object);

        Octree octree = new Octree();
        octree.buildIndex(bridgePoints);
        for (int i = 0; i < bridgePoints.size(); i ++) {
            dynamicNNIndices.add(octree.searchNearestNeighbors(5, i));
        }
        for (int iter = 0; iter < iterationNum; iter ++) {
            List<Vector3d> deltaPositions = new ArrayList<>();
            for (int i = 0; i < bridgePoints.size(); i ++) {
                Point3d xi = bridgePoints.get(i);
                Vector3d dynamicXixjSum = new Vector3d();
                // 遍历邻居
                int attractCnt = 0;
                for (int index : dynamicNNIndices.get(i)) {
                    Point3d xj = bridgePoints.get(index);
                    Vector3d xixj = new Vector3d(xj.x - xi.x, xj.y - xi.y, xj.z - xi.z);
                    dynamicXixjSum.add(xixj);
                    attractCnt += 1;
                }

                Vector3d delta = new Vector3d();
                dynamicXixjSum.scale(1.0 / attractCnt);

                delta.add(dynamicXixjSum); // 收缩

                deltaPositions.add(delta);
            }

            for (int i = 0; i < bridgePoints.size(); i ++) {
                Point3d p = bridgePoints.get(i);
                p.add(deltaPositions.get(i));
            }

            deltaPositions.clear();

            pipeline.getModel().updateCanvasObject(bridgePoints);
            iter += 1;
        }
    }

}
