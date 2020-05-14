package cn.edu.cqu.graphics.cluster;

import cn.edu.cqu.graphics.model.tree.TreeSkeletonNode;

import java.util.HashMap;
import java.util.Vector;

/**
 * Created by zjl on 2017/6/23.
 *
 */
public interface BinClusterer {

    /**
     *
     * @param bins the level sets that are obtained via geodesic distance
     * @param graph a graph holding information of TreeSkeletonNode
     * @return
     */
    Vector<Vector<TreeSkeletonNode>> cluster(Vector<TreeSkeletonNode> bins, HashMap<Long, HashMap<Long, Double>> graph);
}
