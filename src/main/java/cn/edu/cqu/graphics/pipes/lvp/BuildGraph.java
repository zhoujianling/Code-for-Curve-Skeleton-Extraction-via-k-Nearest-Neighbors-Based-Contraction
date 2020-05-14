package cn.edu.cqu.graphics.pipes.lvp;

import cn.edu.cqu.graphics.Constants;
import cn.edu.cqu.graphics.math.Optimizer;
import cn.edu.cqu.graphics.model.OctreeVertex;
import cn.edu.cqu.graphics.model.PointCloud;
import cn.edu.cqu.graphics.model.Vertex;
import cn.edu.cqu.graphics.protocol.*;
import cn.edu.cqu.graphics.util.OctreeUtil;
import javafx.util.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.*;

/**
 * Created by zjl on 2017/6/20.
 * 适用于被八叉树降采样的邻居图构造
 * 注意weightMap 有坑
 * 根据分出来的格子的中心点构造图
 */
@Component
public class BuildGraph extends CachedPipe {

    @Param(comment = "k近邻数", key = "nearestK", minVal = 3, maxVal = 124)
    private Integer NEAREST_NEIGHBOR_K = 124;//K近邻的k取值

    @Param(comment = "8叉树深度", key = "octreeLevel", minVal = 3, maxVal = 6)
    private Integer octreeLevel = 6;//K近邻的k取值

    @PipeInput
    @FromPreviousOutput(name = "输入点云")
    PointCloud data;

    @Autowired
    Optimizer optimizer;

//    private double boundingBoxLength;


    @PipeInput
    @FromPreviousOutput(name = "顶点索引")
    public HashMap<Long, Vertex> index2Vertex;// = new HashMap<>();//格子index到格子的映射

    @PipeOutput(type = Constants.TYPE_NEIGHBOR_GRAPH, name = "邻居图", visualize = true, visible = false)
    private HashMap<Long, HashMap<Long, Double>> weightMap = new HashMap<>();//记录权重


    private void buildGraph() {

        computeWeight();
//        removeEdges();
        removeVertices();
//        exportDistanceList();
    }

    private void removeVertices() {
        Set<Long> tobeRemovedVertexIndex = new HashSet<>();
        for (Long vertexIndex : index2Vertex.keySet()) {
            if (weightMap.get(vertexIndex) == null) {
                tobeRemovedVertexIndex.add(vertexIndex);
            } else if (weightMap.get(vertexIndex).size() < 1) {
                weightMap.remove(vertexIndex);
                tobeRemovedVertexIndex.add(vertexIndex);
            }
        }
        for (Long index : tobeRemovedVertexIndex) {
            index2Vertex.remove(index);
        }
    }


    private void removeEdges() {
        final double RESERVE_RATIO = 0.65;
        List<Double> distanceSet = new ArrayList<>();
        Map<Double, Pair<Long, Long>> dis2keyPair = new HashMap<>();
        for (Long outterIndex : weightMap.keySet()) {
            HashMap<Long, Double> temp = weightMap.get(outterIndex);
            for (Long innerIndex : temp.keySet()) {
                double dis = temp.get(innerIndex);
                distanceSet.add(dis);
                dis2keyPair.put(dis, new Pair<>(outterIndex, innerIndex));
            }
        }
        distanceSet.sort(Double::compareTo);
        for (int i = 0; i < distanceSet.size(); i++) {
            double pos = i * 1.0 / distanceSet.size();
            if (
//                    pos < (1.0 - RESERVE_RATIO) / 2.0 ||
                    pos > (1.0 + RESERVE_RATIO) / 2.0) { // 去掉正态分布两侧的边
                Pair<Long, Long> keyPair = dis2keyPair.get(distanceSet.get(i));
                weightMap.get(keyPair.getKey()).remove(keyPair.getValue());
                weightMap.get(keyPair.getValue()).remove(keyPair.getKey());
            }
        }
    }


