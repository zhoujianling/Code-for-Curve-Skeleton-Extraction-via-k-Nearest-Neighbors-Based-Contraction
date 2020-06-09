package cn.edu.cqu.graphics.util;

import cn.edu.cqu.graphics.model.TreeSkeleton;

import javax.vecmath.Point3d;

public class CommonUtil {

    /**
     * 拷贝一份骨架线
     * @param src 源骨架线
     * @param dst 要拷贝到的骨架线
     */
    public static void copySkeleton(TreeSkeleton src, TreeSkeleton dst) {
        dst.clear();
        for (int vi : src.vertices()) {
            dst.addVertex(vi, new Point3d(src.getVertex(vi)));
        }
        dst.setRootId(src.getRootId());

        for (int vi : src.vertices()) {
            for (int vj : src.adjacentVertices(vi)) {
                double edgeWeight = src.edgeWeight(vi, vj);
                dst.addEdge(vi, vj, edgeWeight);
            }
        }
    }
}
