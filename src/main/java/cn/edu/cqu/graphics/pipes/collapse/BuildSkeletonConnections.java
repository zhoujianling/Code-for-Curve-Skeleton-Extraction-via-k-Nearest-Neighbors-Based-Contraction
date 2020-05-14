package cn.edu.cqu.graphics.pipes.collapse;

import cn.edu.cqu.graphics.Constants;
import cn.edu.cqu.graphics.math.Optimizer;
import cn.edu.cqu.graphics.math.energy.CircleObject;
import cn.edu.cqu.graphics.math.energy.L1Median;
import cn.edu.cqu.graphics.model.PointCloud;
import cn.edu.cqu.graphics.model.common.OsculatingPlane;
import cn.edu.cqu.graphics.platform.CanvasObject;
import cn.edu.cqu.graphics.protocol.*;
import cn.jimmiez.pcu.common.graph.Graphs;
import cn.jimmiez.pcu.common.graphics.BoundingBox;
import cn.jimmiez.pcu.common.graphics.Octree;
import cn.jimmiez.pcu.model.Skeleton;
import cn.jimmiez.pcu.util.Pair;
import cn.jimmiez.pcu.util.VectorUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.vecmath.Point2d;
import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Logger;

import static java.lang.Math.*;

/**
 * 2000.MLS 那篇论文里的移动连线法
 */
@Component
public class BuildSkeletonConnections extends CachedPipe {

    @PipeInput
    @FromPreviousOutput(name = "Point Cloud")
    private PointCloud pointCloud;

    @PipeInput
    @FromPreviousOutput(name = "Down-Sampled Points")
    private List<Point3d> samplePoints;

    @PipeInput
    @FromPreviousOutput(name = "Skeletal Points")
    private List<Point3d> collapsedPoints;
//
//    @PipeInput
//    @FromPreviousOutput(name = "Bridge-Points")
//    private List<Point3d> bridgePoints;

    @PipeInput
    @FromPreviousOutput(name = "SIGMA")
    private List<Double> sigmas = new ArrayList<>();

    @PipeInput
    @FromPreviousOutput(name = "PCA主方向")
    private List<Vector3d> pcaDirections = new ArrayList<>();

    @PipeOutput(name = "Curve Skeleton", type = Constants.TYPE_COMMON_SKELETON_CURVE, visualize = true)
    private Skeleton skeleton = new Skeleton();

    @PipeOutput(name = "Curve Skeleton-2", type = Constants.TYPE_COMMON_SKELETON_CURVE, visualize = true, visible = false)
    private Skeleton optimizedSkeleton = new Skeleton();

    @Temp
    private List<SkeletonBranch> branches = new ArrayList<>();

    // 已经处理过的 branch Points 的 Index
    @Temp
    private Set<Integer> visited = new HashSet<>();

    private BoundingBox box;

    @Temp
    private Map<Integer, List<Integer>> skeletonNodeNeighbors = new HashMap<>();

    /** 从 Skeleton 的 vi 到 SP 的映射 **/
    @Temp
    private Map<Integer, SkeletonPoint> vi2Sp = new HashMap<>();

    @Param(comment = "Skeleton-Segments", key = "diagonCnt", minVal = 10, maxVal = 50)
    private Integer diagonCnt = 20;

    /** 分段球半径 **/
    private double radius;

    @Autowired
    private Optimizer optimizer;

    @Autowired
    private Logger logger;

    private Octree collapsedOctree = new Octree();

    private Octree sampleOctree = new Octree();

    /** 一个骨架点最少对应到 5 个降采样数据点**/
    private static final Integer MIN_POINTS_CNT = 5;

    /** 对于每个 branch 的两个端点，都去搜索最近的 5 个骨架点，看看能否连起来 **/
    private static final Integer CHECK_SP_CNT = 5;

    /** 连接两个 branch 所允许的最低的置信度 **/
    @Param(comment = "Branch-Conf-Threshold", key = "branchConnConfThres", minVal = 1, maxVal = 10)
    private Integer branchConnectionConfidenceThreshold = 3;

    /** 搜索下一个 骨架点 所允许的最低的置信度, **/
    @Param(comment = "Node-Conf-Threshold", key = "spConnConfThres", minVal = 1, maxVal = 10)
    private Integer spConnectionConfidenceThreshold = 6;

    /** 插值的松紧程度，大于0则紧，小于0则松 **/
    private static final Double CARDINAL_TENSION = 0.5;

    @Override
    public String getName() {
        return "Connect Skeleton Nodes";
    }


