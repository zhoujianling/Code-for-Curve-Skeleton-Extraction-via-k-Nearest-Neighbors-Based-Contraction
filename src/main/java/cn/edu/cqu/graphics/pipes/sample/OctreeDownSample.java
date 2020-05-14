package cn.edu.cqu.graphics.pipes.sample;

import cn.edu.cqu.graphics.Constants;
import cn.edu.cqu.graphics.math.Optimizer;
import cn.edu.cqu.graphics.math.energy.L1Median;
import cn.edu.cqu.graphics.model.Cube;
import cn.edu.cqu.graphics.model.OctreeVertex;
import cn.edu.cqu.graphics.model.PointCloud;
import cn.edu.cqu.graphics.model.Vertex;
import cn.edu.cqu.graphics.protocol.CachedPipe;
import cn.edu.cqu.graphics.protocol.FromPreviousOutput;
import cn.edu.cqu.graphics.protocol.PipeInput;
import cn.edu.cqu.graphics.protocol.PipeOutput;
import cn.jimmiez.pcu.common.graphics.Normalizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.media.j3d.*;
import javax.vecmath.Matrix4d;
import javax.vecmath.Point3d;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

/**
 * 这个过程花费约20ms，无性能瓶颈
 * 有一个很奇怪的事，就是
 * 划分的level为1的时候，按理来说,递归层次浅，应该时间消耗小，但是level越低时间消耗越大
 * 原因是
 * subdivide()很快
 * 时间主要花在了输出到txt里了，但是也不是IO-bounding的，在计算median的时候卡在了L1MedianPoint3d.getPoint()上了
 * 如果一个cube内的点足够多的话，最小化目标函数这个过程的时间开销比subdivide()里的深层递归大多了
 */
@Component
public class OctreeDownSample extends CachedPipe {
    private double length;//bounding-box(正方体)的长度
    private Point3d center;//
    private OctreeDownSample[] children;//一个立方体，可以分成8个子立方体，递归划分得到八叉树
    private List<Point3d> points = new ArrayList<Point3d>();// 读取的点云数据，最后全部分配到了叶子cube内了，父一级的cube内没有点
    private Long index;

    @Autowired
    Logger logger;

    @PipeInput
    @FromPreviousOutput(name = "输入点云")
    protected PointCloud data;

    @PipeOutput(type = Constants.TYPE_VERTEX_INDEX, name = "降采样点", visualize = true, visible = false)
    public HashMap<Long, Vertex> index2Vertex = new HashMap<>();//格子index到格子的映射

//    @Param(name = "八叉树深度", key = "octreeLevel")
    private Integer maxLevel = 6;

    static Double maxY = null;
    static Double minY = null;

    @Autowired
    private Optimizer optimizer;


    // 不要删,spring需要空构造器
    public OctreeDownSample() {
        // 不要删！！！！！！！！！
        // 不要删！！！！！！！！！
        // 不要删！！！！！！！！！
    }

    private double meanRadius(List<Point3d> list, Point3d centroid) {
        double sum = 0.0;
        for (Point3d p : list) sum += p.distance(centroid);
        return sum / list.size();
    }

    private OctreeDownSample(double x, double y, double z, double length, long index) {
        super();
        this.center = new Point3d(x, y, z);
        this.length = length;
        this.index = index;
    }

    public void addPoint(Point3d p) {
        if (children != null) {
            for (OctreeDownSample child : children) {
                if (child.inTree(p))
                    child.addPoint(p);
            }
        } else {
            points.add(p);
        }
    }

    private boolean inTree(Point3d p) {
        if (p.x <= center.x + length / 2.0 && p.x >= center.x - length / 2.0 && p.y <= center.y + length / 2.0 && p.y >= center.y - length / 2.0
                && p.z <= center.z + length / 2.0 && p.z >= center.z - length / 2.0)
            return true;
        else
            return false;
    }

    /**
     * 递归划分八叉树
     * @param
     */
    public void subdivide(int level, long upperLayerIndex) {
        if (level == 0)
            return;
        children = new OctreeDownSample[8];
        int n = 0;
        for (int i : new int[]{-1, 1})
            for (int j : new int[]{-1, 1})
                for (int k : new int[]{-1, 1}) {
                    long index = (long) (((i + 1) * 4 + (j + 1) * 2 + (k + 1)) / 2);
                    index <<= (maxLevel - level) * 3;
                    index |= upperLayerIndex;
                    OctreeDownSample child = new OctreeDownSample(center.x + i * length / 4.0, center.y + j * length / 4.0, center.z + k * length / 4.0, length / 2.0, index);
                    child.subdivide(level - 1, index);
                    children[n] = child;
                    n++;
                }
    }


    public String toString() {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        if (children == null) {
            if (points.size() != 0) {
                Point3d median = optimizer.computeMedian3d(points, L1Median.class);
                pw.printf("%d ", index);
                pw.printf("%f %f %f ", median.x, median.y, median.z);
                pw.printf("%d ", points.size());
                for (int i = 0; i < points.size(); i++) {
                    Point3d p = points.get(i);
                    if (i == points.size() - 1)
                        pw.printf("%f %f %f\n", p.x, p.y, p.z);
                    else
                        pw.printf("%f %f %f ", p.x, p.y, p.z);
                }
            }
        } else {
            for (OctreeDownSample child : children) {
                String s = child.toString();
                pw.print(s);
            }
        }
        return sw.toString();
    }

