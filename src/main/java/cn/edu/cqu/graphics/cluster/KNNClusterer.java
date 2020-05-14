package cn.edu.cqu.graphics.cluster;

import cn.edu.cqu.graphics.model.Vertex;
import cn.edu.cqu.graphics.model.VertexPair;
import cn.edu.cqu.graphics.model.tree.TreeSkeletonNode;
import cn.edu.cqu.graphics.pipes.lvp.BuildGraph;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Vector;

/**
 * Created by zjl on 2017/6/24.
 * 1999.
 */
@Component
public class KNNClusterer implements BinClusterer {

//    @Autowired
    HashMap<Long, Vertex> index2Vertex;

    @Override
    public Vector<Vector<TreeSkeletonNode>> cluster(Vector<TreeSkeletonNode> bins, HashMap<Long, HashMap<Long, Double>> graph) {
        Vector<Vector<TreeSkeletonNode>> result = new Vector<>();
        for (TreeSkeletonNode bin : bins) {
            HashMap<Long, HashMap<Long, Double>> weightMap = new HashMap<>();
            BuildGraph.kNN(3, bin.getVertices(), index2Vertex, true, weightMap);
            double w = 0.6 * medianWeight(weightMap);
            removeEdge(w, weightMap);
            result.add(obtainConnectedComponents(bin, weightMap));
        }
        return result;
    }

    private double medianWeight(HashMap<Long, HashMap<Long, Double>> weightMap) {
        double result = 0.0;
        for (Map.Entry entry : weightMap.entrySet()) {
            HashMap<Long, Double> innerMap = (HashMap<Long, Double>) entry.getValue();
            for (Map.Entry innerEntry : innerMap.entrySet()) {
                Double weight = (Double) innerEntry.getValue();
                result += weight;
            }
        }
        return result / weightMap.size();
    }

    private void removeEdge(double weightThreshold, HashMap<Long, HashMap<Long, Double>> weightMap) {
        Vector<VertexPair> removeCandidates = new Vector<>();
        for (Map.Entry entry : weightMap.entrySet()) {
            HashMap<Long, Double> innerMap = (HashMap<Long, Double>) entry.getValue();
            for (Map.Entry innerEntry : innerMap.entrySet()) {
                Double weight = (Double) innerEntry.getValue();
                if (weight > weightThreshold)
                    removeCandidates.add(new VertexPair((Long)entry.getKey(), (Long) innerEntry.getKey()));
            }
        }

        for (VertexPair pair : removeCandidates) {
            weightMap.get(pair.getIndexA()).remove(pair.getIndexB());
        }
    }

    public static Vector<TreeSkeletonNode> obtainConnectedComponents(TreeSkeletonNode cluster, HashMap<Long, HashMap<Long, Double>> weightMap) {
        Vector<TreeSkeletonNode> result = new Vector<>();
        HashSet<Long> vertices = cluster.getVertices();
        HashMap<Long, Boolean> visited = new HashMap<>();
        for (Long vertexIndex : vertices) {
            visited.put(vertexIndex, false);
        }

        for (Long vertexIndex : vertices) {
            if (visited.get(vertexIndex))
                continue;
            HashSet<Long> connectedComponent = new HashSet<>();
            connectedComponentHelp(vertexIndex, vertices, connectedComponent, weightMap, visited);
            TreeSkeletonNode newCluster = new TreeSkeletonNode(cluster.getLevelSetIndex(), connectedComponent);
            result.add(newCluster);
        }
        return result;
    }

    private static void connectedComponentHelp(
            Long index,
            HashSet<Long> vertices,
            HashSet<Long> connectedComponent,
            HashMap<Long, HashMap<Long, Double>> weight,
            HashMap<Long, Boolean> visited) {
        if (visited.get(index))
            return;
        connectedComponent.add(index);
        visited.put(index, true);
        for (Long vertexIndex : vertices) {
            if (weight.get(index).containsKey(vertexIndex))
                connectedComponentHelp(vertexIndex, vertices, connectedComponent, weight, visited);
        }
    }
}
