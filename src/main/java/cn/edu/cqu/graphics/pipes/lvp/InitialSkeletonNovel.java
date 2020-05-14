package cn.edu.cqu.graphics.pipes.lvp;

import cn.edu.cqu.graphics.Constants;
import cn.edu.cqu.graphics.cluster.KNNClusterer;
import cn.edu.cqu.graphics.cluster.OctreeClusterer;
import cn.edu.cqu.graphics.math.Optimizer;
import cn.edu.cqu.graphics.math.energy.L1Median;
import cn.edu.cqu.graphics.math.energy.L1MedianVariancePenalty;
import cn.edu.cqu.graphics.model.LevelSet;
import cn.edu.cqu.graphics.model.Vertex;
import cn.edu.cqu.graphics.model.tree.TreeSkeletonNode;
import cn.edu.cqu.graphics.model.tree.TreeSkeletons;
import cn.edu.cqu.graphics.protocol.*;
import cn.edu.cqu.graphics.util.MathUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.vecmath.Point3d;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static cn.edu.cqu.graphics.Constants.INFINITE;

/**
 * Created by zjl on 2017/6/20.
 *
 */
@Component
public class InitialSkeletonNovel extends CachedPipe {

    @Param(comment = "使用LVP", key = "useNewPoint")
    private Boolean useLVP = true;

    @Param(comment = "水平集数量", key = "levelSetNumber", minVal = 1, maxVal = 50)
    public Integer binNumber = 38;//tree2 optimal

    @Param(key = "octreeLevel")
    private Integer octreeLevel = 6;

    private double maxGeodesicDistance = 0.0;

    @PipeOutput(type = Constants.TYPE_ROOT_VERTEX, name = "根顶点")
    private Vertex root;

    @PipeInput
    @FromPreviousOutput(name = "顶点索引")
    private HashMap<Long, Vertex> index2Vertex;

    @PipeInput
    @FromPreviousOutput(name = "邻居图")
    private HashMap<Long, HashMap<Long, Double>> neighborMap;


    @PipeOutput(type = Constants.TYPE_LVP_SKELETON_CURVE, name = "树骨架", visualize = true)
    private TreeSkeletons skeletonData = new TreeSkeletons();

    @PipeOutput(type = Constants.TYPE_LVP_LEVEL_SET, name = "水平集", visualize = true)
    private LevelSet levelSet = new LevelSet();

    private List<Map.Entry<Long, Double>> distanceEntrySet = new ArrayList<>();

    @Temp
    private HashMap<Long, Double> distances = new HashMap<>();//root到每个图结点的最短距离

    @Temp
    private HashMap<Long, Vector<Long>> clusterPaths = new HashMap<>();//key，某个簇的id，Vector<long> 记录最底部簇到这个簇的路径

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


    /**
     *
     */
    private void determineRoot() {
//        for (Vertex temp : index2Vertex.values()) {
//            if (root == null || root.getPosition().y > temp.getPosition().y) {
//                root = temp;
//            }
//        }
        double dis = 100000.0f;
        for (Vertex v : index2Vertex.values()) {
            double tempDis = MathUtils.distance(v.getPosition(), new Point3d(3.17619, 1.64409, 7.58607));
            if (root == null || tempDis < dis) {
                dis = tempDis;
                root = v;
            }
        }

//        lowestVertices.clear();
//        Long currentVertexIndex = root.getIndex();
//        int cnt = 0;
//        while (true) {
//            HashMap<Long, Double> disMap = neighborMap.getNode(currentVertexIndex);
//            currentVertexIndex = findLowestNeighbor(disMap, lowestVertices);
//            lowestVertices.add(currentVertexIndex);
//            if (currentVertexIndex.equals(root.getIndex()) && cnt > 4) {
//                System.out.println("==============FIND LOWEST RING!===========");
//                break;
//            }
//            cnt ++;
//            if (cnt >= 1000) {
//                System.out.println("************* ERROR ***************");
//                break;
//            }
//        }
//        Point3d p = optimizer.computeMedian3d(lowestVertices, index2Vertex, L1MedianVariancePenalty.class);
//        logger.info("p.x : " + p.x + " p.y: " + p.y + " p.z: " + p.z);
//        OctreeDownSample box = octree.boundingBox(p);
//        root = new OctreeVertex(box.getIndex(), index2Vertex.getData());
//        root.getPoints().add(p);
//        HashMap<Long, Double> disMap = new HashMap<>();
//        for (Long index : lowestVertices) {
//            Vertex v = index2Vertex.getNode(index);
//            double distance = v.distance(root);
//            disMap.putNode(index, distance);
//            neighborMap.getNode(index).putNode(root.getIndex(), distance);
//        }
//        neighborMap.putNode(root.getIndex(), disMap);
    }