    public static void NN124(Set<Long> vertices, HashMap<Long, Vertex> index2Vertex, HashMap<Long, HashMap<Long, Double>> weightMap, int octreeLevel) {
        weightMap.clear();
        for (Long index : vertices) {
            HashMap<Long, Double> edges = new HashMap<>();
            Set<Long> edgeTargetIndexSet = new HashSet<>();
            Vector<Long> vector = OctreeUtil.obtainAdjacents26(index, octreeLevel);
            for (Long edgeTargetIndex : vector) {
                edgeTargetIndexSet.add(edgeTargetIndex);
                for (Long outterIndex : OctreeUtil.obtainAdjacents26(edgeTargetIndex, octreeLevel)) {
                    if (!index.equals(outterIndex))
                        edgeTargetIndexSet.add(outterIndex);
                }
            }
            for (Long adjacentIndex : edgeTargetIndexSet) {
                if (index2Vertex.containsKey(adjacentIndex)) {
                    Double distance = index2Vertex.get(index).distance(index2Vertex.get(adjacentIndex));
                    edges.put(adjacentIndex, distance);
                }
            }
            weightMap.put(index, edges);
        }
    }

    public void NN26(Set<Long> vertices, HashMap<Long, Vertex> index2Vertex, HashMap<Long, HashMap<Long, Double>> weightMap) {
        weightMap.clear();
        for (Long index : vertices) {
            HashMap<Long, Double> edges = new HashMap<>();
            Vector<Long> vector = OctreeUtil.obtainAdjacents26(index, octreeLevel);
            for (Long edgeTargetIndex : vector) {
                for (Long outterIndex : OctreeUtil.obtainAdjacents26(edgeTargetIndex, octreeLevel)) {
                    if (!index.equals(outterIndex) && index2Vertex.containsKey(outterIndex)) {
                        Double distance = index2Vertex.get(index).distance(index2Vertex.get(outterIndex));
                        edges.put(outterIndex, distance);
                    }
                }
            }
            weightMap.put(index, edges);
        }
    }

    /**
     * 求K近邻子图
     */
    public static void kNN(int k, Set<Long> vertices, HashMap<Long, Vertex> i2v, boolean usePhysicalDis, HashMap<Long, HashMap<Long, Double>> weightMap) {
        weightMap.clear();
        for (Long index : vertices)
            weightMap.put(index, new HashMap<>());
        int cnt = 0;
        for (Long outerVertexIndex : vertices) {

//            System.out.println("current: " + cnt + " total size: " + vertices.size());
            cnt ++ ;

            TreeMap<Double, Vertex> weightSortMap = new TreeMap<>();
            Vertex firstVertex = i2v.get(outerVertexIndex);
            for (Long innerVertexIndex : vertices) {
                Vertex secondVertex = i2v.get(innerVertexIndex);
                if (Objects.equals(secondVertex.getIndex(), firstVertex.getIndex()))
                    continue;
                double weight;
                weight = firstVertex.distance(secondVertex);//计算两点之间的权重
                weightSortMap.put(weight, secondVertex);
            }
            Iterator sortIter = weightSortMap.entrySet().iterator();
            for (int i = 0; i < k; i++) {
                if (!sortIter.hasNext())
                    break;
                Map.Entry nearestEntry = (Map.Entry) sortIter.next();
                Vertex nearestVertex = (Vertex) nearestEntry.getValue();
                weightMap.get(outerVertexIndex).put(nearestVertex.getIndex(), (Double) nearestEntry.getKey());
                weightMap.get(nearestVertex.getIndex()).put(outerVertexIndex, (Double) nearestEntry.getKey());
                //无向图，应该双向都有权重////
            }
        }
    }


//    /**
//     * 求K近邻子图, 每个结点最近的k个结点连接起来
//     * @param k
//     * @param vertices
//     * @param i2v
//     * @param usePhysicalDis
//     * @return
//     */
//    public static HashMap<Long, HashMap<Long, Double>> kNN(int k, Set<Long> vertices, HashMap<Long, OctreeVertex> i2v, boolean usePhysicalDis) {
//        HashMap<Long, HashMap<Long, Double>> map = new HashMap<>();
//        for (Long index : vertices)
//            map.putNode(index, new HashMap<>());
//        //分别求每个结点的k近邻
//        final int LEVEL_K = Constants.OCTREE_LEVEL;
//        for (Long outerVertexIndex : vertices) {
//            HashSet<Long> visited = new HashSet<>();
//            TreeMap<Double, Long> weightSortMap = new TreeMap<>((Comparator<Double>) (o1, o2) -> -o1.compareTo(o2));
//            for (int i = 1; i < LEVEL_K; i ++) {
//                int hit = 0;//命中次数
//                long shiftedIndex = outerVertexIndex >> (LEVEL_K - i);//到底要shift多少位？
//                if (hit == 0)
//                    break;
//            }
//            Iterator sortIter = weightSortMap.entrySet().iterator();
//            for (int i = 0; i < k; i++) {
//                if (!sortIter.hasNext())
//                    break;
//                Map.Entry nearestEntry = (Map.Entry) sortIter.next();
//                Vertex nearestVertex = (OctreeVertex) nearestEntry.getValue();
//                map.getNode(outerVertexIndex).putNode(nearestVertex.getIndex(), (Double) nearestEntry.getKey());
//                map.getNode(nearestVertex.getIndex()).putNode(outerVertexIndex, (Double) nearestEntry.getKey());
//                //无向图，应该双向都有权重////
//            }
//        }
//        return map;
//    }


