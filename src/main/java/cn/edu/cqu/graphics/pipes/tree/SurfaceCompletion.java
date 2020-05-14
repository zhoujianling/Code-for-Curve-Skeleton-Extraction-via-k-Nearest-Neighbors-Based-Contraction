package cn.edu.cqu.graphics.pipes.tree;

import cn.edu.cqu.graphics.Constants;
import cn.edu.cqu.graphics.config.UiTreeConfig;
import cn.edu.cqu.graphics.model.tree.TreeSkeletonNode;
import cn.edu.cqu.graphics.model.tree.TreeSkeletons;
import cn.edu.cqu.graphics.model.tree.Triangle;
import cn.edu.cqu.graphics.pipes.Interpolater;
import cn.edu.cqu.graphics.protocol.*;
import cn.edu.cqu.graphics.util.MathUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.vecmath.Matrix4d;
import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;
import javax.vecmath.Vector4d;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cn.edu.cqu.graphics.pipes.Interpolater.T;


@Component
public class SurfaceCompletion extends CachedPipe {
//    InitialSkeleton extraction;
    public static final int CIRCLE_SEGMENTS = 12;
//    private static final float T = 0.9f; // Cardinal拟合松紧系数

    @PipeInput
    @FromPreviousOutput(name = "树骨架")
    private TreeSkeletons index2Cluster;
//    private transient HashMap<Long, TreeSkeletonNode> index2Cluster;

    @Autowired
    private transient UiTreeConfig config;

    @PipeOutput(type = Constants.TYPE_TRIANGLES, name = "局部三角形", visualize = true)
    @EnableDiskCache
    private List<Triangle> triangles = new ArrayList<>();

    private List<Long> branchEnd = new ArrayList<>();
    private Map<Long, Integer> maxGD = new HashMap<>();
    private static final Double THETA = 3.0;

    public SurfaceCompletion()  {
    }

    private void collectBranchEnds(TreeSkeletonNode root) {
        if (root == null) {return;}
        if (root.getChildren().size() < 1) { branchEnd.add(root.getIndex());}
        for (Long childIndex : root.getChildren()) {
            collectBranchEnds(index2Cluster.getNode(childIndex));
        }
    }

    private void genMaxGD() {
        for (Long endIndex : branchEnd) {
            TreeSkeletonNode end = index2Cluster.getNode(endIndex);
            TreeSkeletonNode curr = end;
            while (!curr.getParent().equals(curr.getIndex())) {
                int currMaxGD = end.getGeodesicDistance();
                if (maxGD.get(curr.getIndex()) == null || maxGD.get(curr.getIndex()) < currMaxGD) {
                    maxGD.put(curr.getIndex(), currMaxGD);
                }
                curr = index2Cluster.getNode(curr.getParent());
            }
        }
    }

    private void genGeodesicDistance(TreeSkeletonNode root) {
        if (root == null) {return;}
        genGeodesicDistanceHelp(root, 0);
    }

    private void genGeodesicDistanceHelp(TreeSkeletonNode current, int dis) {
        if (current == null) {return;}
        current.setGeodesicDistance(dis);
        for (Long childIndex : current.getChildren()) {
            genGeodesicDistanceHelp(index2Cluster.getNode(childIndex), dis + 1);
        }
    }

    /**
     * 半径生成
     * 末端是0，根部最粗
     * 分叉处使用达芬奇公式，注意不是平方和
     * 根据测地距离半径逐渐衰减
     * @param root lalala
     */
    private void generateRadii(TreeSkeletonNode root) {
        generateRadiusHelp2(root, config.getRootRadius());
    }

    private double calculateRadius(int rootGD, TreeSkeletonNode curr, double rootRadii) {
        Integer mg = maxGD.get(curr.getIndex());
        if (mg == null) return rootRadii;
        return Math.pow(
                (Math.pow(rootRadii, THETA) *
                        (1 - (curr.getGeodesicDistance() - rootGD)* 1.0 / (mg - rootGD))),
                1.0 / THETA
        );
    }

    private void genRadiusHelp(double rootRadii) {
        for (Long endIndex : branchEnd) {
            TreeSkeletonNode end = index2Cluster.getNode(endIndex);
            TreeSkeletonNode curr = end;
            while (!curr.getParent().equals(curr.getIndex())) {
                double radii = calculateRadius(0, end, rootRadii);
                if (radii > curr.getRadius()) {
                    curr.setRadius(radii);
                }
                curr = index2Cluster.getNode(curr.getParent());
            }
        }
    }

