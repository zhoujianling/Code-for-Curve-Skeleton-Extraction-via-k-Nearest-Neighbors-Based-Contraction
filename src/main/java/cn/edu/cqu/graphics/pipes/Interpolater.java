package cn.edu.cqu.graphics.pipes;

import cn.edu.cqu.graphics.model.tree.TreeSkeletonNode;
import cn.edu.cqu.graphics.model.tree.TreeSkeletons;
import cn.edu.cqu.graphics.protocol.CachedPipe;
import cn.edu.cqu.graphics.protocol.FromPreviousOutput;
import cn.edu.cqu.graphics.protocol.PipeInput;
import org.springframework.stereotype.Component;

import javax.vecmath.Point3d;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;

@Component
public class Interpolater extends CachedPipe {
    public static  double T = 0.1;//cadinal拟合松紧系数 负数松
    public static  double interpolateNumber = 8;


    @PipeInput(willBeModified = true)
    @FromPreviousOutput(name = "树骨架线")
    private TreeSkeletons index2Cluster;

    /**
     * 骨架点插值
     * @param root
     */
    private void interpolate(TreeSkeletonNode root, TreeSkeletonNode rootParent) {
        for (int j = 0; j < root.getChildren().size(); j++) {
//        for (Long childIndex : root.getChildren()) {
            Long childIndex = root.getChildren().get(j);

            TreeSkeletonNode child = index2Cluster.getNode(childIndex);
            List<TreeSkeletonNode> fakeClusters = new ArrayList<>();

            TreeSkeletonNode grandChild = child;
            if (child.getChildren().size() > 0) {
                grandChild = index2Cluster.getNode(child.getChildren().get(0));
            }

            Point3d p1 = rootParent.getCentroid();
            Point3d p2 = root.getCentroid();
            Point3d p3 = child.getCentroid();
            Point3d p4 = grandChild.getCentroid();
            Function<Double, Point3d> cardinal = cardinalSplineFit(p1, p2, p3, p4);

            for (int i = 0; i < interpolateNumber; i++) {
                Point3d p = cardinal.apply((i + 1) * 1.0 / (interpolateNumber + 1));

                TreeSkeletonNode fakeCluster = index2Cluster.uniqueNode(-1, new HashSet<>());
                index2Cluster.putNode(fakeCluster.getIndex(), fakeCluster);
                fakeCluster.setCentroid(p);
                fakeClusters.add(fakeCluster);
            }
            insertChildren(root, child, fakeClusters, j);
            interpolate(child, root);
        }
    }

    private void insertChildren(TreeSkeletonNode parent, TreeSkeletonNode child, List<TreeSkeletonNode> newlyInserted, int rootChildIndex) {
        if (parent == null || child == null || newlyInserted == null) return;
        if (! child.getParent().equals(parent.getIndex())) return;
        for (int i = 1; i < newlyInserted.size(); i++) {
            TreeSkeletonNode prev = newlyInserted.get(i - 1);
            TreeSkeletonNode curr = newlyInserted.get(i);
            prev.getChildren().add(curr.getIndex());
            curr.setParent(prev.getIndex());
        }
        newlyInserted.get(0).setParent(parent.getIndex());
        newlyInserted.get(newlyInserted.size() - 1).getChildren().add(child.getIndex());
        child.setParent(newlyInserted.get(newlyInserted.size() - 1).getIndex());
        parent.getChildren().set(rootChildIndex, newlyInserted.get(0).getIndex());
    }


    private static Function<Double, Point3d> cardinalSplineFit(Point3d p1, Point3d p2, Point3d p3, Point3d p4) {
        return (Double u) -> {
            double s = (1 - T) / 2;
            double coef1 = (-s * Math.pow(u, 3) + 2 * s * Math.pow(u, 2) - s * u);
            double coef2 = ((2 - s) * Math.pow(u, 3) + (s - 3) * Math.pow(u, 2) + 1);
            double coef3 = ((s - 2) * Math.pow(u, 3) + (3 - 2 * s) * Math.pow(u, 2) + s * u);
            double coef4 = s * Math.pow(u, 3) - s * Math.pow(u, 2);
            float x = (float) (p1.x * coef1 + p2.x * coef2 + p3.x * coef3 + p4.x * coef4);
            float y = (float) (p1.y * coef1 + p2.y * coef2 + p3.y * coef3 + p4.y * coef4);
            float z = (float) (p1.z * coef1 + p2.z * coef2 + p3.z * coef3 + p4.z * coef4);
            return new Point3d(x, y, z);
        };
    }

    @Override
    public String getName() {
        return "骨架点插值";
    }

    @Override
    public void apply() {
        TreeSkeletonNode rootCLuster =index2Cluster.getTrunkRootClusterNode();
        TreeSkeletonNode second = index2Cluster.getNode(rootCLuster.getChildren().get(0));
        interpolate(second, rootCLuster);
    }
}