    private Long findLowestNeighbor(HashMap<Long, Double> disMap, Set<Long> excluded) {
        if (disMap.keySet().size() < 3) {
            throw new IllegalStateException("确定根节点失败。");
        }
        Long result = disMap.keySet().iterator().next();
        double y = index2Vertex.get(result).getPosition().y;
        for (Long vertexIndex : disMap.keySet()) {
            if (excluded.contains(vertexIndex)) {
                continue;
            }
            double tempY = index2Vertex.get(vertexIndex).getPosition().y;
            if (tempY < y) {
                y = tempY;
                result = vertexIndex;
            }
        }
        return result;
    }


    public void determineShortestPath() {
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

                    if (relayWeight > maxGeodesicDistance)//聚类成桶的过程需要知道maxDistance，方便做桶的尺度的划分
                        maxGeodesicDistance = relayWeight;
                }
            }

        }
    }

    private void initDijkstra(HashSet<Long> sSet) {
        paths.clear();
        distances.clear();
        sSet.clear();
        paths.put(root.getIndex(), new Vector<>());
        sSet.add(root.getIndex());

        // init distance
        for (Long vertexIndex : index2Vertex.keySet()) {
            Vertex vertex = index2Vertex.get(vertexIndex);
            distances.put(vertex.getIndex(), weight(root.getIndex(), vertex.getIndex()));
            Vector<Long> initPath = new Vector<>();
            if (!Objects.equals(vertex.getIndex(), root.getIndex()))
                initPath.add(vertex.getIndex());
            if (Math.abs(weight(root.getIndex(), vertex.getIndex()) - INFINITE) > 1e-5)
                paths.put(vertex.getIndex(), initPath);
            else
                paths.put(vertex.getIndex(), new Vector<>());
        }
    }

    public Double weight(Long index1, Long index2) {
        if (index1.equals(index2)) {return 0.0;}
        HashMap<Long, Double> map = neighborMap.get(index1);
        return map.getOrDefault(index2, INFINITE);
    }

    /**
     * 根据rootVertex到每个图节点的测地距离划分水平集
     */
    private void skeletonization() {
        levelSet.getLevelSets().clear();
        maxGeodesicDistance = -1;

        distanceEntrySet.clear();
        distanceEntrySet.addAll(distances.entrySet());
        distanceEntrySet.sort(Comparator.comparing(Map.Entry::getValue));

        double maxGeodesicDistance = 0;
        int leftPointer = distanceEntrySet.size() - 1;
        int rightPointer = distanceEntrySet.size() - 1;
        for (int i = distanceEntrySet.size() - 1; i >= 0; i --) {
            if (! distanceEntrySet.get(i).getValue().equals(INFINITE)) {
                maxGeodesicDistance = distanceEntrySet.get(i).getValue();
                break;
            }
        }
        double highGeodesicDistance = maxGeodesicDistance;
        double clusterGeodesicDisSpan = maxGeodesicDistance * 0.04; // 高度
        double clusterGeoDisIteDelta = clusterGeodesicDisSpan * 0.5; //每次下滑的距离
        double lowGeodesicDistance = highGeodesicDistance - clusterGeodesicDisSpan;
        int cnt = 0;
        while (true) {
            while (distanceEntrySet.get(rightPointer).getValue() > highGeodesicDistance) { rightPointer --;}
            while (distanceEntrySet.get(leftPointer).getValue() > lowGeodesicDistance) {leftPointer --;}
            performClustering(leftPointer, rightPointer);
//            logger.info("Cluster one level: " + cnt);
            cnt += 1;
            if (Math.abs(lowGeodesicDistance - 0.0) < 1e-7) {
                break;
            }
            highGeodesicDistance -= clusterGeoDisIteDelta;
            lowGeodesicDistance -= clusterGeoDisIteDelta;
            if (lowGeodesicDistance < clusterGeodesicDisSpan) lowGeodesicDistance = 0.0;
        }


//        for (Map.Entry entry : distances.entrySet()) {
//            double distance = (double) entry.getValue();
//            if (Math.abs(distance - graph.getMaxDistance()) < 1e-5) {
//                System.out.println("neglected.");
//                continue;
//            }
//            if (distance > maxGeodesicDistance) {
//                System.out.println("neglected.");
//                continue;
//            }
//            Long index = (long) entry.getKey();
//            int vecPos = (int)(distance / ((maxGeodesicDistance + 1) / binNumber));
//            while (vecPos >= levelSets.size())
//                levelSets.add(new TreeSkeletonNode(levelSets.size()));
//            levelSets.getNode(vecPos).getVertices().add(index);
//        }
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

    private void performClustering(int leftPointer, int rightPointer) {
        HashSet<Long> vertices = new HashSet<>();
        vertices.addAll(distanceEntrySet.subList(leftPointer, rightPointer + 1).stream().map(Map.Entry::getKey).collect(Collectors.toList()));
        TreeSkeletonNode skeletonNode = new TreeSkeletonNode(100, vertices);

        HashMap<Long, HashMap<Long, Double>> weightMap = OctreeClusterer.subgraph(skeletonNode, index2Vertex, octreeLevel);
        Vector<TreeSkeletonNode> vec = (KNNClusterer.obtainConnectedComponents(skeletonNode, weightMap));
        levelSet.getResult().add(vec);
        for (TreeSkeletonNode vc : vec) {
            index2Cluster.put(vc.getIndex(), vc);
//            index2Cluster.putNode(vc.getIndex(), vc);
        }
        buildClusterRelation();
    }

    /**
     * 定义重合度：假如有 Root -> A -> B
     * 对于 A 而言，A 和 B 的重合度就是 (共同 vertices 数量) / B 的 vertices 数量
     */
    private void buildClusterRelation() {
        if (levelSet.getResult().size() < 2) return;
        Vector<TreeSkeletonNode> currents = levelSet.getResult().get(levelSet.getResult().size() - 1);
        Vector<TreeSkeletonNode> children = levelSet.getResult().get(levelSet.getResult().size() - 2);
        for (TreeSkeletonNode cluster : currents) {
            for (TreeSkeletonNode child : children) {
                
            }
        }
    }


    private void levelSetClustering() {
        assert levelSet.getLevelSets().size() > 0;
//        Vector<Vector<TreeSkeletonNode>> result = KNNClusterer.cluster(levelSets, graph);
        levelSet.setResult(octreeClusterer.cluster(levelSet.getLevelSets(), pipeline.getConfig()));

//         result = reverseClusterer.cluster(levelSets, graph);
    }



    /**
     * 经过
     */
    public void reconstructBranchTreeHelp() {
        assert clusterPaths.size() > 0;
        Long rootClusterIndex = clusterPaths.entrySet().iterator().next().getValue().firstElement();

        TreeSkeletonNode trunkRootClusterNode = index2Cluster.get(rootClusterIndex);
//        trunkRootClusterNode.copy(index2Cluster.getNode(rootClusterIndex));
//        index2Cluster.putNode(rootClusterIndex, trunkRootClusterNode);
        trunkRootClusterNode.setParent(trunkRootClusterNode.getIndex());//// TODO: 2017/7/10 根节点的父亲取成自己
        skeletonData.setTrunkRootClusterNode(rootClusterIndex);

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

    public void buildClusterIndex() {
        index2Cluster.clear();
        for (Vector<TreeSkeletonNode> clusters : levelSet.getResult()) {
            for (TreeSkeletonNode cluster : clusters) {
                index2Cluster.put(cluster.getIndex(), cluster);
            }
        }
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


    public HashMap<Long, Vertex> getIndex2Vertex() {
        return index2Vertex;
    }

    public void setIndex2Vertex(HashMap<Long, Vertex> index2Vertex) {
        this.index2Vertex = index2Vertex;
    }

    public Vertex getRoot() {
        return root;
    }

    public void setRoot(Vertex v) {
        this.root = v;
    }

    public List<Map.Entry<Long, Double>> getDistanceEntrySet() {return distanceEntrySet;}

    public HashMap<Long, Double> getDistances() {
        return distances;
    }

    private void loadOutput() {
        skeletonData.setIndex2Cluster(index2Cluster);
//        geodesicData.setPaths(paths);
    }

    @Override
    public String getName() {
        return "提取初始骨架";
    }

    @Override
    public void apply() {
        determineRoot();
        determineShortestPath();
        skeletonization();
//        levelSetClustering();
//        buildClusterIndex();
//
        reconstructClusterGraph(); //
        reconstructBranchTreeHelp();
        determineSkeletonPoint();
        loadOutput();
    }
}
