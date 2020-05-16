package cn.edu.cqu.graphics.pipes.lvp;

import cn.edu.cqu.graphics.Constants;
import cn.edu.cqu.graphics.config.FissionConfig;
import cn.edu.cqu.graphics.math.Optimizer;
import cn.edu.cqu.graphics.math.energy.L1Median;
import cn.edu.cqu.graphics.math.energy.L1MedianVariancePenalty;
import cn.edu.cqu.graphics.model.LevelSet;
import cn.edu.cqu.graphics.model.Vertex;
import cn.edu.cqu.graphics.model.tree.TreeSkeletonNode;
import cn.edu.cqu.graphics.model.tree.TreeSkeletons;
import cn.edu.cqu.graphics.protocol.*;
import cn.edu.cqu.graphics.util.MathUtils;
import com.google.gson.Gson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.media.j3d.BoundingSphere;
import javax.vecmath.Point3d;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

@Component
public class Refinement extends CachedPipe {

    private static final int NEGLECT_NODE_NUM = 2;
    public static boolean DEMO_STATE = false;

    @Param(comment = "使用LVP", key = "useNewPoint")
    private Boolean useLVP = true;

    @PipeInput(willBeModified = true)
    @FromPreviousOutput(name = "水平集")
    private LevelSet levelSet;

    @Autowired
    private FissionConfig fissionConfig;

    @PipeInput(willBeModified = true)
    @FromPreviousOutput(name = "树骨架")
    private TreeSkeletons index2Cluster;

    @PipeInput
    @FromPreviousOutput(name = "顶点索引")
    private HashMap<Long, Vertex> index2Vertex;

    @Autowired
    private Optimizer optimizer;

    private TreeSkeletonNode snapshot = new TreeSkeletonNode(-2);

    private Vector<Point3d> guessPoints = new Vector<>();

    @PipeOutput(type = Constants.TYPE_CLUSTER_RADII, name = "顶点半径索引")
    private HashMap<Long, Float> clusterIndex2Radius = new HashMap<>();

    public Refinement() {
    }

    private void refine() {
        Vector<Vector<TreeSkeletonNode>> bins = levelSet.getResult();
        HashSet<Long> checked = new HashSet<>();
        HashSet<Long> tobeRemoved = new HashSet<>();

        for (int i = bins.size() - 1; i >= 0; i--) { // 从顶层levelset 向下迭代，开始检查
            if (i < NEGLECT_NODE_NUM) continue;// i < 13
            Vector<TreeSkeletonNode> vector = bins.get(i);
            for (TreeSkeletonNode cluster : vector) { // 对每层 levelset 的 cluster 开始遍历
                if (tobeRemoved.contains(cluster.getIndex())) continue;//父级被移除的
                if (checked.contains(cluster.getIndex())) continue;//同级的其他cousin
                if (cluster.getParent() == null) continue;//两种情况，一种是root，已经在循环外规避了，还有一种实在root点下面,这是特殊情况
                if (obtainCluster(cluster.getParent()).getChildren().size() > 1) {  // 有 sibling 节点，才考虑要不要对parent进行裂变
                    Vector<TreeSkeletonNode> lowerLevelSet = bins.get(i - 1);
                    lowerLevelSet.remove(obtainCluster(cluster.getParent())); // 暂时移除 parent，此时对树没有影响
                    TreeSkeletonNode grandParent = obtainCluster(obtainCluster(cluster.getParent()).getParent());

                    checkBranchMerge(obtainCluster(cluster.getParent()), tobeRemoved, checked);//重头戏

                    lowerLevelSet.addAll(grandParent.getChildren().stream().map(this::obtainCluster).collect(toList()));
                }
            }
            System.out.println("=============================================");
        }
        saveGuessPoints();
        buildClusterIndex();
        determineSkeletonPoint();
        exportSnapshot(bins);
    }
//
    private void buildClusterIndex() {
        index2Cluster.getIndex2Cluster().clear();
        for (Vector<TreeSkeletonNode> clusters : levelSet.getResult()) {
            for (TreeSkeletonNode cluster : clusters) {
                index2Cluster.putNode(cluster.getIndex(), cluster);
            }
        }
    }