    /**
     * 思考这些情况；
     * 1、branch 的端点连接到别的 skeleton nodes，有必要
     * 2、branch 的端点连接到 bridge points，有必要
     * 3、孤立的 outlier nodes 被 filter
     *
     */
    @SuppressWarnings("Duplicates")
    private void connectBranches() {
        skeleton.clear();
        // 从 SP 到 Skeleton 类里骨架点的 index 的映射, 之后连接 branch 时要用到
        Map<SkeletonPoint, Integer> spMap = new HashMap<>();
        // 从 SP 到 Branch 的映射, 之后做骨架点插值时要用到
        Map<SkeletonPoint, SkeletonBranch> spBranchMap = new HashMap<>();
        // 所有参与第一步连接的 ESP
        Set<SkeletonPoint> sps = new HashSet<>();

        for (SkeletonBranch sb : branches) {
            if (sb.skeletonPoints.size() < 2) continue;
            int prevIndex = skeleton.addVertex(sb.getNegativeESP().point);
            vi2Sp.put(prevIndex, sb.getNegativeESP());
            spMap.put(sb.getNegativeESP(), prevIndex);
            spBranchMap.put(sb.skeletonPoints.get(0), sb);
            for (int i = 1; i < sb.skeletonPoints.size(); i ++) {
                SkeletonPoint curr = sb.skeletonPoints.get(i);
                int currIndex = skeleton.addVertex(curr.point);
                vi2Sp.put(currIndex, curr);
                spMap.put(curr, currIndex);
                spBranchMap.put(curr, sb);
                double distance = curr.point.distance(skeleton.getVertex(prevIndex));
                skeleton.addEdge(prevIndex, currIndex, distance);
                prevIndex = currIndex;
            }
        }

        if (branches.size() < 2 || skeleton.vertices().size() < CHECK_SP_CNT + 1) return;

        /**
         * 连接割裂开来的 branches
         * 对每个 branch 的端点都执行搜索，检查最近的 5 个骨架点，
         */
        for (SkeletonBranch sb : branches) {
            SkeletonPoint candidate1 = searchAnotherSP(sb, true);
            if (candidate1 != null) {
                logger.fine("Candidate1 is NULL");
                SkeletonPoint sp1 = sb.getPositiveESP();
                int sp1Index = spMap.get(sp1);
                int candidateIndex = spMap.get(candidate1);
                // 搞几个插值点
                List<Point3d> interpolatedPoints = interpolate(sb, sp1, spBranchMap.get(candidate1), candidate1);
                if (interpolatedPoints == null) {
                    skeleton.addEdge(sp1Index, candidateIndex, sp1.point.distance(candidate1.point));
                } else {
                    int prevIndex = sp1Index, currIndex = -1;
                    for (Point3d point : interpolatedPoints) {
                        currIndex = skeleton.addVertex(point);
                        vi2Sp.put(currIndex, new SkeletonPoint(point, candidate1.direction, .5));
                        skeleton.addEdge(prevIndex, currIndex, skeleton.getVertex(prevIndex).distance(skeleton.getVertex(currIndex)));
                        prevIndex = currIndex;
                    }
                    currIndex = candidateIndex;
                    skeleton.addEdge(prevIndex, currIndex, skeleton.getVertex(prevIndex).distance(skeleton.getVertex(currIndex)));
                }
                candidate1.isBridge = true;
                sps.add(sp1);
            }

            SkeletonPoint candidate2 = searchAnotherSP(sb, false);
            if (candidate2 != null) {
                logger.fine("Candidate2 is NULL");
                SkeletonPoint sp2 = sb.getNegativeESP();
                int sp2Index = spMap.get(sp2);
                int candidateIndex = spMap.get(candidate2);
                List<Point3d> interpolatedPoints = interpolate(sb, sp2, spBranchMap.get(candidate2), candidate2);
                if (interpolatedPoints == null) {
                    skeleton.addEdge(sp2Index, candidateIndex, sp2.point.distance(candidate2.point));
                } else {
                    int prevIndex = sp2Index, currIndex = -1;
                    for (Point3d point : interpolatedPoints) {
                        currIndex = skeleton.addVertex(point);
                        vi2Sp.put(currIndex, new SkeletonPoint(point, candidate2.direction, .5));
                        skeleton.addEdge(prevIndex, currIndex, skeleton.getVertex(prevIndex).distance(skeleton.getVertex(currIndex)));
                        prevIndex = currIndex;
                    }
                    currIndex = candidateIndex;
                    skeleton.addEdge(prevIndex, currIndex, skeleton.getVertex(prevIndex).distance(skeleton.getVertex(currIndex)));
                }
                candidate2.isBridge = true;
                sps.add(sp2);
            }
        }

    }

    /**
     * 执行第二步连接, 执行对骨架线的强力修复
     * @param branch 当前分支
     * @param mainBranch 主分支
     */
    private void repairBranch(List<Integer> branch, List<Integer> mainBranch) {
        if (branch == null || mainBranch == null) {
            logger.warning("输入参数含null, 跳过");
            return;
        }
        if (branch.size() < 2 || mainBranch.size() < 3) {
            logger.warning("输入参数骨架点过少, 跳过");
            return;
        }

        List<Integer> candidates = new ArrayList<>();
        Map<Integer, Pair<Integer, Double>> distanceMap = new HashMap<>(); // vi 到最近距离的 map
        for (Integer skeletonVi : branch) {
            SkeletonPoint sp = vi2Sp.get(skeletonVi);
            double nearestDistance = Double.MAX_VALUE;
            Integer nearestVi = mainBranch.get(0);
            if (sp == null) {
                logger.warning("sp is null");
            }
            for (Integer mainBranchVi : mainBranch) {
                SkeletonPoint mainBranchSP = vi2Sp.get(mainBranchVi);
                if (mainBranchSP == null) {
                    logger.warning("main sp is null");
                }
                double distance = mainBranchSP.point.distance(sp.point);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestVi = mainBranchVi;
                }
            }
            distanceMap.put(skeletonVi, new Pair<>(nearestVi, nearestDistance));
            candidates.add(skeletonVi);
        }
        candidates.sort((vi1, vi2) -> {
            Double dis1 = distanceMap.get(vi1).getValue();
            Double dis2 = distanceMap.get(vi2).getValue();
            return dis1.compareTo(dis2);
        });