    private void generateRadiusHelp2(TreeSkeletonNode root, double radius) {
        if (root == null) {return;}
        TreeSkeletonNode realRootRef = root;
        while (true) {
            root.setRadius(calculateRadius(realRootRef.getGeodesicDistance(), root, radius));
            if (root.getChildren().size() < 1) {
                return;
            } else if (root.getChildren().size() == 1) {
                root = index2Cluster.getNode(root.getChildren().get(0));
            } else {break;}
        }
        // 注意这个时候的 realRootRef 的 parent 是一个分叉点（或者就是 trunkRoot本身）
        // 为了分叉时不那么违和，把最初4 个圈再适当扩大一点, 可以用法向量来修正
        smoothBranching(realRootRef, 7);
        // ^^^^^^^^^^^^^^^
        List<Double> childRadius = allocateRadius(root, calculateRadius(realRootRef.getGeodesicDistance(), root, radius));
        if (childRadius.size() != root.getChildren().size()) {
            throw new IllegalStateException("?????");
        }
        for (int i = 0; i < root.getChildren().size(); i++) {
            Long childIndex = root.getChildren().get(i);
            TreeSkeletonNode child = index2Cluster.getNode(childIndex);
            generateRadiusHelp2(child, childRadius.get(i));
//            genRadiusHelp(childRadius.getNode(i));
        }
    }

    /**
     * 假设 Parent 为 S1， firstBranchNode 为 S2
     * parent 处的半径为 r1, firstBranchNode 的半径为 r2， 切线向量分别为 n1 n2
     * r2 should be:
     * (r1 / cos < n1, n2 >) - |S1S2| * (sin < s1s2, n1> / cos< n1, n2>)
     * @param firstBranchNode 分叉后遇到的第一个骨架点
     * @param smoothNum 要平滑的圈圈的数量
     */
    private void smoothBranching(TreeSkeletonNode firstBranchNode, int smoothNum) {
        if (smoothNum >= Interpolater.interpolateNumber) {
            throw new IllegalArgumentException("smoothNum to large");
        }
        TreeSkeletonNode parent = index2Cluster.getNode(firstBranchNode.getParent());
        if (parent == firstBranchNode) {
            System.out.println("根节点不需要扩大半径。");
            return;
        }
        TreeSkeletonNode curr = firstBranchNode;
        double ra = smoothBranchingHelp(parent, curr);
        double originalRadius = firstBranchNode.getRadius();
        for (int i = 0; i < smoothNum; i++) {
            parent = curr;
            curr.setRadius(ra - (i * 1.0 / smoothNum) * (ra - originalRadius));
            curr = index2Cluster.getNode(curr.getChildren().get(0));
        }
    }

    /**
     * (r1 / cos < n1, n2 >) - |S1S2| * (sin < s1s2, n1> / cos< n1, n2>)
     */
    private double smoothBranchingHelp(TreeSkeletonNode s1, TreeSkeletonNode s2) {
        if (s1 == s2) {return s2.getRadius();}
        Point3d c1 = s1.getCentroid();
        Point3d c2 = s2.getCentroid();
        Vector3d s12 = new Vector3d(c2.x - c1.x, c2.y - c1.y, c2.z - c1.z);
//        Vector3d n1 = normalV2(s1, index2Cluster.getNode(s1.getChildren().getNode(0)));
//        Vector3d n2 = normalV2(s2, index2Cluster.getNode(s2.getChildren().getNode(0)));
        Vector3d n1 = normalV2(s1, s2);
        Vector3d n2 = normalV3(s1, s2);
        double r1 = s1.getRadius();
        double r2 = s2.getRadius();
        double enlargedRadii = ((r1 / MathUtils.cos(n1, n2)) -
                (MathUtils.normd(s12) *
                        (MathUtils.sin(s12, n1) / MathUtils.cos(n1, n2)))
                );
//        s2.setH(enlargedRadii);
        return enlargedRadii;
    }

    /**
     * 给一层孩子套圈圈
     * 递归下去
     * @param root 当前骨架点
     */
    private void generateCircles(TreeSkeletonNode root) {
        if (root == null) {return;}

        while (true) {
            TreeSkeletonNode parent = index2Cluster.getNode(root.getParent());
            Vector3d normal = normalV3(parent, root); // 获取child骨架线切线向量
            circle(root.getCentroid(), root.getRadius(), normal, root); // 求出child圈上的点

            //vvvvvvvvvvvvvvv 两个圈之间的三角网格
            genSingleLayerTriangle(parent, root, triangles);
            //^^^^^^^^^^^^^^

            if (root.getChildren().size() < 1) {
                return;
            } else if (root.getChildren().size() == 1) {
                root = index2Cluster.getNode(root.getChildren().get(0));
            } else {break;}
        }

        for (Long childIndex : root.getChildren()) {
            generateCircles(index2Cluster.getNode(childIndex)); // 递归求子树圈
        }
    }