    public void determineSkeletonPoint() {
        for (Map.Entry entry : index2Cluster.getIndex2Cluster().entrySet()) {
            TreeSkeletonNode cluster = (TreeSkeletonNode) entry.getValue();
            List<Point3d> list = cluster.getVertices().stream().map(id -> index2Vertex.get(id).getPosition()).collect(Collectors.toList());

            if (useLVP) {
                cluster.setCentroid(optimizer.computeMedian3d(list, L1MedianVariancePenalty.class));
            } else {
                cluster.setCentroid(optimizer.computeMedian3d(list, L1Median.class));
            }
        }
    }

    private void exportSnapshot(Vector<Vector<TreeSkeletonNode>> bins) {
        for (Vector<TreeSkeletonNode> vector : bins) {
            for (TreeSkeletonNode cluster : vector) {
                if (fissionConfig.getShowFissionIndex().equals(cluster.getIndex())) {
                    saveSnapshot(cluster);
                    break;
                }
            }
        }
    }

    private void saveGuessPoints() {
        PrintStream orignal = System.out;
        try {
            System.setOut(new PrintStream(new FileOutputStream("guess.txt")));
            Gson gson = new Gson();
            System.out.print(gson.toJson(guessPoints));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } finally {
            System.setOut(orignal);
        }
    }

    /**
     * 算法的核心，会在每次从树枝末梢向树干合并时被调用
     * 检查合并时机是否过早
     * @param cluster 有多个孩子的那个intersect area cluster
     * @param tobeRemoved 准备
     * @param checked 整个 refine 过程中已经检查过的 cluster index set
     */
    private void checkBranchMerge(TreeSkeletonNode cluster, HashSet<Long> tobeRemoved, HashSet<Long> checked) {

        Vector<Point3d> kmeansCentroids = guessInitialKMeansCentroid(cluster);

        for (Point3d p : kmeansCentroids) {
            System.out.println(String.format("before fission, %.3f, %.3f, %.3f", p.x, p.y, p.z));
        }

        Vector<HashSet<Long>> clusters = new Vector<>();
        kMeans(cluster, kmeansCentroids, clusters, 5);

//        double threshold = determineFissionThreshold(cluster.getChildren().stream().map(this::obtainCluster).collect(Collectors.toCollection(Vector::new)));
//        nodeFission(cluster, clusters, threshold, tobeRemoved);
        nodeFission(cluster, clusters, 0.0, tobeRemoved, checked);
    }

    private void saveSnapshot(TreeSkeletonNode cluster) {
        if (cluster == null) return;
        snapshot.copy(cluster);
//        for (Long childIndex : cluster.getChildren()) {
//            TreeSkeletonNode child = index2Cluster.getNode(childIndex);
//            TreeSkeletonNode childCopy = new TreeSkeletonNode(cluster.getLevelSetIndex() + 1);
//            childCopy.copy(child);
//        }
    }