        int cnt = min(5, candidates.size());
        for (int i = 0; i < cnt; i ++) {
            int vi = candidates.get(i);
            int mainVi = distanceMap.get(vi).getKey();
            SkeletonPoint sp = vi2Sp.get(vi);
            SkeletonPoint mainSp = vi2Sp.get(mainVi);
            if (testBridge(sp, mainSp)) {
                optimizedSkeleton.addEdge(vi, mainVi, sp.point.distance(mainSp.point));
                mainBranch.addAll(branch);
                break;
            }
//            Skeleton bridge = new Skeleton();
//            CanvasObject co = new CanvasObject("桥" + vi, Constants.TYPE_COMMON_SKELETON_CURVE, bridge, pipeline.getConfig());
//            pipeline.getModel().addCanvasObject(co);
        }

    }

    private boolean testBridge(SkeletonPoint sp1, SkeletonPoint sp2) {
        boolean result = false;
        double distance = sp1.point.distance(sp2.point);
        Point3d medianPoint = new Point3d(sp1.point);
        medianPoint.add(sp2.point);
        medianPoint.scale(0.5);
        logger.info("distance: " + distance + " .x " + medianPoint.x);
        try {
//            List<Integer> indices = collapsedOctree.searchAllNeighborsWithinDistance(medianPoint, distance / 3.0);
//            if (indices.size() > 2) return true;
            return true;
        } catch (Exception e){}
        return result;
    }


    /**
     * 搜索并连接 branch 算法的实现
     * @param branch 为当前 branch 搜索另一个 sp 与之相连
     * @param positive 这个branch的端点是 anchor 的正方向还是反方向
     * @return 返回要连接的骨架点
     */
    private SkeletonPoint searchAnotherSP(SkeletonBranch branch, boolean positive) {
        SkeletonPoint result = null;
        SkeletonPoint esp = positive ? branch.getPositiveESP() : branch.getNegativeESP();
        Vector3d espDirection = positive ? branch.getPositiveESPDirection() : branch.getNegativeESPDirection();

        PriorityQueue<SkeletonPoint> queue = getEuclideanQueue(esp);
        for (SkeletonBranch skeletonBranch : branches) {
            for (SkeletonPoint skeletonPoint : skeletonBranch.skeletonPoints) {
                if (skeletonPoint == esp) continue;
                queue.add(skeletonPoint);
            }
        }
        List<SkeletonPoint> candidates = new ArrayList<>();
        Map<SkeletonPoint, Double> confidences = new HashMap<>();
        for (int i = 0; i < min(CHECK_SP_CNT, queue.size()); i ++) {
            SkeletonPoint candidate = queue.poll();
            candidates.add(candidate);
            double confidence = computeConnectionConfidence(esp, candidate, espDirection, branch);
            confidences.put(candidate, confidence);
        }
        if (candidates.size() < 1) return null;

//        // 基于 confidence 排序, 大的在前
        candidates.sort((sp1, sp2) -> {
            Double conf1 = confidences.get(sp1);
            Double conf2 = confidences.get(sp2);
            return conf2.compareTo(conf1);
        });
        double branchConnectionConfThresholdReal = branchConnectionConfidenceThreshold / 10.0;
        if (confidences.get(candidates.get(0)) > branchConnectionConfThresholdReal) {
            result = candidates.get(0);
        }
        return result;
    }