    /**
     * 为每个骨架点生成圈，圈存储在 TreeSkeletonNode 中。
     * @param center 貌似是圈的中心
     * @param radius 圈的半径
     * @param normal 当前骨架线切线向量，也就是圈平面的法向量
     * @param skeletonPoint 骨架点
     */
    private void circle(Point3d center, double radius, Vector3d normal, TreeSkeletonNode skeletonPoint) {
        if (radius < 0) return;
        normal.normalize();
        double yzMod = Math.sqrt(normal.y * normal.y + normal.z * normal.z);
        List<Point3d> points = new ArrayList<>();
        for (int i = 0; i < CIRCLE_SEGMENTS; i++) {
            double theta1 = Math.PI * 2 / CIRCLE_SEGMENTS * i;
            double theta2 = Math.PI * 2 / CIRCLE_SEGMENTS * (i + 1);
            // 下面一堆 JB 矩阵可以略过，我推了一天没推出来，直接网上抄公式的 // zjl
            Point3d p1 = new Point3d(
                    radius * Math.cos(theta1),
                    radius * Math.sin(theta1),
                    0f
            );
            Point3d p2 = new Point3d(
                    radius * Math.cos(theta2),
                    radius * Math.sin(theta2),
                    0f
            );

            Matrix4d matY = new Matrix4d(new double[] {
                    1.0, 0, 0, 0,
                    0, normal.z / yzMod, - (normal.y / yzMod), 0,
                    0, normal.y / yzMod, normal.z / yzMod, 0,
                    0, 0, 0, 1.0
            });
            Matrix4d matX = new Matrix4d(new double[] {
                    yzMod, 0, -normal.x, 0,
                    0, 1.0, 0, 0,
                    normal.x,  0, yzMod, 0,
                    0, 0, 0, 1.0
            });
            Matrix4d p1PlaceHolder = new Matrix4d(new double[] {
                    0, 0, 0, 0,
                    0, 0, 0, 0,
                    0, 0, 0, 0,
                    0, 0, 0, 0
            });
            Matrix4d p2PlaceHolder = new Matrix4d(p1PlaceHolder);
            p1PlaceHolder.setColumn(0, new double[] {p1.x, p1.y, p1.z, 1});
            p2PlaceHolder.setColumn(0, new double[] {p2.x, p2.y, p2.z, 1});

            matX.mul(matY);
            matX.invert();

            Matrix4d matXCopy4p1 = new Matrix4d(matX);
            Matrix4d matXCopy4p2 = new Matrix4d(matX);
            matXCopy4p1.mul(p1PlaceHolder);
            matXCopy4p2.mul(p2PlaceHolder);

            Vector4d firstColumn4P1 = new Vector4d();
            Vector4d firstColumn4P2 = new Vector4d();
            matXCopy4p1.getColumn(0, firstColumn4P1);
            matXCopy4p2.getColumn(0, firstColumn4P2);
            p1.x = firstColumn4P1.x;
            p1.y = firstColumn4P1.y;
            p1.z = firstColumn4P1.z;
            p2.x = firstColumn4P2.x;
            p2.y = firstColumn4P2.y;
            p2.z = firstColumn4P2.z;

            p1.add(center);
            p2.add(center);
            //#########3三角形
            //#################

            points.add(p1);
            points.add(p2);

            if (skeletonPoint != null) {
                skeletonPoint.getCirclePoints().add(p1);
            } else {
                System.out.println("nulllllll");
            }
        }
//        return edges(points); //画圈圈
//        return new Shape3D(); //不画圈圈
    }

    private void genSingleLayerTriangle(TreeSkeletonNode parent, TreeSkeletonNode child, List<Triangle> triangles) {
        if (parent == null || child == null) return;
        if (parent == child) return;
        if (parent.getCirclePoints().size() != child.getCirclePoints().size()) {
            System.out.println("圈上的点数不一致。第一次应该出现在根部。");
            return;
        }
        if (child.getCirclePoints().size() < 1) return;
        for (int i = 0; i < child.getCirclePoints().size(); i++) {
            Point3d a = child.getCirclePoints().get(i);
            Point3d b = parent.getCirclePoints().get(i);
            Point3d c = parent.getCirclePoints().get((i + 1) % parent.getCirclePoints().size());
            Point3d d = child.getCirclePoints().get((i + 1) % child.getCirclePoints().size());
            triangles.add(new Triangle(a, b, c));
            triangles.add(new Triangle(a, c, d));

        }
//        Triangle tri = new Triangle(child.getCirclePoints().getNode(0), parent.getCirclePoints().getNode(0), parent.getCirclePoints().getNode(1));
//        triangles.add(tri);
    }

