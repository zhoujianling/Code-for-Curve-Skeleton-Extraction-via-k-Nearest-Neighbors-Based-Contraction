package cn.edu.cqu.graphics.pipes.lvp;

import cn.edu.cqu.graphics.Constants;
import cn.edu.cqu.graphics.cluster.OctreeClusterer;
import cn.edu.cqu.graphics.math.Optimizer;
import cn.edu.cqu.graphics.math.energy.L1Median;
import cn.edu.cqu.graphics.math.energy.L1MedianVariancePenalty;
import cn.edu.cqu.graphics.model.LevelSet;
import cn.edu.cqu.graphics.model.Vertex;
import cn.edu.cqu.graphics.model.tree.TreeSkeletonNode;
import cn.edu.cqu.graphics.model.tree.TreeSkeletons;
import cn.edu.cqu.graphics.protocol.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.vecmath.Point3d;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static cn.edu.cqu.graphics.Constants.INFINITE;

@Component
public class InitialSkeleton extends CachedPipe {

    @Param(comment = "使用LVP", key = "useNewPoint")
    private Boolean useLVP = true;

    @PipeOutput(type = Constants.TYPE_ROOT_VERTEX, name = "根顶点")
    private Vertex root;

    @PipeInput
    @FromPreviousOutput(name = "顶点索引")
    private HashMap<Long, Vertex> index2Vertex;

    @PipeInput
    @FromPreviousOutput(name = "邻居图")
    private HashMap<Long, HashMap<Long, Double>> neighborMap;


    @PipeOutput(type = Constants.TYPE_LVP_SKELETON_CURVE, name = "树骨架", visualize = true)
//    @EnableDiskCache
    private TreeSkeletons skeletonData = new TreeSkeletons();

    @PipeOutput(type = Constants.TYPE_LVP_LEVEL_SET, name = "水平集", visualize = true)
//    @EnableDiskCache
    private LevelSet levelSet = new LevelSet();

    private List<Map.Entry<Long, Double>> distanceEntrySet = new ArrayList<>();

    @Temp
    private HashMap<Long, TreeSkeletonNode> index2Cluster = new HashMap<>();

    @PipeOutput(type = Constants.TYPE_GEODESIC, name = "最短路径", visualize = true, visible = false)
    private HashMap<Long, Vector<Long>> paths = new HashMap<>();

    @Autowired
    private Logger logger;

    @Autowired
    private Optimizer optimizer;

    @Autowired
    private OctreeClusterer octreeClusterer;

    @Param(comment = "水平集数量", key = "levelSetNumber", minVal = 1, maxVal = 50)
    private Integer levelSetNumber = 38;//tree2 optimal 38

    private double maxDistance = 0.0;


    private HashMap<Long, Vector<Long>> clusterPaths = new HashMap<>();//key，某个簇的id，Vector<long> 记录最底部簇到这个簇的路径
    private transient HashMap<Long, Double> distances = new HashMap<>();//root到每个图结点的最短距离
    private Vector<Vector<TreeSkeletonNode>> result;
    private TreeSkeletonNode trunkRootClusterNode = new TreeSkeletonNode(-1);

    /**
     *
     */
    private void determineRoot() {
        for (Vertex v : index2Vertex.values()) {
            if (root == null || root.getPosition().y > v.getPosition().y ) {
                root = v;
            }
        }
//        double dis = 100000.0f;
//        for (Vertex v : index2Vertex.values()) {
//            double tempDis = MathUtils.distance(v.getPosition(), new Point3d(3.17619, 1.64409, 7.58607));
//            if (root == null || tempDis < dis) {
//                dis = tempDis;
//                root = v;
//            }
//        }
    }