    /**
     * 结点裂变
     * @param cluster 可能的原错误骨架点
     * @param newClusters k-means 的聚类结果，把vertex的index的集合划分成几类
     * @param threshold 判断阈值
     * @param tobeRemoved 将要被移除的cluster
     * @param checked 已经被检查过的cluster
     */
    private void nodeFission(TreeSkeletonNode cluster, Vector<HashSet<Long>> newClusters, double threshold, HashSet<Long> tobeRemoved, HashSet<Long> checked) {
        Vector<Long> childrenCopy = new Vector<>(cluster.getChildren());
        List<Point3d> newClusterCenters = new Vector<>();
        threshold = 0.0;
        for (int i = 0; i < newClusters.size(); i++) {
            HashSet<Long> newCluster = newClusters.get(i);
            List<Point3d> list = newCluster.stream().map((index) -> obtainVertex(index).getPosition()).collect(toList());
            Point3d centroid = optimizer.computeMedian3d(list, L1Median.class);
//            Point3d centroid = meanPoint(newCluster);
//            Point3d centroid = ritterCenter(newCluster);
            newClusterCenters.add(centroid);
            System.out.println(String.format("final centroid: %.3f, %.3f, %.3f", centroid.x, centroid.y, centroid.z));

            TreeSkeletonNode tempCluster = obtainCluster(childrenCopy.get(i));//弄copy是 因为结点裂变过程中cluster的孩子动态变化
            double radius = branchDiameter3(tempCluster);
            threshold += (radius * radius);
            clusterIndex2Radius.put(childrenCopy.get(i), (float) radius); //缓存半径数据
        }
        threshold = Math.sqrt(threshold);

        List<Point3d> newClusterCentersCopy = newClusterCenters.stream().filter(p -> !Double.isNaN(p.x)).collect(Collectors.toCollection(Vector::new));
        Point3d compared;

        compared = optimizer.computeMedian3d(newClusterCentersCopy, L1Median.class);//聚类中心的l1

        List<Integer> fissionOrder = new Vector<>();
        for (int i = 0; i < newClusterCenters.size(); i++) fissionOrder.add(i);
        final Point3d finalCompared = compared;
        Collections.sort(fissionOrder, new Comparator<Integer>() {
            @Override
            public int compare(Integer o1, Integer o2) {
                TreeSkeletonNode vc1 = obtainCluster(cluster.getChildren().get(o1));
                TreeSkeletonNode vc2 = obtainCluster(cluster.getChildren().get(o2));
                Double dis1 = vc1.getCentroid().distance(finalCompared);
                Double dis2 = vc2.getCentroid().distance(finalCompared);
                return - dis1.compareTo(dis2);
            }
        });

        for (int j = 0; j < fissionOrder.size(); j++) {
            int i = fissionOrder.get(j);
            HashSet<Long> newCluster = newClusters.get(i);
            if (newCluster.size() < 1) continue;
            checked.add(childrenCopy.get(i));
            Point3d centroid = newClusterCenters.get(i);
            double dis = centroid.distance(compared);
            System.out.println(String.format("dis: %f, threshold:%f", dis, threshold));
            if (dis >= threshold) {
//                System.out.println(("node fission! " ));
                System.out.println(String.format("node fission! i:%d, pos: %.3f, %.3f, %.3f", i, centroid.x, centroid.y, centroid.z));
                nodeFissionHelp(newCluster, cluster, i, childrenCopy);

                newClusterCentersCopy.remove(newClusterCenters.get(i));
                compared = optimizer.computeMedian3d(newClusterCentersCopy, L1Median.class);
            } else {
                System.out.println("no fission");
            }
        }

        if (cluster.getChildren().size() < 1) {
            obtainCluster(cluster.getParent()).getChildren().remove(cluster.getIndex());
            tobeRemoved.add(cluster.getIndex());
        }
    }

    /**
     * 已经确定了要裂变了
     * 这里的centroid的选取不影响这次的裂变，但是会影响下次裂变的决策
     * @param newClusterVertices
     * @param cluster
     * @param i
     * @param childrenCopy
     */
    private void nodeFissionHelp(HashSet<Long> newClusterVertices, TreeSkeletonNode cluster, int i, List<Long> childrenCopy) {
        Point3d centroid = optimizer.computeMedian3d(newClusterVertices, index2Vertex, L1MedianVariancePenalty.class);

//        Point3d centroid = ritterCenter(newClusterVertices);
        System.out.println(String.format("x: %.3f, y: %.3f, z: %.3f**********************************", centroid.x, centroid.y, centroid.z));

        TreeSkeletonNode fissionedCluster = index2Cluster.uniqueNode(cluster.getLevelSetIndex(), newClusterVertices);
        fissionedCluster.setCentroid(centroid);
        fissionedCluster.setParent(cluster.getParent());
        obtainCluster(cluster.getParent()).getChildren().add(fissionedCluster.getIndex());

        fissionedCluster.getChildren().add(childrenCopy.get(i));
        obtainCluster(childrenCopy.get(i)).setParent(fissionedCluster.getIndex());
        cluster.getChildren().remove(childrenCopy.get(i));
        index2Cluster.putNode(fissionedCluster.getIndex(), fissionedCluster);
        removeFissonedVertices(cluster, fissionedCluster);
//        cluster.setCentroid(new Optimizer(cluster.getVertices().stream().map((id) -> obtainVertex(id).getPosition()).collect(toList())).getPoint());
        cluster.setCentroid(optimizer.computeMedian3d(cluster.getVertices(), index2Vertex, L1Median.class));
    }

