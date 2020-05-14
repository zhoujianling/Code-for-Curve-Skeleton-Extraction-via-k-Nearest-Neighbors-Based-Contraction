package cn.edu.cqu.graphics.pipes.collapse;

import cn.edu.cqu.graphics.Constants;
import cn.edu.cqu.graphics.alg.GridVoxelizer;
import cn.edu.cqu.graphics.alg.GridVoxelizerPcaHelper;
import cn.edu.cqu.graphics.math.EigenvalueCalculator;
import cn.edu.cqu.graphics.math.Optimizer;
import cn.edu.cqu.graphics.platform.CanvasObject;
import cn.edu.cqu.graphics.platform.ExperimentPlatform;
import cn.edu.cqu.graphics.protocol.*;
import cn.jimmiez.pcu.common.graph.*;
import cn.jimmiez.pcu.common.graphics.BoundingBox;
import cn.jimmiez.pcu.common.graphics.Octree;
import cn.jimmiez.pcu.common.graphics.shape.Sphere;
import cn.jimmiez.pcu.util.Pair;
import cn.jimmiez.pcu.util.VectorUtil;
import org.apache.commons.math3.linear.EigenDecomposition;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.optim.SimpleValueChecker;
import org.apache.commons.math3.optim.nonlinear.scalar.gradient.NonLinearConjugateGradientOptimizer;
import org.apache.commons.math3.stat.correlation.Covariance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.logging.Logger;

import static cn.edu.cqu.graphics.Constants.*;
import static java.lang.Math.*;

/**
 */
@SuppressWarnings("Duplicates")
@Component
public class IterativeContract extends CachedPipe {


    @Autowired
    Logger logger;

    @PipeInput
    @FromPreviousOutput(name = "Down-Sampled Points")
//    public HashMap<Long, Vertex> index2Vertex = new HashMap<>();//格子index到格子的映射
    private List<Point3d> dataPoints;

    @PipeOutput(type = TYPE_COLLAPSED_POINTS_2, name = "Skeletal Points", visualize = true)
    @CanvasAttr(primaryColor = {0, 125, 88})
    @EnableDiskCache
    private List<Point3d> points = new ArrayList<>();

    @PipeOutput(type = TYPE_IS_STILL, name = "是否收敛")
    @EnableDiskCache
    private List<Boolean> ifFrozen = new ArrayList<>();

    @PipeOutput(type = TYPE_EIGEN_VALUES, name = "SIGMA")
    @EnableDiskCache
    private List<Double> sigmas = new ArrayList<>();

    @PipeOutput(type = TYPE_EIGEN_VECTORS, name = "PCA主方向")
    @EnableDiskCache
    private List<Vector3d> pcaDirections = new ArrayList<>();

    @Temp
    private List<Integer> neighborNumbers = new ArrayList<>();

//    @Param(comment = "划分粒度", key = "diagonCnt", minVal = 20, maxVal = 50)
//    private Integer diagonCnt = 20;

    @Param(comment = "Growth Rate(a%)", key = "radiusIncreaseRate", minVal = 0, maxVal = 300)
    private Integer rate = 100;

    @Param(comment = "Riemannian Neighbors", key = "enableRiemann")
    private Boolean enableRiemann = false;

    @Param(comment = "Iterations (c)", key = "iterationNum", minVal = 2, maxVal = 200)
    private Integer iterationNum = 12;

    @Param(comment = "Threshold(η‰)", key = "stopSigma", minVal = 900, maxVal = 999)
    private Integer stopThreshold = 995;

    @Param(comment = "Initial R_0", key = "initRadius", minVal = 1, maxVal = 100)
    private Integer initRadius = 10;

    @Autowired
    Optimizer optimizer;

    @Autowired
    ExperimentPlatform platform;

    private double averageDistance;


    /** 当前最小的 sigma **/
    private double minSigma = 0.99999;

    private double averageSigma = 0.5;

    private List<Double> densities = new ArrayList<>();

    private double minDensity = 0.999999;

    private double initialAverageDistance;

    private double initialRadius = -1;

    private int initialK = 20;

    private int maxK = -1;

    private Sphere radiusSphere = new Sphere(new Point3d(), 0.01);

    private BoundingBox boundingBox = null;

    private static double MERGE_THRESHOLD = 1E-3;