    /**
     * 要注意一个问题，index2Vertex中的结点不一定在neighborGraph里面
     */
    private void determineShortestPath() {
        HashSet<Long> sSet = new HashSet<>();//用于辅助实现dijkstra算法的S集，维护了局部已知最短路径的节点集合
        initDijkstra(sSet);

        for (int i = 0; i < index2Vertex.size(); i++) {//遍历所有结点，逐个取出加入到S集
            Vertex shortestOutsideS = null; //将 V - S 中离root点最近的点加入到 S 集中，这个距离会一直维护的
            double shortestDistance = INFINITE;
            for (Long vertexIndex : index2Vertex.keySet()) {
                Vertex vertex = index2Vertex.get(vertexIndex);
                if (!sSet.contains(vertexIndex) && distances.get(vertexIndex) < shortestDistance) {
                    shortestOutsideS = vertex;
                    shortestDistance = distances.get(vertexIndex);
                }
            }
            if (shortestOutsideS == null) {
//                logger.info("shortestOutsideS == null");
                continue;
            }

//            logger.info("putNode element into S set, distance: " + shortestDistance);

            //新节点加入到S集
            sSet.add(shortestOutsideS.getIndex());

            //更新root到S集外元素的距离
            for (Long vertexIndex : index2Vertex.keySet()) {
                Vertex uncertainVertex = index2Vertex.get(vertexIndex);
                if (sSet.contains(uncertainVertex.getIndex()))
                    continue;
                Double relayWeight = distances.get(shortestOutsideS.getIndex()) + weight(shortestOutsideS.getIndex(), uncertainVertex.getIndex());
                if (relayWeight < distances.get(uncertainVertex.getIndex())) {
                    distances.put(uncertainVertex.getIndex(), relayWeight);
                    Vector<Long> newPath = new Vector<>(paths.get(shortestOutsideS.getIndex()));
                    newPath.add(uncertainVertex.getIndex());
                    paths.put(uncertainVertex.getIndex(), newPath);

                    if (relayWeight > maxDistance)//聚类成桶的过程需要知道maxDistance，方便做桶的尺度的划分
                        maxDistance = relayWeight;
                }
            }
        }

        List<Long> tobeDeletedIndex = new ArrayList<>();
        for (Long index : distances.keySet()) {
            if (Math.abs(distances.get(index) - INFINITE) < 1e-5) {
                tobeDeletedIndex.add(index);
            }
        }
        for (Long i : tobeDeletedIndex) {
            distances.remove(i);
        }
    }

    private void initDijkstra(HashSet<Long> sSet) {
        paths.clear();
        distances.clear();
        sSet.clear();
        paths.put(root.getIndex(), new Vector<>());
        sSet.add(root.getIndex());

        Iterator iter = index2Vertex.entrySet().iterator();
        while (iter.hasNext()) {
            Vertex vertex = (Vertex) ((Map.Entry)iter.next()).getValue();
            distances.put(vertex.getIndex(), weight(root.getIndex(), vertex.getIndex()));
            Vector<Long> initPath = new Vector<>();
            if (!Objects.equals(vertex.getIndex(), root.getIndex()))
                initPath.add(vertex.getIndex());
            if (Math.abs(weight(root.getIndex(), vertex.getIndex()) -
                    INFINITE) > 1e-5)
                paths.put(vertex.getIndex(), initPath);
            else
                paths.put(vertex.getIndex(), new Vector<>());
        }
    }

    /**
     * 根据rootVertex到每个图节点的测地距离划分水平集
     */
    private void divideIntoLevelSets() {
        levelSet.getLevelSets().clear();
        maxDistance = -1;

        ArrayList<Double> distanceList = new ArrayList<>(distances.values());
        Collections.sort(distanceList);
        for (int i = 0; i < distanceList.size(); i ++) {
            if (Math.abs(distanceList.get(i) - INFINITE) < 1e-5) {
            } else {
                maxDistance = distanceList.get(i);
            }
        }
//        logger.info("MAX DISTANCE: " + maxDistance);

        for (Map.Entry entry : distances.entrySet()) {
            double distance = (double) entry.getValue();
            if (Math.abs(distance - INFINITE) < 1e-5) {
                System.out.println("neglected.");
                continue;
            }
            if (distance > maxDistance) {
                System.out.println("neglected.");
                continue;
            }
            Long index = (long) entry.getKey();
            int vecPos = (int)(distance / ((maxDistance + 1) / levelSetNumber));
//            logger.info("VEC POS: " + vecPos);
            while (vecPos >= levelSet.getLevelSets().size())
                levelSet.getLevelSets().add(new TreeSkeletonNode(levelSet.getLevelSets().size()));
            levelSet.getLevelSets().get(vecPos).getVertices().add(index);
        }
//        refactorRoot();
    }

    private void refactorRoot() {
        if (levelSet.getLevelSets().size() < 2) {return;}
        TreeSkeletonNode root = levelSet.getLevelSets().get(0);
        TreeSkeletonNode second = levelSet.getLevelSets().get(1);
        List<Long> vertices = new Vector<>();
        vertices.addAll(root.getVertices());
        vertices.addAll(second.getVertices());
        root.getVertices().clear();
        second.getVertices().clear();

        vertices.sort((o1, o2) -> {
            double delta = (index2Vertex.get(o1).getPosition().y - index2Vertex.get(o2).getPosition().y);
            if (Math.abs(delta - 0.0) < 1e-5) {
                return 0;
            } else if (delta < 0) {
                return -1;
            } else {
                return 1;
            }
        });

        for (int i = 0; i < vertices.size() / 2; i++) {
            Long vertexIndex = vertices.get(i);
            root.getVertices().add(vertexIndex);
        }
        for (int i = vertices.size() / 2; i < vertices.size(); i ++) {
            Long vertexIndex = vertices.get(i);
            second.getVertices().add(vertexIndex);
        }
    }

    private void levelSetClustering() {
        assert levelSet.getLevelSets().size() > 0;
//        Vector<Vector<TreeSkeletonNode>> result = KNNClusterer.cluster(levelSets, graph);
        result = octreeClusterer.cluster(levelSet.getLevelSets(),  pipeline.getConfig());

//         result = reverseClusterer.cluster(levelSets, graph);
    }