    public BranchGroup buildTree(boolean showPoint) {
        BranchGroup bg = new BranchGroup();
        if (children == null) { // 最底层的cell
            if (points.size() != 0) {
                double s = length * 0.5;
                Matrix4d m = new Matrix4d(new double[] {
                        s, 0, 0, center.x,
                        0, s, 0, center.y,
                        0, 0, s, center.z,
                        0, 0, 0, 1
                });
                TransformGroup tg = new TransformGroup(new Transform3D(m));
                tg.addChild(createCube());//添加立方体线框

                bg.addChild(tg);
                bg.addChild(createPointsShape());
            }
        } else {
            for (OctreeDownSample child : children) //高层cell递归向下
                bg.addChild(child.buildTree(showPoint));
        }
        return bg;
    }

    public void computeBoundingBox() {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE, maxZ = Double.MIN_VALUE;
        for (Point3d p : data.getPoints()) {
            minX = Math.min(minX, p.x);
            minY = Math.min(minY, p.y);
            minZ = Math.min(minZ, p.z);
            maxX = Math.max(maxX, p.x);
            maxY = Math.max(maxY, p.y);
            maxZ = Math.max(maxZ, p.z);
        }
        this.length = Math.max((maxX - minX), Math.max(maxY - minY, maxZ - minZ));
        this.center = new Point3d((maxX + minX) / 2,
                (maxY + minY) / 2, (maxZ + minZ) / 2);
    }

    private Shape3D createPointsShape() {
        Shape3D shape = new Shape3D();
        PointArray pa = new PointArray(points.size(), PointArray.COORDINATES);
        pa.setCoordinates(0, points.toArray(new Point3d[points.size()]));
        shape.setGeometry(pa);
        Appearance ap = new Appearance();
        ColoringAttributes ca = new ColoringAttributes();
        ca.setColor(0.5f, 0.5f, 0.5f);
        ap.setColoringAttributes(ca);
        ap.setMaterial(null);
        ap.setPolygonAttributes(new PolygonAttributes(PolygonAttributes.POLYGON_POINT, PolygonAttributes.CULL_BACK, 0));
        ap.setPointAttributes(new PointAttributes(1, false));
        shape.setAppearance(ap);
        return shape;
    }

    private Cube createCube() {

        Appearance ap = new Appearance();
        ColoringAttributes ca = new ColoringAttributes();
        ca.setColor(0.0f, 0.0f, 0.0f);
        ap.setColoringAttributes(ca);
        ap.setMaterial(null);
        ap.setPolygonAttributes(new PolygonAttributes(PolygonAttributes.POLYGON_LINE, PolygonAttributes.CULL_BACK, 0));
        ap.setLineAttributes(new LineAttributes(1, LineAttributes.PATTERN_SOLID, false));
        // RenderingAttributes ra = new RenderingAttributes();
        // ra.setIgnoreVertexColors(true);
        // ap.setRenderingAttributes(ra);
        Cube cube = new Cube();
        cube.setAppearance(ap);
        return cube;

    }

    private void buildIndex2Vertex(OctreeDownSample octreeDownSample) {
        if (octreeDownSample.children == null) {// 已经是最低一层的cell
            if (octreeDownSample.points.size() > 0) {
                Vertex vertex = new OctreeVertex(octreeDownSample.getIndex(), index2Vertex);
                vertex.getPoints().addAll(octreeDownSample.points);
                vertex.setPosition(optimizer.computeMedian3d(octreeDownSample.points, L1Median.class));
                index2Vertex.put(octreeDownSample.getIndex(), vertex);
                if (OctreeDownSample.minY == null || OctreeDownSample.minY > vertex.getPosition().y) {
                    OctreeDownSample.minY = vertex.getPosition().y;
                }
                if (OctreeDownSample.maxY == null || OctreeDownSample.maxY < vertex.getPosition().y) {
                    OctreeDownSample.maxY = vertex.getPosition().y;
                }
            }
        } else {
            for (OctreeDownSample child : octreeDownSample.children) {
                buildIndex2Vertex(child);
            }
        }
    }

    public Point3d getCenter() {
        return center;
    }

    public double getLength() {
        return length;
    }

    public Long getIndex() {
        return index;
    }

    public HashMap<Long, Vertex> getIndex2Vertex() {
        return index2Vertex;
    }

    @Override
    public String getName() {
        return "构造八叉树";
    }

    @Override
    public void apply() {
        points.clear();
        index2Vertex.clear();
        // 归一化
        Normalizer normalizer = new Normalizer();
//        normalizer.normalize(data.getPoints());
        //
        computeBoundingBox();
        this.maxLevel = (int) (Math.ceil((Math.log((data.getPoints().size() + 1) / 64) / Math.log(8))) + 2);
        this.maxLevel = 6;
        logger.info("八叉树划分深度：" + this.maxLevel);

        subdivide(maxLevel, 0L);//子划分过程
        for (Point3d p : data.getPoints()) {
            addPoint(p);
        }

        buildIndex2Vertex(this);
    }
}