    private NonLinearConjugateGradientOptimizer op2 = new NonLinearConjugateGradientOptimizer(NonLinearConjugateGradientOptimizer.Formula.POLAK_RIBIERE,
            new SimpleValueChecker(1 * 1e-10, 1 * 1e-10));

    // 用于搜索黎曼邻居
    @Temp
    private Graph fiveNN = null;

    @Autowired
    private EigenvalueCalculator calculator;

    private double[] paramArray = new double[10];

    @Param(comment = "Auto-Snap", key = "autoSnap")
    private Boolean enableSnapshot = false;

    @Override
    public String getName() {
        return "KNN-Contraction";
    }

    @Override
    public void apply() throws FileNotFoundException {
//        dataPoints = index2Vertex.values().stream().map(Vertex::getPosition).collect(Collectors.toList());
        points.clear();
        neighborNumbers.clear();
        ifFrozen.clear();
        sigmas.clear();
        densities.clear();
//        radiusTimes = 5;
        mainIterationBody();
    }

    private int statFrozenCnt() {
        int cnt = 0;
        for (int i = 0; i < ifFrozen.size(); i ++) {
            if (ifFrozen.get(i)) cnt += 1;
        }
        return cnt;
    }

    /**
     * 返回 moving 点中的平均 radius, 最大 radius，最小 radius，initialAverageDistance 作为参考。
     * @return
     */
    private void statParamInfo(List<List<Integer>> dynamicIndices) {
        // max K
        paramArray[1] = Double.NEGATIVE_INFINITY;
        // min K
        paramArray[2] = Double.POSITIVE_INFINITY;
        // active max K
        paramArray[3] = Double.NEGATIVE_INFINITY;
        // active min K
        paramArray[4] = Double.POSITIVE_INFINITY;
        // actual active aver K
        paramArray[7] = -1;
        // actual active min K
        paramArray[8] = Double.POSITIVE_INFINITY;
        // min Sigma
        paramArray[5] = Double.POSITIVE_INFINITY;
        // active min Sigma
        paramArray[6] = - 1.0f;

        double kSum = 0;
        int totalCnt = 0, activeCnt = 0;
        double actualActiveKSum = 0;
        double activeSigmaSum = 0;
        double totalSigmaSum = 0;
        for (int i = 0; i < neighborNumbers.size(); i ++) {
            kSum += neighborNumbers.get(i);
            totalCnt += 1;
            paramArray[1] = Math.max(paramArray[1], neighborNumbers.get(i));
            paramArray[2] = Math.min(paramArray[2], neighborNumbers.get(i));
            totalSigmaSum += sigmas.get(i);
            if (! ifFrozen.get(i)) {
                activeCnt += 1;
                paramArray[3] = Math.max(paramArray[3], neighborNumbers.get(i));
                paramArray[4] = Math.min(paramArray[4], neighborNumbers.get(i));
                activeSigmaSum += sigmas.get(i);
                actualActiveKSum += dynamicIndices.get(i).size();
                paramArray[8] = min(paramArray[8], dynamicIndices.get(i).size());
            }
            paramArray[5] = min(paramArray[5], sigmas.get(i));
        }
        paramArray[6] = activeSigmaSum / activeCnt;
        paramArray[0] = kSum / totalCnt;
        paramArray[7] = actualActiveKSum / activeCnt;
        averageSigma = totalSigmaSum / dataPoints.size();
//        radiusSphere.setRadius(paramArray[0]);
    }

    private List<Point3d> voxelizePoints(List<Point3d> shifting, List<Integer> indices, GridVoxelizerPcaHelper helper) {
        List<Point3d> ps = new ArrayList<>();
        List<GridVoxelizerPcaHelper.GridCell> list = helper.fetchCells(indices);
        for (GridVoxelizerPcaHelper.GridCell cell : list) {
            double x = 0, y = 0, z = 0;
            for (int index : cell.getIndices()) {
                x += shifting.get(index).x;
                y += shifting.get(index).y;
                z += shifting.get(index).z;
            }
            Point3d p = new Point3d(x / cell.getIndices().size(), y / cell.getIndices().size(), z / cell.getIndices().size());
            ps.add(p);
        }
        return ps;
    }