    public void buildClusterIndex() {
        index2Cluster.clear();
        for (Vector<TreeSkeletonNode> clusters : result) {
            for (TreeSkeletonNode cluster : clusters) {
                index2Cluster.put(cluster.getIndex(), cluster);
            }
        }

    }


    /**
     * 经过
     */
    public void reconstructBranchTreeHelp() {
        assert clusterPaths.size() > 0;
        Long rootClusterIndex = clusterPaths.entrySet().iterator().next().getValue().firstElement();

        trunkRootClusterNode = index2Cluster.get(rootClusterIndex);
//        trunkRootClusterNode.copy(index2Cluster.get(rootClusterIndex));
//        index2Cluster.put(rootClusterIndex, trunkRootClusterNode);

        trunkRootClusterNode.setParent(trunkRootClusterNode.getIndex());//// TODO: 2017/7/10 根节点的父亲取成自己
        for (HashMap.Entry entry : clusterPaths.entrySet()) {
            Vector<Long> path = (Vector<Long>) entry.getValue();
            for (int i = 1; i < path.size(); i++) {
                Long currIndex = path.get(i);
                Long prevIndex = path.get(i - 1);
                if (!Objects.equals(currIndex, prevIndex) && index2Cluster.get(currIndex).getParent() == null) {
                    index2Cluster.get(prevIndex).getChildren().add(currIndex);
                    index2Cluster.get(currIndex).setParent(prevIndex);
                }
            }
        }
    }

    /**
     * 建立从root点所在的cluster到每个cluster的path
     */
    public void reconstructClusterGraph() {
        clusterPaths.clear();
        for (Map.Entry entry : index2Cluster.entrySet()) {
            Vector<Long> path = new Vector<>();
            TreeSkeletonNode targetCluster = (TreeSkeletonNode) entry.getValue();
            if (targetCluster.getVertices().size() < 1) continue;
            assert targetCluster.getVertices().size() > 0;
            Long targetVertexIndex = (targetCluster.getVertices().iterator().next());
            Vector<Long> vertexPath = paths.get(targetVertexIndex);//结点路径，而非聚类中心路径
            vertexPath.add(root.getIndex());
            for (Long vertexIndex : vertexPath) {
                Long clusterIndex = obtainContainerIndex(vertexIndex);
                if (path.size() > 0 && path.get(path.size() - 1) == (clusterIndex)
                        || clusterIndex < 0)
                    continue;
                path.add(clusterIndex);
                if (index2Cluster.get(clusterIndex).getLevelSetIndex().equals(targetCluster.getLevelSetIndex() - 1))
                    break;
            }
            path.add(targetCluster.getIndex());
            clusterPaths.put((Long) entry.getKey(), path);
        }
    }

    /**
     *
     * @param vertexIndex
     * @return 如果包含，返回这个簇的id，否则返回 -1
     */
    private Long obtainContainerIndex(Long vertexIndex) {
        Long result = -1L;
        for (Map.Entry entry : index2Cluster.entrySet()) {
            TreeSkeletonNode cluster = (TreeSkeletonNode) entry.getValue();
            if (cluster.getVertices().contains(vertexIndex)) {
                result = (Long) entry.getKey();
                break;
            }
        }
        return result;
    }

    public void determineSkeletonPoint() {
        for (Map.Entry entry : index2Cluster.entrySet()) {
            TreeSkeletonNode cluster = (TreeSkeletonNode) entry.getValue();
            List<Point3d> list = cluster.getVertices().stream().map(id -> index2Vertex.get(id).getPosition()).collect(Collectors.toList());

            if (useLVP) {
                cluster.setCentroid(optimizer.computeMedian3d(list, L1MedianVariancePenalty.class));
            } else {
                cluster.setCentroid(optimizer.computeMedian3d(list, L1Median.class));
            }
//            cluster.setCentroid(l1MedianPoint3d.getPoint());
//            cluster.setCentroid(l1MedianPoint3d.getNewPoint());
        }
    }

    private Double weight(Long index1, Long index2) {
        if (index1.equals(index2)) {return 0.0;}
        HashMap<Long, Double> map = neighborMap.get(index1);
        return map.getOrDefault(index2, INFINITE);
    }



    @Override
    public String getName() {
        return "LVP初始骨架提取";
    }

    @Override
    public void apply() throws FileNotFoundException {
        determineRoot();
        determineShortestPath();
        divideIntoLevelSets();
        levelSetClustering();
        buildClusterIndex();

        reconstructClusterGraph(); //
        reconstructBranchTreeHelp();
        determineSkeletonPoint();

        skeletonData.setIndex2Cluster(index2Cluster);
        skeletonData.setTrunkRootClusterNode(trunkRootClusterNode.getIndex());
        levelSet.setResult(result);
    }

}