    private List<Double> allocateRadius(TreeSkeletonNode branchingSkeletonPoint, double parentRadius) {
        List<Double> radiusList = new ArrayList<>();
        if (branchingSkeletonPoint.getChildren().size() == 1) {
            radiusList.add(parentRadius);
            return radiusList;
        }
        for (Long childIndex : branchingSkeletonPoint.getChildren()) {
            TreeSkeletonNode child = index2Cluster.getNode(childIndex);
            double childRadius = calculateSingleChildRadius(branchingSkeletonPoint, child, parentRadius);
            radiusList.add(childRadius);
        }
        return radiusList;
    }

    /**
     * 根据孩子子树上结点的数量和达芬奇公式分配半径
     * @param parent
     * @param child
     * @param parentRadius
     * @return
     */
    private double calculateSingleChildRadius(TreeSkeletonNode parent, TreeSkeletonNode child, double parentRadius) {
        if (parent == null || child == null) return parentRadius;
        int totalNum = nodeNumOfSubtree(parent);
        int childNum = nodeNumOfSubtree(child);
        return Math.pow((Math.pow(parentRadius, THETA)) * childNum / totalNum, 1.0 / THETA);
    }

    /**
     * @param cluster 计算后代树枝 *
     * @return 数量
     */
    private int calculateDescendantBranchNum(TreeSkeletonNode cluster) {
        if (cluster.getChildren().size() == 0) return 0;
        if (cluster.getChildren().size() >  1) return cluster.getChildren().size();
        return calculateDescendantBranchNum(index2Cluster.getNode(cluster.getChildren().get(0)));
    }

    /**
     * 统计子树结点数量
     * @param root
     * @return
     */
    private int nodeNumOfSubtree(TreeSkeletonNode root) {
        if (root == null) return 0;
        int result = 1;
        for (Long childIndex : root.getChildren()) {
            result += nodeNumOfSubtree(index2Cluster.getNode(childIndex));
        }
        return result;
    }

    /**
     * 返回**v2**处的切线向量，
     * 默认使用公式：p'(v2)=0.5 * (1 - t) ( p(v3) - p(v1) )
     * 如果 v2 = v3 = treeRoot
     * 使用公式：p'(v3) = p'(v2) = 0.5 * (
     * @param v2 要求向量的位置
     * @param v3 v2的孩子
     * @return 切线向量，取决于松紧系数
     */
    private Vector3d normalV2(TreeSkeletonNode v2, TreeSkeletonNode v3) {
        TreeSkeletonNode v1 = index2Cluster.getNode(v2.getParent());
        Point3d p1 = v1.getCentroid();
        Point3d p3 = v3.getCentroid();
        Vector3d normal = new Vector3d(
                ( (1 - T) * (p3.x - p1.x) / 2.0f),
                ((1 - T) * (p3.y - p1.y) / 2.0f),
                ((1 - T) * (p3.z - p1.z) / 2.0f)
        );
        return normal;
    }

    /**
     * 返回**v3**处的切线向量，
     * 默认使用公式：p'(v3)=0.5 * (1 - t) ( p(lvp) - p(v2) )
     * 如果 v2 = v3 = treeRoot
     * 使用公式：p'(v3) = p'(v2) = 0.5 * (
     * @param v2 要求向量的位置
     * @param v3 v2的孩子
     * @return 切线向量，取决于松紧系数
     */
    private Vector3d normalV3(TreeSkeletonNode v2, TreeSkeletonNode v3) {
        TreeSkeletonNode lvp;
        TreeSkeletonNode v1 = index2Cluster.getNode(v2.getParent());
        if (v3.getChildren().size() < 1) {
            lvp = v3;
        } else {
            lvp = index2Cluster.getNode(v3.getChildren().get(0));
        }

//        Point3d p1 = cn.edu.cqu.v1.getCentroid();
        Point3d p2 = v2.getCentroid();
//        Point3d p3 = cn.edu.cqu.v3.getCentroid();
        Point3d p4 = lvp.getCentroid();
        Vector3d normal = new Vector3d(
                ( (1 - T) * (p4.x - p2.x) / 2.0f),
                ((1 - T) * (p4.y - p2.y) / 2.0f),
                ((1 - T) * (p4.z - p2.z) / 2.0f)
        );
        return normal;
    }

    public List<Triangle> getTriangles() {
        return triangles;
    }

    public void setTriangles(List<Triangle> triangles) {
        this.triangles = triangles;
    }

    @Override
    public String getName() {
        return "表面重建";
    }

    @Override
    public void apply() {
        TreeSkeletonNode root = index2Cluster.getTrunkRootClusterNode();
        collectBranchEnds(root); // 记录所有末端， genMaxGD 要用
        genGeodesicDistance(root); // 记录测地距离，genMaxGD 和 generateRadii 要用
        genMaxGD(); // 记录所有vertex可到达的最远末端的测地距离
        generateRadii(root); // 为每个骨架点计算半径
        generateCircles(root); // 为每个骨架点套圈圈
    }
}