    /**
     * 应该在每次计算 K 邻居后重新计算密度？
     * @param shifting
     */
    private void computeDensities(List<Point3d> shifting, List<List<Integer>> dynamicIndices) {
        minDensity = 0.9999999;
        for (int i = 0; i < shifting.size(); i ++) {
            Point3d point = shifting.get(i);
            if (! VectorUtil.validPoint(point)) continue;
            if (dynamicIndices.get(i).size() < 1) continue;
            double densitySum = 1.0;
            for (int index : dynamicIndices.get(i)) {
                double radius = neighborNumbers.get(i);
                double i16Radius = 16.0 / radius / radius;
                Point3d neighbor = shifting.get(index);
                double distance = neighbor.distance(point);
                distance = max(1e-5, distance);
                densitySum += pow(E, - i16Radius * distance * distance);
            }
            densitySum = 1. / densitySum;
            minDensity = min(minDensity, densitySum);
            densities.set(i, densitySum);
        }

    }

    /**
     * 判断是不是已经收缩到线了
     * @param shifting
     */
    @SuppressWarnings("Duplicates")
    private void computeStatus(List<Point3d> shifting, List<List<Integer>> dynamicIndices) {
        for (int i = 0; i < shifting.size(); i ++) {
            if (!VectorUtil.validPoint(shifting.get(i))) {
                ifFrozen.set(i, true);
            } else {
                double weightedSigma = sigmas.get(i);
                if (weightedSigma > 0.99) {
                    ifFrozen.set(i, true);
                } else {
                    ifFrozen.set(i, false);
                }
            }

        }
    }

