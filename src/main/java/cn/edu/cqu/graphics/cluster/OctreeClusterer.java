package cn.edu.cqu.graphics.cluster;

import cn.edu.cqu.graphics.Constants;
import cn.edu.cqu.graphics.config.AlgorithmConfig;
import cn.edu.cqu.graphics.model.PointCloud;
import cn.edu.cqu.graphics.model.Vertex;
import cn.edu.cqu.graphics.model.tree.TreeSkeletonNode;
import cn.edu.cqu.graphics.pipes.lvp.BuildGraph;
import cn.edu.cqu.graphics.platform.DataManager;
import cn.edu.cqu.graphics.util.OctreeUtil;
import com.google.gson.Gson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

/**
 * Created by zjl on 2017/7/7.
 * 利用cell邻接性直接求联通分量
 */
@Component
public class OctreeClusterer {

    private HashMap<Long, Vertex> index2Vertex = null;

    private PointCloud originalData;

    @Autowired
    DataManager manager;

    public OctreeClusterer(DataManager manager) {
        manager.searchCache(this);
    }

    public Vector<Vector<TreeSkeletonNode>> cluster(Vector<TreeSkeletonNode> bins, AlgorithmConfig config) {
        index2Vertex = (HashMap<Long, Vertex>) manager.fetchObject(Constants.TYPE_VERTEX_INDEX, config);
        originalData = (PointCloud) manager.fetchObject(Constants.TYPE_POINT_CLOUD, config);
        Integer octreeLevel = config.getIntegerParam("octreeLevel");
        if (octreeLevel == null) {
            throw new IllegalArgumentException("找不到八叉树深度参数, 应该在Pipe里被@Param注解");
        }

        Vector<Vector<TreeSkeletonNode>> result = new Vector<>();
        HashMap<Long, HashMap<Long, Double>> map = new HashMap<>();
        int cnt = 0;
        for (TreeSkeletonNode skeletonNode : bins) {
            HashMap<Long, HashMap<Long, Double>> weightMap = subgraph(skeletonNode, index2Vertex, octreeLevel);
            if (cnt < 3) { // root点做特殊处理
                Vector<TreeSkeletonNode> vec = new Vector<>();
                vec.add(new TreeSkeletonNode(skeletonNode.getLevelSetIndex(), skeletonNode.getVertices()));
                result.add(vec);
            } else {
//                removeEdges(weightMap, 1.7, octreeLevel);
//                removeEdges(weightMap, 6, octreeLevel);

                result.add(KNNClusterer.obtainConnectedComponents(skeletonNode, weightMap));
            }
            cnt += 1;
            map.putAll(weightMap);
        }
        saveMap(map);
        index2Vertex = null;
        return result;
    }

    private void saveMap(HashMap<Long, HashMap<Long, Double>> weightMap) {
        PrintStream orignal = System.out;
        try {
            System.setOut(new PrintStream(new FileOutputStream("map.txt")));
            Gson gson = new Gson();
            System.out.print(gson.toJson(weightMap));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } finally {
            System.setOut(orignal);
        }
    }

    /**
     * 断开边现在有多种思路：邻接cell的数量、cell中的点密度、中心点距离
//     * @param weightMap
//     * @param adjacentK
     */
//    public void removeEdges(HashMap<Long, HashMap<Long, Double>> weightMap, int adjacentK) {
//        for (HashMap.Entry entry : weightMap.entrySet()) {
//            HashMap map = (HashMap)entry.getValue();
//            if (map.size() <= adjacentK)
//                map.clear();
//        }
//    }

//    private void removeEdges(HashMap<Long, HashMap<Long, Double>> weightMap, double distanceThreshold, int octreeLevel) {
//        for (HashMap.Entry entry : weightMap.entrySet()) {
//            Long index = (Long) entry.getKey();
//            HashMap<Long, Double> map = (HashMap<Long, Double>)entry.getValue();
//            HashSet<Long> toBeRemoved = new HashSet<>();
//            for (Long keyIndex : map.keySet()) {
//                double dis = index2Vertex.get(index).distance(index2Vertex.get(keyIndex));
//                if (dis > distanceThreshold * originalData.getLength() / (Math.pow(2, octreeLevel)))
//                    toBeRemoved.add(keyIndex);
//            }
//            for (Long removeIndex : toBeRemoved)
//                map.remove(removeIndex);
//        }
//    }

    private void removeEdges(HashMap<Long, HashMap<Long, Double>>map, BuildGraph graph) {
        for (HashMap.Entry entry : map.entrySet()) {
            HashMap<Long, Double> innerMap = (HashMap<Long, Double>) entry.getValue();
            Set<Long> tobeRemoved = new HashSet<>();
            double densityThreshold = averageDensity(innerMap );
            for (HashMap.Entry innerEntry : innerMap.entrySet()) {

                if (((Double)innerEntry.getValue()) < 0.1 * densityThreshold) {
                    tobeRemoved.add((Long) innerEntry.getKey());
                }
            }
            for (Long tobeRemovedIndex : tobeRemoved) {
                innerMap.remove(tobeRemovedIndex);
            }
        }
    }

    private double averageDensity(HashMap<Long, Double> weightMap ) {
        double weightSum = 0;
        for (HashMap.Entry entry : weightMap.entrySet()) {
            weightSum += ((Double)entry.getValue());
        }
        return weightSum * 1.0 / weightMap.size();
    }

    /**
     * // TODO: 2017/7/7 可否断开弱关联呢？比如某个节点出入度只有2？
     */
    public static HashMap<Long, HashMap<Long, Double>> subgraph(TreeSkeletonNode cluster, HashMap<Long, Vertex> index2Vertex, int octreeLvel) {
        HashMap<Long, HashMap<Long, Double>> result = new HashMap<>();
        for (Long index : cluster.getVertices()) {
            HashMap<Long, Double> innerTree = new HashMap<>();
            Vector<Long> adjacents = OctreeUtil.obtainAdjacents26(index, octreeLvel);
            for (Long adjacentIndex : adjacents)
                if (cluster.getVertices().contains(adjacentIndex))
                    innerTree.put(adjacentIndex,
                            1.0 * index2Vertex.get(index).getPoints().size()
                                    * index2Vertex.get(adjacentIndex).getPoints().size());
            result.put(index, innerTree);
        }
        return result;
    }
}