//    @SuppressWarnings("Duplicates")
//    private double computeRepairBridgeConfidence(SkeletonPoint esp, SkeletonPoint candidate, Vector3d espDirection) {
//        double confidence = 1.0;
//        if (candidate.isBridge) confidence += 0.5;
//
//        Point3d pp = esp.point;
//        Point3d pj = candidate.point;
//
//        Vector3d ppPj = new Vector3d(pj.x - pp.x, pj.y - pp.y, pj.z - pp.z);
//        double angleJ = ppPj.angle(espDirection);
//        double distance = pp.distance(pj);
//        // *= cos 使得要连接的骨架点尽可能分布在 PCA 方向上
//        // 先乘 cos(angle)，再减倍数，因为我们优先要求这玩意落在PCA方向上
//        confidence -= (distance / box.diagonalLength());
//        confidence *= cos(angleJ);
//
//        return confidence;
//    }

    /**
     * 计算 confidence:通过 PCA 方向上的投影距离进行筛选, PCA 方向上的投影距离尽可能近，
     * @param esp 一个 branch 的端点
     * @param candidate 待连接的骨架点的候选点
     * @param espDirection  这个端点的方向
     * @return 计算 confidence
     */
    @SuppressWarnings("Duplicates")
    private double computeConnectionConfidence(SkeletonPoint esp, SkeletonPoint candidate, Vector3d espDirection, SkeletonBranch branch) {
        double confidence = 1.0;
        Point3d pp = esp.point;
        Point3d pj = candidate.point;

        Vector3d ppPj = new Vector3d(pj.x - pp.x, pj.y - pp.y, pj.z - pp.z);
        double angleJ = ppPj.angle(espDirection);
        double branchLength = branch.computeBranchLength();
        double distance = pp.distance(pj);
        // *= cos 使得要连接的骨架点尽可能分布在 PCA 方向上
        // 先乘 cos(angle)，再减倍数，因为我们优先要求这玩意落在PCA方向上
        confidence *= cos(angleJ);
        confidence -= (distance / (branchLength ));

        return confidence;
    }

    /**
     * 连接两个 branch，可能出现骨架点稀疏的问题，做插值处理
     * @param branch 当前branch
     * @param esp 当前branch的端点
     * @param tb 要连接到的骨架分支 targetBranch
     * @param tsp  要连接到的骨架点
     */
    private List<Point3d> interpolate(SkeletonBranch branch, SkeletonPoint esp, SkeletonBranch tb, SkeletonPoint tsp) {
        if (branch == null || esp == null || tb == null || tsp == null) {
            logger.warning("意外的空指针。");
            return null;
        }
        if (branch.skeletonPoints.size() < 2 || tb.skeletonPoints.size() < 2) return null;
        double length = esp.point.distance(tsp.point);
        int interpolateNum = (int) ((length + 0.5 * radius) / radius) - 1;
        Point3d p1 = null;
        Point3d p2 = esp.point;
        Point3d p3 = tsp.point;
        Point3d p4 = null;
        if (esp == branch.getPositiveESP()) {
            p1 = branch.skeletonPoints.get(branch.skeletonPoints.size() - 2).point;
        } else if (esp == branch.getNegativeESP()) {
            p1 = branch.skeletonPoints.get(1).point;
        }
        int index = tb.skeletonPoints.indexOf(tsp);
        Point3d p4Candidate1 = index > 0 ? tb.skeletonPoints.get(index - 1).point : null;
        Point3d p4Candidate2 = index < tb.skeletonPoints.size() - 1 ? tb.skeletonPoints.get(index + 1).point : null;
        Vector3d espTspDirection = new Vector3d(tsp.point.x - esp.point.x, tsp.point.y - esp.point.y, tsp.point.z - esp.point.z);
        if (p4Candidate1 == null) { // 都为null的情况已经在前面判断了
            p4 = p4Candidate2;
        } else if (p4Candidate2 == null) {
            p4 = p4Candidate1;
        } else {
            Vector3d candidateV1 = new Vector3d(p4Candidate1.x - tsp.point.x, p4Candidate1.y - tsp.point.y, p4Candidate1.z - tsp.point.z);
            p4 = espTspDirection.angle(candidateV1) < PI / 2 ? p4Candidate1 : p4Candidate2;
        }
        if (p1 == null || p4 == null) return null;
        Function<Double, Point3d> fit = cardinalSplineFit(p1, p2, p3, p4);
        List<Point3d> interpolatedPoints = new ArrayList<>();
        for (int i = 0; i < interpolateNum; i ++) {
            double ratio = (i + 1) * 1.0 / (interpolateNum + 1);
            Point3d point = fit.apply(ratio);
            interpolatedPoints.add(point);
        }
        return interpolatedPoints;
    }

    @SuppressWarnings("Duplicates")
    private Function<Double, Point3d> cardinalSplineFit(final Point3d p1, final Point3d p2, final Point3d p3, final Point3d p4) {
        return u -> {
            double s = (1 - CARDINAL_TENSION) / 2;
            double coef1 = (-s * Math.pow(u, 3) + 2 * s * Math.pow(u, 2) - s * u);
            double coef2 = ((2 - s) * Math.pow(u, 3) + (s - 3) * Math.pow(u, 2) + 1);
            double coef3 = ((s - 2) * Math.pow(u, 3) + (3 - 2 * s) * Math.pow(u, 2) + s * u);
            double coef4 = s * Math.pow(u, 3) - s * Math.pow(u, 2);
            float x = (float) (p1.x * coef1 + p2.x * coef2 + p3.x * coef3 + p4.x * coef4);
            float y = (float) (p1.y * coef1 + p2.y * coef2 + p3.y * coef3 + p4.y * coef4);
            float z = (float) (p1.z * coef1 + p2.z * coef2 + p3.z * coef3 + p4.z * coef4);
            return new Point3d(x, y, z);
        };
    }

    private void connectNodes() {
        // 根据 indices 的 sigma 进行排序，优先选取 sigma 大的点作为 anchorPoint
        List<Integer> sortedIndices = new ArrayList<>();
        for (int i = 0; i < samplePoints.size(); i ++) sortedIndices.add(i);
        sortedIndices.sort((index1, index2) -> {
            Double sigma1 = sigmas.get(index1);
            Double sigma2 = sigmas.get(index2);
            return sigma2.compareTo(sigma1);
        });
        for (int i : sortedIndices) {
            // 如果坐标无效或者不构成线性结构，是直接忽视掉的
            if ((! VectorUtil.validPoint(collapsedPoints.get(i))) || sigmas.get(i) < 0.95) {
                visited.add(i); continue;
            }
            if (visited.contains(i)) continue;
            List<Integer> indices = collapsedOctree.searchAllNeighborsWithinDistance(i, radius);
            Vector3d direction = pcaDirections.get(i);
            Point3d localCenter = computeL1MedianInNeighbors(indices);

            int currentNodeIndex = skeleton.addVertex(localCenter);
            skeletonNodeNeighbors.put(currentNodeIndex, indices);

            SkeletonBranch sb = searchOneBranch(localCenter, direction, indices);
            if (sb != null && sb.skeletonPoints.size() > 2) branches.add(sb);
            visited.addAll(indices);
        }
    }

    private SkeletonBranch searchOneBranch(
            Point3d anchor, Vector3d direction, List<Integer> indices) {
        if (!VectorUtil.validPoint(anchor) || indices.size() < MIN_POINTS_CNT) return null;
        SkeletonPoint sp = new SkeletonPoint(anchor, direction, 1.0);
        sp.indices.addAll(indices);

        SkeletonBranch branch = new SkeletonBranch(sp);

        while (true) {
            SkeletonPoint nextPoint = searchNextSkeletonPoint(branch, true);
            if (nextPoint == null) break;
            branch.addPositiveESP(nextPoint);
        }
        visited.removeAll(sp.indices);
        while (true) {
            SkeletonPoint prevPoint = searchNextSkeletonPoint(branch, false);
            if (prevPoint == null) break;
            branch.addNegativeESP(prevPoint);
        }
        return branch;
    }

    private Point3d computeL1MedianInNeighbors(List<Integer> indices) {
        int cnt = Math.min(15, indices.size());
        List<Point3d> points = new Vector<>();
        for (int i = 0; i < cnt; i ++) {
            Point3d point = collapsedPoints.get(indices.get(i));
            points.add(point);
        }
        Point3d localCenter = optimizer.computeMedian3d(points, L1Median.class);
        return localCenter;
    }

    private SkeletonPoint searchNextSkeletonPoint(SkeletonBranch branch, boolean positive) {
        List<SkeletonPoint> candidates = new ArrayList<>();

        SkeletonPoint prevESP = positive ? branch.getPositiveESP() : branch.getNegativeESP();
        Vector3d prevESPDirection = positive ? branch.getPositiveESPDirection() : branch.getNegativeESPDirection();
        Point3d espPoint = prevESP.point;

        PriorityQueue<Integer> queue = getProjectDistanceQueue(prevESP, prevESPDirection);
        for (double currRadius = radius; currRadius <= radius * 3 + 1E-6; currRadius += radius) {
            queue.clear();
            double confidence = 1.0 - ((currRadius - radius) / radius * 0.1);
            List<Integer> neighbors = collapsedOctree.searchAllNeighborsWithinDistance(prevESP.point, currRadius);
            List<Integer> unvisitedNeighbors = new ArrayList<>();
            for (int index : neighbors) {
                // 主方向上 N倍 radius内的点 才被考虑,否则会把临近的其他分支给污染掉
                Point3d point = collapsedPoints.get(index);
                Vector3d preEspCandidate = new Vector3d(point.x - espPoint.x, point.y - espPoint.y, point.z - espPoint.z);
                double angle = preEspCandidate.angle(prevESPDirection);
                double orthLen = preEspCandidate.length() * sin(angle);
                if (! visited.contains(index) && orthLen < radius) {
                    unvisitedNeighbors.add(index);
                }
            }
            if (unvisitedNeighbors.size() < MIN_POINTS_CNT) continue;
            // 计算当前搜索半径内的下一个候选骨架点
            queue.addAll(unvisitedNeighbors);
            Point3d meanPoint = new Point3d();
            Vector3d meanDirection = new Vector3d();
            for (int i = 0; i < MIN_POINTS_CNT; i ++) {
                int furthestPointIndex = queue.poll();
                meanPoint.add(collapsedPoints.get(furthestPointIndex));
                Vector3d pcaDirection = new Vector3d(pcaDirections.get(furthestPointIndex));
                if (pcaDirection.angle(prevESPDirection) > PI / 2) pcaDirection.scale(-1.);
                meanDirection.add(pcaDirection);
            }
            meanPoint.scale(1.0 / MIN_POINTS_CNT);
            meanDirection.scale(1.0 / MIN_POINTS_CNT);
            // 计算候选点的 confidence
            double angle = meanDirection.angle(prevESPDirection);
            confidence *= cos(angle);
            SkeletonPoint candidate = new SkeletonPoint(meanPoint, meanDirection, confidence);
            candidate.indices.addAll(unvisitedNeighbors);
            candidates.add(candidate);
        }
        // 对候选骨架点排序，置信度大的在前面
        candidates.sort((sp1, sp2) -> sp2.confidence.compareTo(sp1.confidence));
        double spConnectionConfidenceThresholdReal = spConnectionConfidenceThreshold / 10.0;
        if (candidates.size() < 1 || candidates.get(0).confidence < spConnectionConfidenceThresholdReal) return null;

        // 确定下一个骨架点，把最佳搜索半径内的所有采样点全部标记为已访问
        SkeletonPoint result = candidates.get(0);
        prevESP.indices.clear();
        prevESP.indices.addAll(result.indices);
//        logger.info("prevESP points size(): " + prevESP.indices.size());
        visited.addAll(prevESP.indices);
        result.indices.clear(); // 清空临时存放的采样点
        return result;
    }

    /**
     * 返回一个最大堆，堆顶元素在某个方向上的投影距离最大
     * @param prevPoint 某个骨架点
     * @param prevDirection 邻域点集的 PCA 主方向, 为什么不直接用 prevPoint 的 pca方向呢？因为
     *                      当第一个搜索的点(anchorPoint)刚好是 branch 的端点时，可能出现负方向相反的情况
     *                      这种情况无法在函数内确认，直接传参更方便
     * @return 优先队列
     */
    private PriorityQueue<Integer> getProjectDistanceQueue(SkeletonPoint prevPoint, Vector3d prevDirection) {
        Point3d pp = prevPoint.point;
        return new PriorityQueue<>((indexI, indexJ) -> {
            Point3d pi = collapsedPoints.get(indexI);
            Point3d pj = collapsedPoints.get(indexJ);
            Vector3d ppPi = new Vector3d(pi.x - pp.x, pi.y - pp.y, pi.z - pp.z);
            Vector3d ppPj = new Vector3d(pj.x - pp.x, pj.y - pp.y, pj.z - pp.z);
            double angleI = ppPi.angle(prevDirection);
            double angleJ = ppPj.angle(prevDirection);
            Double distanceI = ppPi.length() * cos(angleI);
            Double distanceJ = ppPj.length() * cos(angleJ);
            return distanceJ.compareTo(distanceI);
        });
    }

    /**
     * 返回一个最小堆，用于连接多个 branches 时确定哪些骨架点优先连接
     * 第一步通过欧氏距离进行筛选
     * 第二步再通过 垂直分量上的距离来筛（尽可能小）
     * @param endPoint branch 的端点
     * @return 优先队列
     */
    private PriorityQueue<SkeletonPoint> getEuclideanQueue(SkeletonPoint endPoint) {
        Point3d pp = endPoint.point;
        return new PriorityQueue<>((sp1, sp2) -> {
            Point3d pi = sp1.point;
            Point3d pj = sp2.point;
            Vector3d ppPi = new Vector3d(pi.x - pp.x, pi.y - pp.y, pi.z - pp.z);
            Vector3d ppPj = new Vector3d(pj.x - pp.x, pj.y - pp.y, pj.z - pp.z);
            Double distanceI = ppPi.length();
            Double distanceJ = ppPj.length();
            return distanceI.compareTo(distanceJ);
        });
    }

    /**
     * 均匀分布
     */
    private void redistribute() {

//        Set<Integer> visited = new HashSet<>();
//        List<Integer> sortedIndices = new ArrayList<>();
//        sortedIndices.addAll(skeleton.vertices());
//        sortedIndices.sort((o1, o2) -> {
//            Integer size1 = skeleton.adjacentVertices(o1).size();
//            Integer size2 = skeleton.adjacentVertices(o2).size();
//            return size2.compareTo(size1);
//        });
//        for (int i = 0; i < sortedIndices.size(); i ++) {
//            Integer endIndex = sortedIndices.get(i);
//            if (visited.contains(endIndex)) continue;
//            for (int adjacentIndex : skeleton.adjacentVertices(endIndex)) {
//                List<Integer> branchNodesIndices = new ArrayList<>();
//                branchNodesIndices.add(endIndex);
//                int prev = endIndex;
//                int next = adjacentIndex;
//                // ===========
//                boolean jump = false;
//                while (skeleton.adjacentVertices(next).size() == 2) {
//                    if (jump) break;
//                    for (int j : skeleton.adjacentVertices(next)) {
//                        if (visited.contains(j)) { jump = true; continue; }
//                        if (j == prev) continue;
//                        prev = next;
//                        next = j;
//                        if (prev == adjacentIndex) {
//                            branchNodesIndices.add(prev);
//                            visited.add(prev);
//                        }
//                        branchNodesIndices.add(j);
//                        if (skeleton.adjacentVertices(next).size() < 3) {
//                            visited.add(j);
//                        }
//                        break;
//                    }
//                }
//
//                // ===========
//                if (branchNodesIndices.size() > 2) {
////                    System.out.println("indices size: " + branchNodesIndices.size());
//                    redistributeImpl(branchNodesIndices);
//                }
//            }
//        }

    }

    /**
     * 输入一个 branch 的结点 Index
     * 对这些 nodes 均匀分布
     * 如果这些结点成环，那么第一个 Index 和最后一个 index 相等
     *
     * @param branchNodes
     */
    private void redistributeImpl(List<Integer> branchNodes) {
        if (branchNodes.size() < 3) return;
        double totalLength = 0;
        for (int i = 1; i < branchNodes.size(); i ++) {
            Point3d p1 = skeleton.getVertex(branchNodes.get(i - 1));
            Point3d p2 = skeleton.getVertex(branchNodes.get(i ));
            totalLength += p1.distance(p2);
        }
        Point3d first = skeleton.getVertex(branchNodes.get(0));
        Point3d second = skeleton.getVertex(branchNodes.get(1));
        double currentEdge = first.distance(second);
        double segmentLength = totalLength / (branchNodes.size() - 1);
        double edgeLengthSum = 0;

        List<Point3d> newPositions = new ArrayList<>();
        newPositions.add(skeleton.getVertex(branchNodes.get(0)));
        int j = 1;
        for (int i = 1; i < branchNodes.size(); i ++) {
            boolean flag = true;
            while ((edgeLengthSum + currentEdge) < segmentLength * i) {
                flag = false;
                Point3d prev = skeleton.getVertex(branchNodes.get(j - 1));
                Point3d next = skeleton.getVertex(branchNodes.get(j ));
                edgeLengthSum += prev.distance(next);
                j += 1;
                if (j >= branchNodes.size()) break;
                Point3d nextNext = skeleton.getVertex(branchNodes.get(j));
                currentEdge = next.distance(nextNext);
            }
            if (j >= branchNodes.size()) break;

            Point3d p1 = skeleton.getVertex(branchNodes.get(j - 1));
            Point3d p2 = skeleton.getVertex(branchNodes.get(j ));
            double edgeLength = p1.distance(p2);
            if (flag) {
                Point3d latest = newPositions.get(newPositions.size() - 1);
                double u = segmentLength / edgeLength;
                double x = latest.x + (p2.x - p1.x) * u;
                double y = latest.y + (p2.y - p1.y) * u;
                double z = latest.z + (p2.z - p1.z) * u;
                Point3d newPosition = new Point3d(x, y, z);
                newPositions.add(newPosition);
            } else {
                Point3d latest = skeleton.getVertex(branchNodes.get(j - 1));
                double u = (segmentLength - (edgeLengthSum - (i - 1) * segmentLength)) / edgeLength;
                double x = latest.x + (p2.x - p1.x) * u;
                double y = latest.y + (p2.y - p1.y) * u;
                double z = latest.z + (p2.z - p1.z) * u;
                Point3d newPosition = new Point3d(x, y, z);
                newPositions.add(newPosition);
            }
        }

        for (int i = 0; i < newPositions.size(); i ++) {
            skeleton.getVertex(branchNodes.get(i)).set(newPositions.get(i));
        }
    }

    /**
     * 先寻找每个branch对应的点云的部分，然后针对 branch 做居中处理
     * @param branches branches
     */
    private void recenterBranches(List<SkeletonBranch> branches) {
        if (skeleton == null || branches.size() < 2) return;

        /**
         * 本可以确定点集到骨架点的关系，但那样会放大误差。直接搞 Branch 更好
         * 允许蒙皮的重叠，会改善重居中的效果
         */
        Map<SkeletonBranch, List<Integer>> skinningMap = performSkinning();
        for (SkeletonBranch branch : branches) {
            List<Integer> sampleIndices = skinningMap.get(branch);
            recenterBranch(branch, sampleIndices);
        }
    }

    /**
     * 现在有一个骨架线的分支（可视为一个链表），和一段点云，将这段骨架在这段点云中执行重新居中操作
     * 对于每个非ESP 的骨架点，根据 PCADirection得到切线向量， 求一个平面和切线垂直
     * 先挑出到平面小于一定距离的数据点，然后把重居中转化为椭圆拟合问题
     * 首先，我们要解决2维平面上的椭圆拟合问题，这是一个优化问题，损失函数的参数为：
     * 1) center.x  椭圆中点 x
     * 2) center.y  椭圆中点 y
     * 3) a         椭圆的长轴
     * 4) b         椭圆的短轴
     * 5) theta     椭圆的旋转角
     * 然后把中点二维坐标还原到三维空间里,就是重居中后的坐标
     * @param branch 输入链表，非null，至少有3个结点
     * @param indices 输入点云，数据量有保证。
     */
    private void recenterBranch(SkeletonBranch branch, List<Integer> indices) {
        if (indices.size() < samplePoints.size() / 20 ) {
            logger.info("Too less samples, skip.(< 5%)");
            return;
        }
        if (branch.skeletonPoints.size() < 3) {
            logger.info("Too less nodes(< 3)，skip");
            return;
        }
        int pointCount = 0;
        for (SkeletonPoint sp : branch.skeletonPoints) pointCount += sp.indices.size();

        // 需要考虑到环状结构, 可否计算每个数据点到骨架点的亲和度？
        // 先找最近的3个骨架点，然后判断跟 PCA 方向的夹角，要求夹角尽可能垂直，一个点可以被多个骨架点使用【？】
        // 亲和度大于 0.8 的都可以被骨架点使用，这样如何？
        Map<SkeletonPoint, List<Point3d>> affinedPointList = new HashMap<>();
        for (int i = 0; i < branch.skeletonPoints.size(); i ++) {
            List<Point3d> points = new ArrayList<>();
            SkeletonPoint sp = branch.skeletonPoints.get(i);
            affinedPointList.put(sp, points);

            double meanDistance = 0;
            int[] nearestPoints = sampleOctree.searchNearestNeighbors(pointCount / branch.skeletonPoints.size() * 2, sp.point);
            for (int index : nearestPoints) {
                Point3d samplePoint = samplePoints.get(index);
                Vector3d v1 = new Vector3d(sp.point.x - samplePoint.x, sp.point.y - samplePoint.y, sp.point.z - samplePoint.z);
                meanDistance += (sp.point.distance(samplePoint) * sin(v1.angle(sp.direction)));
            }
            meanDistance = meanDistance / (pointCount * 2.0 / branch.skeletonPoints.size());
            for (Point3d samplePoint : pointCloud.getPoints()){
//            for (Point3d samplePoint : samplePoints) {
                Vector3d v1 = new Vector3d(sp.point.x - samplePoint.x, sp.point.y - samplePoint.y, sp.point.z - samplePoint.z);
                double angle = v1.angle(sp.direction);
                double projectionDistance = samplePoint.distance(sp.point) * sin(angle);
                if (angle < PI * 0.65 && angle > PI * 0.35
                        && (projectionDistance < meanDistance * 2)
                ) {
                    points.add(samplePoint);
                }

            }
        }
        // 执行居中
        for (int i = 0; i < branch.skeletonPoints.size(); i ++) {
            SkeletonPoint sp = branch.skeletonPoints.get(i);
            Point3d centroid = recenterSP(branch, sp, affinedPointList.get(sp));
            if (centroid != null && VectorUtil.validPoint(centroid)) {
                sp.point.set(centroid);
            } else {
                if (branch.skeletonPoints.size() < 3) return;
                branch.removeSP(sp);
                i -= 1;
            }
        }
    }

    /**
     * 针对某一个 SP 执行重居中操作
     * @param sp 骨架点
     * @param points 当前 branch 对应的采样点的坐标集合
     * @return 重居中后的坐标，可以为 null
     */
    private Point3d recenterSP(SkeletonBranch branch, SkeletonPoint sp, List<Point3d> points) {
//        double branchLength = branch.computeBranchLength();
        // 设定阈值为3倍平均骨架线段的长度
        if (points.size() < 10)  return null;
        OsculatingPlane orthPlane = new OsculatingPlane(sp.direction, sp.point);

        List<Point2d> point2ds = new ArrayList<>();
        double meanDistance = 0;
        for (Point3d point : points) {
            Point3d projectionPoint = orthPlane.transformFromWorld(point);
            Point2d point2d = new Point2d(projectionPoint.x, projectionPoint.y);
            point2ds.add(point2d);
            meanDistance += sqrt(point2d.x * point2d.x + point2d.y * point2d.y);
        }
        meanDistance /= points.size();
        double[] initialGuess = new double[3];
        initialGuess[0] = 0;
        initialGuess[1] = 0;
        initialGuess[2] = meanDistance;
        double[] result = null;
        try {
            result = optimizer.optimize2d(point2ds, initialGuess, CircleObject.class);
        } catch (Exception e) {}
        if (result == null) return sp.point;
        Point3d point = new Point3d(result[0], result[1], 0);
//        return orthPlane.transformToWorld(point);
//        return optimizer.computeMedian3d(points, L1MedianVariancePenalty.class);
        return sp.point;  // 不居中
//        return optimizer.computeMedian3d(points, L1Median.class);
    }

    /**
     * 执行点云的“蒙皮”计算，对点云进行分段，判断哪些点属于哪个骨架分支
     * 通过置信度规则来选择骨架线
     * @return 哈希表
     */
    private Map<SkeletonBranch, List<Integer>> performSkinning() {
        Map<SkeletonBranch, List<Integer>> result = new HashMap<>();

        List<SkeletonPoint> spList = new ArrayList<>();
        Map<SkeletonPoint, SkeletonBranch> helpMap = new HashMap<>(); // 帮助方便地定位到 branch，可有可无
        for (SkeletonBranch branch : branches) {
            result.put(branch, new ArrayList<>());
            for (SkeletonPoint point : branch.skeletonPoints) {
                spList.add(point);
                helpMap.put(point, branch);
            }
        }

        // sampleSPRelation 是一个和 samplePoints 一样大的列表，保存每个采样点最近的SP
        // 使用欧式距离判断
//        List<SkeletonPoint> sampleSPRelation = new ArrayList<>();
        for (int index = 0; index < samplePoints.size(); index ++) {
            Point3d sample = samplePoints.get(index);
            double minDistance = Double.MAX_VALUE;
            SkeletonPoint nearestSP = null;
            for (SkeletonPoint sp: spList) {
                Point3d v = sp.point;
                double distance = v.distance(sample);
                if (distance < minDistance) {
                    minDistance = distance;
                    nearestSP = sp;
                }
            }
            if (nearestSP == null) throw new IllegalStateException("找不到最近的骨架点。检查是否存在 NaN");
            SkeletonBranch branch = helpMap.get(nearestSP);
            result.get(branch).add(index);
        }

        // 将分割后的点云显示出来
        for (int i = 0; i < branches.size(); i ++) {
            SkeletonBranch sb = branches.get(i);
            List<List<Integer>> indices = new ArrayList<>();
            CanvasObject object = new CanvasObject("点云蒙皮" + i, Constants.TYPE_POINT_CLOUD_SKINNED, samplePoints, pipeline.getConfig());
            indices.add(result.get(sb));
            object.setSecondaryData(indices);
            object.setVisible(false);
//            pipeline.getModel().addCanvasObject(object);
        }
        return result;
    }

    private void init() {
        collapsedOctree.buildIndex(collapsedPoints);
        sampleOctree.buildIndex(samplePoints);

        skeleton.clear();
        optimizedSkeleton.clear();
        skeletonNodeNeighbors.clear();
        visited.clear();
        branches.clear();
        vi2Sp.clear();

        box = BoundingBox.of(pointCloud.getPoints());
        radius = box.diagonalLength() / diagonCnt;
        radius /= 2.0;
    }

    @Override
    public void apply() throws FileNotFoundException {
        init();
        connectNodes();
        recenterBranches(branches);
        connectBranches();

        // 后处理过程
        repairBranches();
//        filterBranches();


        // 重分布的拟策略：间距过小则合并，间距过大则Cardinal插值
        // 当遭遇分叉点的时候，观察哪边的夹角更小,方便选择p4
//        redistribute();


        // 仍然执行 Cardinal 插值，但是是在几个分支的连线之间执行的。
//        CardinalInterpolator interpolator = new CardinalInterpolator(2);
//        interpolator.interpolate(skeleton);
    }

    /**
     * 执行对优化后骨架的最终清理，删除 outlier
     */
    private void filterBranches() {
        if (optimizedSkeleton.vertices().size() < 1) {
            logger.info("没有要清理的骨架，跳过。");
            return;
        }
        List<List<Integer>> branches = Graphs.connectedComponents(optimizedSkeleton);
        List<Integer> mainBranch = branches.get(0);
        List<Integer> willRemove = new ArrayList<>(); // 记录要删除的骨架点
        for (List<Integer> branch : branches) {
            if (branch.size() > mainBranch.size()) {
                mainBranch = branch;
            } else if (branch.size() < 3) {
                willRemove.addAll(branch);
            }
        }
        for (Integer index : willRemove) {
            optimizedSkeleton.removeVertex(index);
        }
    }

    /**
     * 执行对初始骨架断开处的强力修复
     */
    private void repairBranches() {
        if (skeleton.vertices().size() < 1) {
            logger.info("没有要修复的骨架，跳过。");
            return;
        }
        // 复制初始骨架
        for (Integer vi : skeleton.vertices()) {
            optimizedSkeleton.addVertex(vi, skeleton.getVertex(vi));
        }
        for (Integer vi : skeleton.vertices()) {
            for (Integer vj : skeleton.adjacentVertices(vi)) {
                double distance = skeleton.getVertex(vi).distance(skeleton.getVertex(vj));
                optimizedSkeleton.addEdge(vi, vj, distance);
            }
        }
        // 找出骨架主体
        List<List<Integer>> branches = Graphs.connectedComponents(optimizedSkeleton);
        List<Integer> mainBranch = branches.get(0);
        for (List<Integer> branch : branches) {
            if (branch.size() > mainBranch.size()) mainBranch = branch;
        }

        // 计算一下各 branch 到 mainBranch 的最短距离
        Map<List, Double> branch2DistanceMap = new HashMap<>(); // branch 到 mainbranch 最近距离的 map
        for (List<Integer> branch : branches) {
            if (branch == mainBranch) continue;
            Map<Integer, Double> distanceMap = new HashMap<>(); // branch 到 mainbranch 最近距离的 map
            for (Integer skeletonVi : branch) {
                SkeletonPoint sp = vi2Sp.get(skeletonVi);
                double nearestDistance = Double.MAX_VALUE;
                for (Integer mainBranchVi : mainBranch) {
                    SkeletonPoint mainBranchSP = vi2Sp.get(mainBranchVi);
                    double distance = mainBranchSP.point.distance(sp.point);
                    if (distance < nearestDistance) {
                        nearestDistance = distance;
                    }
                }
                distanceMap.put(skeletonVi, nearestDistance);
            }
            double smallestDistance = Double.MAX_VALUE;
            for (double dis : distanceMap.values()) smallestDistance = min(smallestDistance, dis);
            branch2DistanceMap.put(branch, smallestDistance);
        }
        branch2DistanceMap.put(mainBranch, Double.MAX_VALUE);

        branches.sort((o1, o2) -> {
            Double dis1 = branch2DistanceMap.get(o1);
            Double dis2 = branch2DistanceMap.get(o2);
            return dis1.compareTo(dis2);
        });
        for (List<Integer> branch : branches) {
            if (mainBranch == branch) continue;
            // 执行修复过程
            repairBranch(branch, mainBranch);
        }
    }

    private class SkeletonPoint {

        /** 骨架点坐标 **/
        Point3d point = null;

        /** 骨架线的切线方向 **/
        Vector3d direction = null;

        /** 从哪些原始点中降采样出来的骨架点 **/
        List<Integer> indices = new ArrayList<>();

        /** 信度 **/
        Double confidence = .5;

        /** 是不是多个 branch 连接的点 **/
        boolean isBridge = false;

        public SkeletonPoint(Point3d p, Vector3d di, double con) {
            this.point = p;
            this.direction = di;
            this.confidence = con;
        }

        public boolean belongTo(SkeletonBranch branch) {
            return branch.skeletonPoints.contains(this);
        }
    }

    /**
     * 规定第一个加入 SkeletonBranch 的点为 anchorPoint， anchorPoint 的PCA主方向为 Positive方向
     * 反方向为 negative方向，两端的骨架点称为 ESP
     */
    private class SkeletonBranch {

        private List<SkeletonPoint> skeletonPoints = new LinkedList<>();

        private SkeletonPoint anchorPoint = null;

        private boolean isCyclic = false;

        public SkeletonBranch(SkeletonPoint point) {
            anchorPoint = point;
            skeletonPoints.add(point);
        }

        public void addPositiveESP(SkeletonPoint p) {
            if (skeletonPoints.get(0) == p) {
                if (! isCyclic) {
                    isCyclic = true;
                } else {
                    logger.severe("Fatal error! 骨架线已经成环！");
                }
            } else {
                skeletonPoints.add(p);
            }
        }

        public void addNegativeESP(SkeletonPoint p) {
            if (skeletonPoints.get(skeletonPoints.size() - 1) == p) {
                if (! isCyclic) {
                    isCyclic = true;
                } else {
                    logger.severe("Fatal error! 骨架线已经成环！");
                }
            } else {
                skeletonPoints.add(0, p);
            }
        }

        public double computeBranchLength() {
            double length = 0.0;
            for (int i = 1; i < skeletonPoints.size(); i ++) {
                SkeletonPoint curr = skeletonPoints.get(i);
                SkeletonPoint prev = skeletonPoints.get(i - 1);
                length += curr.point.distance(prev.point);
            }
            return length;
        }

        public void removeSP(SkeletonPoint point) {
            if (skeletonPoints.size() < 2)
                throw new IllegalStateException("为什么要删除一个只有1个结点的branch?");
            if (point == anchorPoint) {
                if (point == getPositiveESP()) {
                    anchorPoint = skeletonPoints.get(skeletonPoints.size() - 2);
                }
                if (point == getNegativeESP()) {
                    anchorPoint = skeletonPoints.get(1);
                }
            }
            skeletonPoints.remove(point);
        }

        public SkeletonPoint getPositiveESP () {
            return skeletonPoints.get(skeletonPoints.size() - 1);
        }

        public SkeletonPoint getNegativeESP() {
            return skeletonPoints.get(0);
        }

        public Vector3d getPositiveESPDirection() {
            return getPositiveESP().direction;
        }

        public Vector3d getNegativeESPDirection() {
            SkeletonPoint negativeESP = getNegativeESP();
            Vector3d direction = new Vector3d(negativeESP.direction);
            if (negativeESP == anchorPoint) direction.scale(-1.);
            return direction;
        }

        public boolean isAcyclic() {
            return isCyclic;
        }

    }

}