    private void computeSigma(List<Point3d> shifting, List<List<Integer>> dynamicIndices) {
        Octree octree = new Octree();
        octree.buildIndex(shifting);
//        GridVoxelizerPcaHelper helper = new GridVoxelizerPcaHelper();
//        helper.voxelize(shifting, initialRadius / 30.0);

        List<List<Integer>> tempNN = new ArrayList<>();
        for (int i = 0; i < shifting.size(); i ++) {
            if (! VectorUtil.validPoint(shifting.get(i))) {
                tempNN.add(new ArrayList<>());
                continue;
            }

//            int neighborCount = Math.max(15, nonOutlierNum / 100);
//            neighborCount = Math.min(neighborCount, 30);
//            int[] neighborIndices = octree.searchNearestNeighbors(neighborCount, i);
//            List<Integer> neighborIndices = octree.searchAllNeighborsWithinDistance(i, initialAverageDistance * 3);
            List<Integer> neighborIndices = octree.searchAllNeighborsWithinDistance(i, initialAverageDistance * 3);
            tempNN.add(neighborIndices);
//            List<Point3d> ps = voxelizePoints(shifting, neighborIndices, helper);
            Point3d xi = shifting.get(i);
            //create points in a double array
            double[][] covMatrixData = new double[][]{
                    new double[]{0, 0, 0},
                    new double[]{0, 0, 0},
                    new double[]{0, 0, 0}
            };

            for (int index : neighborIndices) {
//            for (Point3d xj : ps) {
                Point3d xj = shifting.get(index);
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

            //create real matrix
            RealMatrix covMatrix = MatrixUtils.createRealMatrix(covMatrixData);

            try {
                //create covariance matrix of points, then find eigen vectors
                Covariance covariance = new Covariance(covMatrix);
                RealMatrix covarianceMatrix = covariance.getCovarianceMatrix();
                EigenDecomposition ed = new EigenDecomposition(covarianceMatrix);
                RealVector vec = ed.getEigenvector(0);
                double[] eigens = ed.getRealEigenvalues();
                double sigma = eigens[0] / (eigens[0] + eigens[1] + eigens[2]);
                if (Double.isNaN(sigma)) sigma = 0.5;
                sigmas.set(i, sigma);
                Vector3d pcaDirection = new Vector3d(vec.toArray());
                pcaDirection.normalize();
                if (VectorUtil.validPoint(pcaDirection)) {
                    pcaDirections.set(i, pcaDirection);
                }
            } catch (Exception e) {
                e.printStackTrace();
//                okay.set(i, false);
            }
        }
        // 计算高斯sigma
        minSigma = 0.99999999;
        for (int i = 0; i < shifting.size(); i ++) {
            if (!VectorUtil.validPoint(shifting.get(i))) { continue; }
            Point3d point = shifting.get(i);
            double sigmaSum = sigmas.get(i);
            double weightSum = 1.0;
            double i4Radius = 16.0 / initialRadius / initialRadius;

            for (int index : tempNN.get(i)) {
                double sigmaI = sigmas.get(index);
                Point3d neighborPoint = shifting.get(index);
                if (Double.isNaN(neighborPoint.x)) continue;
                double distance = neighborPoint.distance(point);
                distance = max(1E-6, distance);
                double weight = pow(E, - i4Radius * distance * distance);
                sigmaSum += sigmaI * weight;
                weightSum += weight;
            }
            double weightedSigma = sigmaSum / weightSum;
            if (Double.isNaN(weightedSigma)) {
                logger.warning("NaN sigma!");
                continue;
            }
            minSigma = min(minSigma, weightedSigma);
            sigmas.set(i, weightedSigma);
        }

    }

    /**
     *
     */
    private void updateKs(List<Boolean> ifFrozen, List<Point3d> shifting, Octree octree, int iter, List<List<Integer>> dynamicNNIndices) {
//        List<Point3d> activePoints = new ArrayList<>();
        int activeCount = 0;
        int frozenCount = 0;
        for (int i = 0; i < shifting.size(); i ++) {
            if (!ifFrozen.get(i)) {
//            if (sigmas.get(i) < 0.95) {
                activeCount += 1;
            } else {
                frozenCount += 1;
            }
        }


        for (int i = 0; i < shifting.size(); i ++) {
            int k = neighborNumbers.get(i);
            if (! VectorUtil.validPoint(shifting.get(i))) continue;
            double increaseRate = rate / 100.0;// * (1. - sigmas.get(i) / (1.0 - minSigma));
            int kIter = (iter) / iterationNum;
            if (! ifFrozen.get(i)) {
                double deltaK = initialK * (increaseRate * increaseRate * (2 * kIter - 1) + 2 * increaseRate);
                double weight = pow(E, - (pow(sigmas.get(i) - minSigma, 2.0) / pow(0.95 - minSigma, 2.0)));
                deltaK *= weight;
                k += deltaK;
            }
            k = (int) min(activeCount * 0.25, k);
            k = max(20, k);
            neighborNumbers.set(i, k);
        }

        for (int i = 0; i < shifting.size(); i ++) {
            int k = neighborNumbers.get(i);
            if (! VectorUtil.validPoint(shifting.get(i))) continue;
            if (! ifFrozen.get(i)) {
                double kSum = k;
                double weightSum = 1.0;
                for (int neighborIndex : octree.searchAllNeighborsWithinDistance(i, initialAverageDistance * 3)) {
                    double weight = 1.0;
//                    if (sigmas.get(neighborIndex) > 0.9) weight = 3;
//                    if (ifFrozen.get(neighborIndex)) weight = 10;
                    kSum += ((neighborNumbers.get(neighborIndex)) * weight);
                    weightSum += weight;
                }
                weightSum = max(1.0, weightSum);
                k = (int) (kSum / (weightSum));

            }
            neighborNumbers.set(i, k);
        }

    }

    private double weightRadius(double computeRadius, int index, List<Point3d> shifting, Octree octree) {
        double MIN_VAL = 0.0001;
        computeRadius = Math.max(computeRadius, MIN_VAL);
        double i16DivRadius = 4.0 / (initialRadius * initialRadius);
        Point3d point = shifting.get(index);
        double penaltyRadiusSum = computeRadius;
        double weightSum = 1.;
        for (int i : octree.searchAllNeighborsWithinDistance(index, initialRadius)) {
            Point3d frozenPoint = shifting.get(i);
            double distance = frozenPoint.distance(point);
            double currWeight = Math.pow(Math.E, - i16DivRadius * distance * distance);
            double lowerRadius = neighborNumbers.get(i);
            if (ifFrozen.get(i)) lowerRadius = initialAverageDistance * 3;
            penaltyRadiusSum += (lowerRadius * currWeight);
            weightSum += currWeight;
        }
        double result = penaltyRadiusSum / weightSum;
        return result;
    }


    /**
     * 构建五邻居图
     * @param octree 一个已经对采样点构建索引的八叉树
     */
    private void buildFiveNNGraph(Octree octree) {
        List<int[]> knnIndices = new ArrayList<>();
        double sum = 0;
        fiveNN = new UndirectedGraph();
        for (int i = 0; i < dataPoints.size(); i ++) fiveNN.addVertex(i);
        List<Pair<VertexPair, Double>> edges = new ArrayList<>();
        for (int i = 0; i < dataPoints.size(); i ++) {
            int[] nn = octree.searchNearestNeighbors(5, i);
            knnIndices.add(nn);
            for (int index : nn) {
                double distance = dataPoints.get(i).distance(dataPoints.get(index));
                edges.add(new Pair<>(new VertexPair(i, index), distance));
                sum += distance;
            }
        }
        edges.sort(Comparator.comparing(Pair::getValue));
        // 执行移除边操作会导致outlier 没法去除
        for (int i = 0; i < edges.size() * 0.03; i ++) edges.remove(edges.size() - 1);
        for (Pair<VertexPair, Double> edge : edges) {
            VertexPair vp = edge.getKey();
            fiveNN.addEdge(vp.getVi(), vp.getVj(), edge.getValue());
        }
        averageDistance = sum / (dataPoints.size() * 5);
//        fiveNN = Graphs.knnGraph(dataPoints, knnIndices);
    }


    private List<Integer> searchGeodesicNeighbors(int i, BaseGraph knnGraph, Octree octree) {
        int[] candidates = octree.searchNearestNeighbors(neighborNumbers.get(i), i);
        List<Integer> result = new ArrayList<>();
        Set<Integer> indices = new HashSet<>();
        indices.add(i);
        for (int index : candidates) indices.add(index);
        if (enableRiemann) {
            BaseGraph subGraph = Graphs.subGraph(knnGraph, indices);
            List<List<Integer>> conns = Graphs.connectedComponents(subGraph);
            for (List<Integer> conn : conns) {
                for (Integer index : conn) {
                    if (index == i) return conn;
                }
            }
            logger.warning("Fail to find Riemann neighbors");
        }
        result.addAll(indices);
        return result;
    }

    private Point3d baryCenter(List<Point3d> points) {
        Point3d sum = new Point3d();
        if (points.size() < 1) return sum;
        for (Point3d point : points) {
            sum.add(point);
        }
        sum.scale(1.0 / points.size());
        return sum;
    }

    private void resampleGrid(List<Point3d> shifting) {
        // 先找出 需要下采样 的点
        List<Point3d> needDownSample = new ArrayList<>();
        List<Integer> sampleIndices = new ArrayList<>();
        // 记录每个点的原始index
        for (int i = 0; i < shifting.size(); i ++) {
            Point3d p = shifting.get(i);
            if (sigmas.get(i) > 0.9 && sigmas.get(i) < 0.99) {
                needDownSample.add(p);
                sampleIndices.add(i);
            }
        }

        GridVoxelizer voxelizer = new GridVoxelizer(1.2);
        voxelizer.setCellSize(initialAverageDistance * 0.5);

        List<GridVoxelizer.GridCell> cells = voxelizer.voxelize(needDownSample);
        for (GridVoxelizer.GridCell cell : cells) {
            List<Point3d> points = new ArrayList<>();
            for (int index : cell.getIndices()) {
                points.add(needDownSample.get(index));
            }
            Point3d cellMedian = baryCenter(points);
//            Point3d cellMedian = optimizer.computeMedian3d(points, L1Median.class);
            for (int i = 0; i < cell.getIndices().size(); i ++) {
                int index = cell.getIndices().get(i);
                int sampleIndex = sampleIndices.get(index);
                Point3d point = shifting.get(sampleIndex);
                if (i == 0) point.set(cellMedian);
                else point.set(Double.NaN, Double.NaN, Double.NaN);
            }
        }
    }

    private void resampleMerge(List<Point3d> shifting, List<List<Integer>> nnIndices) {
        int cnt = 0;
        // 应该是 averageDistance
        MERGE_THRESHOLD = Math.max(averageDistance * 0.3, 1E-5);
        for (int i = 0; i < shifting.size(); i ++) {
            if (ifFrozen.get(i)) continue;
            Point3d point = shifting.get(i);
            List<Integer> toBeMerged = new ArrayList<>();
            for (int neighborIndex : nnIndices.get(i)) {
                if (ifFrozen.get(neighborIndex)) continue;
                if (i == neighborIndex) continue;
                Point3d neighbor = shifting.get(neighborIndex);
                if (Math.abs(point.x - neighbor.x) < MERGE_THRESHOLD &&
                        Math.abs(point.y - neighbor.y) < MERGE_THRESHOLD &&
                        Math.abs(point.z - neighbor.z) < MERGE_THRESHOLD) {
                    toBeMerged.add(neighborIndex);
                }
            }
            if (toBeMerged.size() > 0) cnt += toBeMerged.size();
            for (int index : toBeMerged) {
                shifting.get(index).set(new Point3d(Double.NaN, Double.NaN, Double.NaN));
                ifFrozen.set(index, true);
            }
        }
        System.out.println("\nMerge: " + cnt);
    }

    private void init(List<Point3d> shifting, List<List<Integer>> dynamicNNIndices) {
        Octree samplesOctree = new Octree();
        samplesOctree.buildIndex(dataPoints);

        // 求初始 averageDistance
        buildFiveNNGraph(samplesOctree);

        initialAverageDistance = averageDistance;
        boundingBox = BoundingBox.of(dataPoints);
        initialRadius = boundingBox.diagonalLength() / Math.pow(dataPoints.size(), 0.33333);
        initialRadius *= (initRadius * 1.0 / 10.0);
        initialK = (int) (initialRadius / initialAverageDistance);
        initialK *= initialK;
        initialK = max(initialK, 20);
        maxK = dataPoints.size() / 8;
//        logger.info("初始半径倍率：" + this.initialRadius + " maxK: " + maxK);
        averageSigma = 0.5;

        for (int i = 0; i < dataPoints.size(); i ++) {
            Point3d p = dataPoints.get(i);
            shifting.add(new Point3d(p));
            ifFrozen.add(false);
            sigmas.add(0.5D);
            densities.add(0.5D);
            pcaDirections.add(new Vector3d(Double.NaN, Double.NaN, Double.NaN));
            neighborNumbers.add(initialK);
            radiusSphere.setRadius(initialRadius);
//            dynamicNNIndices.add(samplesOctree.searchAllNeighborsWithinDistance(i, neighborNumbers.get(i)));
            dynamicNNIndices.add(searchGeodesicNeighbors(i, fiveNN, samplesOctree));
        }
        computeSigma(shifting, dynamicNNIndices);
//        computeDensities(shifting, dynamicNNIndices);

    }

    private void mainIterationBody() {
        List<Point3d> shifting = new ArrayList<>();
        List<List<Integer>> dynamicNNIndices = new ArrayList<>();

        // 初始化半径等
        init(shifting, dynamicNNIndices);

        CanvasObject object = new CanvasObject("Moving Points", Constants.TYPE_COLLAPSED_POINTS, shifting, pipeline.getConfig());
        CanvasObject sphereObject = new CanvasObject("Initial Ball", Constants.TYPE_SPHERE, radiusSphere, pipeline.getConfig());
        sphereObject.setVisible(false);
        object.setAttr(attr(new int[] {0, 125, 88}, new int[] {0, 0, 255}));
        object.setSecondaryData(ifFrozen);
        pipeline.getModel().addCanvasObject(object);
        pipeline.getModel().addCanvasObject(sphereObject);


        int frozenCount = 0;
        int iter = 0;
        double threshold = stopThreshold * 1.0 / 1000;
        while (averageSigma < threshold) {
            if (iter > 90) {
//                logger.info("迭代超过90次，自动跳出Loop，防止无法停机。");
                break;
            }

            statParamInfo(dynamicNNIndices);
            logger.info(String.format("frozen/total:%d/%d, averSigma: %.4f, averK:%.2f minK: %.2f, activeMinK: %.2f, iter:%d", frozenCount, dataPoints.size(), averageSigma, paramArray[0], paramArray[2], paramArray[4], iter));
            logger.info(String.format("actualAverK: %.2f, actualMinK: %.2f, active Aver Sigma: %.2f, min Sigma: %.2f, iter:%d", paramArray[7], paramArray[8], paramArray[6], paramArray[5], iter));

            List<Vector3d> deltaPositions = new ArrayList<>();
            for (int i = 0; i < shifting.size(); i ++) {
                Point3d xi = shifting.get(i);
                if (!VectorUtil.validPoint(xi)) {
                    deltaPositions.add(new Vector3d());
                    continue;
                }
                Vector3d dynamicXixjSum = new Vector3d();
                // 遍历邻居
                double moveVectorCnt = 0;
                for (int index : dynamicNNIndices.get(i)) {
                    Point3d xj = shifting.get(index);
                    Vector3d xixj = new Vector3d(xj.x - xi.x, xj.y - xi.y, xj.z - xi.z);
//                    if (averageSigma > 0.98 && (ifFrozen.get(index))) xixj.scale(0.02);
                    moveVectorCnt += 1.;
                    dynamicXixjSum.add(xixj);
                }
                moveVectorCnt = max(1, moveVectorCnt);
                Vector3d delta = new Vector3d();
                dynamicXixjSum.scale(1.0 / moveVectorCnt);

                delta.add(dynamicXixjSum); // 收缩
                if (iter > 0) {
                    Vector3d pcaDirection = pcaDirections.get(i);
                    double angle = delta.angle(pcaDirection);
                    if (Double.isNaN(angle)) {
                        angle = 0;
                    }
                    if (angle > PI / 2) {
                        angle = PI - angle;
                        pcaDirection.scale(-1);
                    }
                    double deltaLength = delta.length();
                    Vector3d deltaPcaExtent = new Vector3d(pcaDirection);
                    deltaPcaExtent.scale(deltaLength * cos(angle));
                    Vector3d deltaOrthExtent = new Vector3d(delta.x - deltaPcaExtent.x, delta.y - deltaPcaExtent.y, delta.z - deltaPcaExtent.z);
                    deltaPcaExtent.scale((1.0 - sigmas.get(i)));

                    delta.set(new double[]{0, 0, 0});

                    delta.add(deltaPcaExtent);
                    delta.add(deltaOrthExtent);
                }

                deltaPositions.add(delta);
            }

            for (int i = 0; i < shifting.size(); i ++) {
                Vector3d deltaVector = deltaPositions.get(i);
                if (! VectorUtil.validPoint(deltaVector)) {
                    logger.warning("Invalid vectors");
                    continue;
                }
                shifting.get(i).add(deltaVector);
            }

            deltaPositions.clear();

            int iters = iterationNum;
            boolean needResample = (iter % iters == 0 && iter > 0);

            computeSigma(shifting, dynamicNNIndices);
            computeStatus(shifting, dynamicNNIndices);
            if (iter % (iterationNum / 2) == 0 && enableSnapshot) {
                platform.notifyFrontEndSaveSnapshot("" + iter);
            }

            if (needResample) { // % 4 == 0
//                if (iter % 6 == 0)
                System.gc();
//                merge(shifting, dynamicNNIndices);
                // 如果开启 GridResample, 必须避免之前判断 frozen，并把 frozen 操作转移到下面
//                resampleGrid(shifting);
//                computeSigma(shifting, dynamicNNIndices);
//                computeStatus(shifting, dynamicNNIndices);
//                resampleKnn(shifting);

                Octree octree = new Octree();
                octree.buildIndex(shifting);
                // 扩大半径
                updateKs(ifFrozen, shifting, octree, iter, dynamicNNIndices);
                for (int i = 0; i <shifting.size(); i ++) {
                    dynamicNNIndices.get(i).clear();
                    if (!VectorUtil.validPoint(shifting.get(i))) continue;
//                    List<Integer> indices = octree.searchAllNeighborsWithinDistance(i, neighborNumbers.get(i));
                    List<Integer> indices = searchGeodesicNeighbors(i, fiveNN, octree);
                    if (indices.size() < 10) {
                        indices.clear();
                        int[] indicesArray = octree.searchNearestNeighbors(10, i);
                        for (int index :indicesArray) indices.add(index);
                    }
                    dynamicNNIndices.get(i).addAll(indices);
                }
//                computeDensities(shifting, dynamicNNIndices);
            }

            try {
                Thread.sleep(16L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            frozenCount = statFrozenCnt();
            pipeline.getModel().updateCanvasObject(shifting);
            pipeline.getModel().updateCanvasObject(radiusSphere);
            iter += 1;
        }
        if (enableSnapshot) {
            platform.notifyFrontEndSaveSnapshot("Last");
        }
        pipeline.getModel().removeCanvasObject(object);
        points.addAll(shifting);
//        System.out.println("bridge points size: " + points2.size());
    }


    private Comparator<Integer> rawDataDistanceComp(Point3d p) {
        return (o1, o2) -> {
            Double distance = p.distance(dataPoints.get(o1));
            Double distance2 = p.distance(dataPoints.get(o2));
            return distance.compareTo(distance2);
        };
    };


}
