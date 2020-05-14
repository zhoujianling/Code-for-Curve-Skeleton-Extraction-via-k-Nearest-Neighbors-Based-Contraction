package cn.edu.cqu.graphics.model.tree;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;

public class TreeSkeletons implements Serializable {

    /**kryo no-arg ctr**/
    public TreeSkeletons() {
    }

    private Long trunkRootIndex = -1L;

    private transient Random random = new Random(System.currentTimeMillis());

    private HashMap<Long, TreeSkeletonNode> index2Cluster = new HashMap<>();

    public TreeSkeletonNode uniqueNode(int levelSetIndex, HashSet<Long> vertices) {
        TreeSkeletonNode node = new TreeSkeletonNode();
        node.setLevelSetIndex(levelSetIndex);
        node.setVertices(vertices);

        Long uniqueIndex = Math.abs(random.nextLong());
        while (index2Cluster.containsKey(uniqueIndex)) uniqueIndex = Math.abs(random.nextLong());
        node.setIndex(uniqueIndex);
        return node;
    }

    public Collection<TreeSkeletonNode> nodes() {
        return index2Cluster.values();
    }

    public TreeSkeletonNode getNode(Long index) {
        return index2Cluster.get(index);
    }

    public void putNode(Long index, TreeSkeletonNode cluster) {
        index2Cluster.put(index, cluster);
    }

    public TreeSkeletonNode getTrunkRootClusterNode() {
        return getNode(trunkRootIndex);
    }

    public void setTrunkRootClusterNode(Long index) {
        this.trunkRootIndex = index;
    }

    public void setIndex2Cluster(HashMap<Long, TreeSkeletonNode> index2Cluster) {
        this.index2Cluster = index2Cluster;
    }

    public HashMap<Long, TreeSkeletonNode> getIndex2Cluster() {
        return index2Cluster;
    }
}