    private void removeFissonedVertices(TreeSkeletonNode cluster, TreeSkeletonNode fissionedCluster) {
        for (Long vertexIndex : fissionedCluster.getVertices()) {
            cluster.getVertices().remove(vertexIndex);
        }
    }

    private TreeSkeletonNode obtainCluster(Long index) {return index2Cluster.getNode(index);}

    private Vertex obtainVertex(Long index) {return index2Vertex.get(index);}

    public void kMeans(TreeSkeletonNode cluster, Vector<Point3d> centroids, Vector<HashSet<Long>> result, int iters) {
        System.out.println("in kmean, k: " + centroids.size());
//        Vector<HashSet<Long>> result = new Vector<>();
        result.clear();
        for (int i = 0; i < centroids.size(); i++) {
            result.add(new HashSet<>());
        }

        for (int i = 0; i < iters; i++) {
            System.out.println("iter: " + i);
            if (DEMO_STATE) {
                try {
                    Thread.sleep(500L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            synchronized (Refinement.class) {
                for (HashSet set : result) set.clear();
                for (Long vertexIndex : cluster.getVertices()) {//重新划定簇
                    int clusterPosition = determineCluster(obtainVertex(vertexIndex), centroids);
                    result.get(clusterPosition).add(vertexIndex);
                }
                for (int j = 0; j < centroids.size(); j++) {//更新均值向量位置
                    Point3d newCentroid;
                    if (DEMO_STATE) {
                        newCentroid = optimizer.computeMedian3d(result.get(j), index2Vertex, L1Median.class);
//                        newCentroid = optimizer.computeMedian3d(result.getNode(j), index2Vertex, L1MedianWu.class);
                    } else {
                        newCentroid = meanPoint(result.get(j));
//                        newCentroid = new L1MedianPoint3d(
//                                result.getNode(j).stream().map(id -> index2Vertex.getNode(id).getPosition()).collect(toList()),
//                                true).getNewPoint();
                    }
                    centroids.set(j, newCentroid);
                    if (i == iters - 1 && !Double.isNaN(newCentroid.getX()))
                        guessPoints.add(newCentroid); // 聚类中心可能不会被判断算法采纳，但还是要暂存
                }
            }
        }
//        return result;
    }

    private BoundingSphere boundingSphere(Set<Long> vertices) {
        Point3d fakeCentroid = optimizer.computeMedian3d(vertices, index2Vertex, L1Median.class);

        BoundingSphere sphere = new BoundingSphere(fakeCentroid, 0.0);
//        Point3d newCentroid = new Point3d(centroids.getNode(j));
        if (vertices.size() < 1) return sphere;
        Point3d[] array = new Point3d[vertices.size()];
        Iterator it = vertices.iterator();
        for (int k = 0; k < vertices.size(); k++) {array[k] = obtainVertex((Long) it.next()).getPosition();}
        sphere.combine(array);
        return sphere;
    }

    /**
     * Ritter方法近似计算bounding sphere
     * @param vertices
     * @return
     */
    private Point3d ritterCenter(Set<Long> vertices) {
        if (vertices.size() < 1) return new Point3d(Double.NaN, Double.NaN, Double.NaN);
        Point3d result;
        Point3d minX = null, maxX = null, minY = null, maxY = null, minZ = null, maxZ = null;
        for (Long vertexIndex : vertices) {
            Vertex vertex = obtainVertex(vertexIndex);
            for (Point3d p : vertex.getPoints()) {
                if (minX == null || p.x < minX.x) minX = new Point3d(p);
                if (maxX == null || p.x > maxX.x) maxX = new Point3d(p);
                if (minY == null || p.y < minY.y) minY = new Point3d(p);
                if (maxY == null || p.y > maxY.y) maxY = new Point3d(p);
                if (minZ == null || p.z < minZ.z) minZ = new Point3d(p);
                if (maxZ == null || p.z > maxZ.z) maxZ = new Point3d(p);

            }
        }
        float disX = (float) MathUtils.vectorNorm(MathUtils.minus(minX, maxX));
        float disY = (float) MathUtils.vectorNorm(MathUtils.minus(minY, maxY));
        float disZ = (float) MathUtils.vectorNorm(MathUtils.minus(minZ, maxZ));
        float maxDis = disX;
        result = MathUtils.divideBy(MathUtils.add(minX, maxX), 2);
        if (disY > maxDis) {
            maxDis = disY;
            result = MathUtils.divideBy(MathUtils.add(minY, maxY), 2);
        }
        if (disZ > maxDis) {
            maxDis = disZ;
            result = MathUtils.divideBy(MathUtils.add(minZ, maxZ), 2);
        }
        return result;
    }

    private Point3d meanPoint(Set<Long> clusters) {
        if (clusters.size() < 1) return new Point3d(Double.NaN, Double.NaN, Double.NaN);
        double x = 0.0;
        double y = 0.0;
        double z = 0.0;
        for (Long clusterIndex : clusters) {
            x += obtainVertex(clusterIndex).getPosition().x;
            y += obtainVertex(clusterIndex).getPosition().y;
            z += obtainVertex(clusterIndex).getPosition().z;
        }
        return new Point3d(x / clusters.size(), y / clusters.size(), z / clusters.size());
//        List<Point3d> list = clusters.stream().map(id -> obtainVertex(id).getPosition()).collect(Collectors.toCollection(Vector::new));
//        return new L1MedianPoint3d(list).getPoint();
    }

    /**
     * kmeans算法中确定某个点属于哪个簇
     * @param vertex
     * @param centroids
     * @return
     */
    private int determineCluster(Vertex vertex, Vector<Point3d> centroids) {
        int result = 0;
        double shortestDis = 10000000.00;
        for (int i = 0; i < centroids.size(); i++) {
            Point3d point3d = centroids.get(i);
//            double dis = vertex.getPosition().distance(point3d);
            double dis = 0.0;
            for (Point3d p : vertex.getPoints())
                dis += p.distance(point3d);
            if (dis < shortestDis) {
                shortestDis = dis;
                result = i;
            }
        }
        return result;
    }

    /**
     * 最新想法，求取近似直径
     * @param children
     * @return
     */
    private double determineFissionThreshold(Vector<TreeSkeletonNode> children) {
        double result = 0.0;
        for (TreeSkeletonNode cluster : children) {
            double temp = branchDiameter3(cluster);
            result += (temp * temp);
        }
        return Math.sqrt(result) * 0.7;
    }

    /**
     *
     * 初步想法是，所有孩子之间的距离的平均值的三分之一
     * 但是这样会引发一个问题！就是靠近树干的地方还是会浮在点云表面
     * 结点裂变的门限应当越来越大，而且应该和公式结合起来
     * @param orignalSkelPoint
     * @return
     */
    private double branchDiameterChildrenDis(TreeSkeletonNode orignalSkelPoint) {
        double sum = 0.0;
        Iterator iterator = orignalSkelPoint.getChildren().iterator();
        Long firstIndex = (Long) iterator.next();
        while (iterator.hasNext()) {
            Long secondIndex = (Long) iterator.next();
            sum += (obtainCluster(firstIndex).getCentroid().distance(obtainCluster(secondIndex).getCentroid()));
            firstIndex = secondIndex;
        }
        double result = sum / (orignalSkelPoint.getChildren().size() - 1) * 0.5;
        System.out.println("fission threshold: " + result);
        return result;
    }

    /**
     *
     * @param branchNode 分支的第一个结点
     * @return 这个分支的直径
     */
    private double branchDiameter(TreeSkeletonNode branchNode) {
        double result;
        double sum = 0.0;
        double max = 0.0;
        double min = 100000;
        for (Long vertexIndex : branchNode.getVertices()) {
            Vertex vertex = obtainVertex(vertexIndex);

            double dis = vertex.getPosition().distance(branchNode.getCentroid());
            sum += dis;
            if (dis > max) {
                max = dis;
            }
            if (dis < min) {
                min = dis;
            }
        }
        result = sum * 1.0 / branchNode.getVertices().size();
//        System.out.println("min diameter:" + min);
//        System.out.println("max diameter:" + max);
//        System.out.println("aver diameter:" + result);
        return result;
    }

    /**
     *
     * @param branchNode 分支的第一个结点
     * @return 这个分支的直径
     */
    private double branchDiameter2(TreeSkeletonNode branchNode) {
        double result;
        double sum = 0.0;
        double max = 0.0;
        double min = 100000;
        System.out.println("vertex number: " + branchNode.getVertices().size());
        Set<Double> shortestDistance = new TreeSet<>(((o1, o2) -> -o1.compareTo(o2)));
        for (Long vertexIndex : branchNode.getVertices()) {
            Vertex vertex = obtainVertex(vertexIndex);

            double dis = vertex.getPosition().distance(branchNode.getCentroid());
            if (shortestDistance.size() < 25) {
                shortestDistance.add(dis);
            } else if (shortestDistance.iterator().next() > dis) {
                shortestDistance.remove(shortestDistance.iterator().next());
                shortestDistance.add(dis);
            }
        }
        for (Double dis : shortestDistance) sum += dis;
        result = sum * 1.0 / shortestDistance.size();
        System.out.println("aver diameter:" + result);
        return result * 2;
    }

    /**
     * 达芬奇公式
     * 简单向量距离公式
     * @param branchNode
     * @return
     */
    private double branchDiameter3(TreeSkeletonNode branchNode) {
        double radius = Math.sqrt(branchDiameter3Help(branchNode));
        System.out.println("radius: " + radius);
//        return radius * 2 * 1.301449;//1.301449
//        return radius * 2.0 ;//1.301449
        return radius * 1.0 ;//1.301449
//        return radius * 0.85;//1.301449
    }

    /**
     * 返回r^2
     * @param branchNode
     * @return
     */
    private double branchDiameter3Help(TreeSkeletonNode branchNode) {
        double k = 1.0;
        double result = 0.0;
        if (branchNode.getChildren().size() < 1) {
            double temp = branchDiameter(branchNode) * k ;
            result = temp * temp;
        } else if (branchNode.getChildren().size() == 1) {
            Set<Double> distanceSet = new HashSet<>();
            while (branchNode.getChildren().size() == 1) {
                TreeSkeletonNode nextNode = obtainCluster(branchNode.getChildren().iterator().next());
                for (Long vertexIndex : branchNode.getVertices()) {
                    Vertex vertex = obtainVertex(vertexIndex);
                    double distance = MathUtils.distance(vertex.getPosition(), branchNode.getCentroid(), nextNode.getCentroid());
                    distanceSet.add(distance);
                }
                branchNode = nextNode;
            }
            double mean = histogram(distanceSet) * 1.0;
//            double mean = sum / branchNode.getChildren().size();
            result = mean * mean;
        } else {

            for (Long childIndex : branchNode.getChildren()) {
                TreeSkeletonNode childNode = obtainCluster(childIndex);
                result += branchDiameter3Help(childNode);//达芬奇公式
            }
        }
        return result;
    }

    public static double histogram(Set<Double> distanceSet) {
        final int BIN_NUM = 13;
        List<List<Double>> bins = new Vector<>();
        double min = 10000, max = 0.0;
        for (Double d : distanceSet) {
            if (d < min) {
                min = d;
            } else if (d > max) {
                max = d;
            }
        }
        if (max <= min) {System.out.println("error, max <= min"); return 0.0;}
        double binRange = (max - min) / BIN_NUM;
        for (int i = 0; i < BIN_NUM; i++) bins.add(new Vector<>());

        for (Double d : distanceSet) {
            int pos = (int) ((d - min) / binRange);
            if (pos >= BIN_NUM) pos = BIN_NUM - 1;
            bins.get(pos).add(d);
        }
        int maxBinPos = 0;
        for (int i = 1; i < BIN_NUM; i++) {
            if (bins.get(i).size() > bins.get(maxBinPos).size()) {
                maxBinPos = i;
            }
        }
        return min + binRange * (maxBinPos + 0.5);
    }

    private double newClusterDistance2Skeleton(Point3d newBranchCenter, Point3d center, TreeSkeletonNode orignal) {
        if (orignal.getParent().equals(orignal.getIndex()))
            return newBranchCenter.distance(center);
        return MathUtils.distance(newBranchCenter, obtainCluster(orignal.getParent()).getCentroid(), center);
    }


    public Vector<Point3d> guessInitialKMeansCentroid(TreeSkeletonNode cluster) {
        Vector<Point3d> result = new Vector<>();
        for (Long childClusterIndex : cluster.getChildren()) {
            Point3d candidateCentroid = guessNewClusterCenter(cluster, obtainCluster(childClusterIndex));
            result.add(candidateCentroid);
        }
        return result;
    }

    public Point3d guessNewClusterCenter(TreeSkeletonNode cluster, TreeSkeletonNode branchNode) {
        Point3d result = new Point3d();
        if (branchNode.getChildren().size() != 1) {
            double knownShortestDistance = Double.MAX_VALUE;
            for (Long vertexIndex : cluster.getVertices()) {
                Vertex vertex = obtainVertex(vertexIndex);
                double tempDis = obtainBranchDistanceSum(vertex.getPosition(), branchNode);
                if (tempDis < knownShortestDistance) {
                    knownShortestDistance = tempDis;
                    result = vertex.getPosition();
                }
            }
        } else {
//            int cnt = 0;
//            double xsum = 0.0, ysum = 0.0, zsum = 0.0;
//            Point3d nearest = branchNode.getCentroid();
//            Point3d first = branchNode.getCentroid();
//            while (branchNode.getChildren().size() == 1) {
//                TreeSkeletonNode child = obtainCluster(branchNode.getChildren().iterator().next());
//                Point3d second = child.getCentroid();
//                xsum += (first.x - second.x);
//                ysum += (first.y - second.y);
//                zsum += (first.z - second.z);
//                first = second;
//                branchNode = child;
//                cnt += 1;
//            }
//            result.x = nearest.x + (xsum * 1.0 / cnt);
//            result.y = nearest.y + (ysum * 1.0 / cnt);
//            result.z = nearest.z + (zsum * 1.0 / cnt);
            int cnt = 0;
            double xsum = 0.0, ysum = 0.0, zsum = 0.0;
            double segmentLengthSum = 0.0;
            Point3d nearest = branchNode.getCentroid();
            Point3d first = branchNode.getCentroid();
            while (branchNode.getChildren().size() == 1) {
                TreeSkeletonNode child = obtainCluster(branchNode.getChildren().iterator().next());
                Point3d second = child.getCentroid();
                xsum += (first.x - second.x);
                ysum += (first.y - second.y);
                zsum += (first.z - second.z);
                first = second;
                branchNode = child;
                cnt += 1;
            }
            result.x = nearest.x + (xsum * 1.0 / cnt);
            result.y = nearest.y + (ysum * 1.0 / cnt);
            result.z = nearest.z + (zsum * 1.0 / cnt);
        }
        return result;
    }

    /**
     * 因为我们想要某个结点离树枝最近，
     * 这样直接把这个结点当作Kmean的初始原型点就很Ok
     * 直接使点离第一个树枝结点最近似乎不妥
     * @param src
     * @param branchNode
     * @return
     */
    private double obtainBranchDistanceSum(Point3d src, TreeSkeletonNode branchNode) {
//        return src.distance(branchNode.getCentroid());
        double result = 0.0;
//        for (Long vertexIndex : branchNode.getVertices()) {
//            Vertex vertex = obtainVertex(vertexIndex);
//            result += src.distance(vertex.getPosition());
//        }
        result += src.distance(branchNode.getCentroid());
        while (branchNode.getChildren().size() == 1) {
            branchNode = obtainCluster(branchNode.getChildren().iterator().next());
            result += src.distance(branchNode.getCentroid());
        }
        return result;
    }

    public void setClusterIndex2Radius(HashMap<Long, Float> m) {this.clusterIndex2Radius = m;}

    public HashMap<Long, Float> getClusterIndex2Radius() {return this.clusterIndex2Radius;}

    public TreeSkeletonNode getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(TreeSkeletonNode snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    public String getName() {
        return "骨架调整";
    }

    @Override
    public void apply() {

        refine();
    }
}
