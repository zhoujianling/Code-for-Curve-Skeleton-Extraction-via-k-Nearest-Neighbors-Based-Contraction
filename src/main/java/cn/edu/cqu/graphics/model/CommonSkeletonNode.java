package cn.edu.cqu.graphics.model;

import javax.vecmath.Point3d;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class CommonSkeletonNode implements Serializable{
    private long nodeIndex;
    private Point3d centroid;
    private List<Long> vertices = new ArrayList<>();

    /**
     * kryo no-arg ctr
     */
    @SuppressWarnings("unused")
    public CommonSkeletonNode() {}

    public CommonSkeletonNode(Long index, Point3d p) {
        this.nodeIndex = index;
        this.centroid = new Point3d(p);
    }

    public long getNodeIndex() {
        return nodeIndex;
    }

    public Point3d getCentroid() {
        return centroid;
    }

    public List<Long> getVertices() {
        return vertices;
    }
}