    private void computeWeight() {
        weightMap.clear();
        if (index2Vertex.size() >= 2) {
            Iterator<Vertex> it =index2Vertex.values().iterator();
            Vertex randomA = it.next();
            Vertex randomB = it.next();
            if (randomA instanceof OctreeVertex
                    && randomB instanceof OctreeVertex
                    && NEAREST_NEIGHBOR_K == 26) {
                NN26(index2Vertex.keySet(), index2Vertex, weightMap);
            } else if (randomA instanceof OctreeVertex
                    && randomB instanceof OctreeVertex
                    && NEAREST_NEIGHBOR_K == 124){
                NN124(index2Vertex.keySet(), index2Vertex, weightMap, octreeLevel);
            } else {
                kNN(NEAREST_NEIGHBOR_K, index2Vertex.keySet(), index2Vertex, true, weightMap);
            }
        }
//        weightMap = kNN(NEAREST_NEIGHBOR_K, index2Vertex.keySet(), index2Vertex, true);
//        weightMap = NN124(index2Vertex.keySet(), index2Vertex);
    }

    private void exportDistanceList() {
        PrintStream sys = System.out;
        Set<Double> distanceSet = new HashSet<>();
        for (Long outterIndex : weightMap.keySet()) {
            HashMap<Long, Double> temp = weightMap.get(outterIndex);
            for (Long innerIndex : temp.keySet()) {
                distanceSet.add(temp.get(innerIndex));
            }
        }
        try {
            System.setOut(new PrintStream(new FileOutputStream("distance.txt")));
            for (Double dis : distanceSet) {
                System.out.print(dis);
                System.out.print('\n');
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        System.setOut(sys);
    }

    public void setIndex2Vertex(HashMap<Long, Vertex> index2Vertex) {
        this.index2Vertex = index2Vertex;
    }

    public HashMap<Long, Vertex> getIndex2Vertex() {
        return this.index2Vertex;
    }


    public double pointsDensity() {
        int pointCount = 0;
        for (Vertex vertex : index2Vertex.values()) {
            pointCount += vertex.getPoints().size();
        }
        return pointCount * 1.0 / index2Vertex.size();
    }


    @Override
    public String getName() {
        return "构造邻接图";
    }

    @Override
    public void apply() {
//        buildIndex2Vertex(octree);
        buildGraph();
        System.out.println("Size: " + index2Vertex.size());
        System.out.println("======================================");
    }
}
