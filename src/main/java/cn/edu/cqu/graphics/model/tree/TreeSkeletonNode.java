package cn.edu.cqu.graphics.model.tree;

import cn.edu.cqu.graphics.config.SpringContext;
import cn.edu.cqu.graphics.config.UiSkeletonConfig;
import cn.edu.cqu.graphics.math.Optimizer;
import cn.edu.cqu.graphics.math.energy.L1MedianVariancePenalty;
import cn.edu.cqu.graphics.model.Color;
import cn.edu.cqu.graphics.model.Vertex;
import com.google.gson.Gson;

import javax.media.j3d.*;
import javax.vecmath.Point3d;
import java.io.Serializable;
import java.util.*;

/**
 * Created by zjl on 2017/6/24.
 * 对应一个骨架点
 * 我需要将HashMap/<Long, Vector> path 转化成一棵树
 * 这棵树需要定义内部结点类
 */
public class TreeSkeletonNode implements Serializable{
    private static Long incrementIndex = 0L;
    private Long index;
    private Integer levelSetIndex = -1;
    private HashSet<Long> vertices;
    private Point3d centroid = null;
    private boolean visible = true;


    private List<Long> children = new ArrayList<>();//这里的index其实就是cluster的index，为什么这么绕呢，为啥不直接保存引用呢，其实就是为了方便序列化
    private Long parent = null;

    //以下用于树重建
    private List<Point3d> circlePoints = new ArrayList<>();
    //这个结点对应的半径
    private double radius;
    //测地距离
    private int geodesicDistance;

    /**kryo no-arg ctr**/
    public TreeSkeletonNode() {
    }

    public TreeSkeletonNode(int levelSetIndex, HashSet<Long> vertices) {
        this.levelSetIndex = levelSetIndex;
        this.index = incrementIndex;
        incrementIndex += 1;
        this.vertices = vertices;
        this.children = new Vector<>();


    }

    public TreeSkeletonNode(int levelSetIndex) {
        this.index = -1L;
        this.levelSetIndex = levelSetIndex;
        this.vertices = new HashSet<>();
        this.children = new Vector<>();
    }

    public void copy(TreeSkeletonNode c) {
        this.index = c.index;
        this.vertices.addAll(c.vertices);
        this.levelSetIndex = c.levelSetIndex;
        this.centroid = c.centroid;
        this.children = c.children;
        this.parent = c.parent;
        this.visible = c.visible;
        this.circlePoints = c.circlePoints;

    }

    public Long getIndex() {
        return this.index;
    }

    public HashSet<Long> getVertices() {
        return this.vertices;
    }

    public Integer getLevelSetIndex() {
        return this.levelSetIndex;
    }

    public Point3d getCentroid() {
        return centroid;
    }

    public void setCentroid(Point3d centroid) {
        this.centroid = centroid;
    }

    public Long getParent() {
        return parent;
    }

    public void setParent(Long parent) {
        this.parent = parent;
    }

    public List<Long> getChildren() {
        return children;
    }

    public void setChildren(List<Long> children) {
        this.children = children;
    }

    @Override
    public String toString() {
        Gson gson = new Gson();
        return gson.toJson(this);
    }


    public Shape3D getCentroidShape(HashMap<Long, Vertex> index2Vertex) {
        UiSkeletonConfig config = SpringContext.instance().getBean(UiSkeletonConfig.class);
        Point3d centroid = new Optimizer().computeMedian3d(vertices, index2Vertex, L1MedianVariancePenalty.class);
//        System.out.println(String.format("x: %.3f, y: %.3f, z: %.3f", centroid.x, centroid.y, centroid.z));
        Shape3D shape = new Shape3D();

        PointArray pa = new PointArray(1, PointArray.COORDINATES);
        pa.setCoordinates(0, new Point3d[] {centroid});
        shape.setGeometry(pa);
        Appearance ap = new Appearance();
        ColoringAttributes ca = new ColoringAttributes();

        Color color = config.getSkeletonNodeColor();
        ca.setColor(color.r / 255.0f, color.g / 255.0f, color.b / 255.0f);
//        ca.setColor(1.0f, 0.0f, 0.0f);
        ap.setColoringAttributes(ca);
        ap.setMaterial(null);
        ap.setPolygonAttributes(new PolygonAttributes(PolygonAttributes.POLYGON_POINT, PolygonAttributes.CULL_BACK, 0));
        ap.setPointAttributes(new PointAttributes(8, false));
        shape.setAppearance(ap);

        return shape;
    }

    @Override
    public int hashCode() {return this.index.intValue();}

    public void setVisible(boolean v) {this.visible = v;}

    public boolean isVisible() {return this.visible;}

    public List<Point3d> getCirclePoints() {
        return circlePoints;
    }

    public void setCirclePoints(List<Point3d> circlePoints) {
        this.circlePoints = circlePoints;
    }

    public double getRadius() {return radius;}

    public void setRadius(double r) {this.radius = r;}

    public int getGeodesicDistance() {return geodesicDistance;}

    public void setVertices(HashSet<Long> vertices) {
        this.vertices = vertices;
    }

    public void setLevelSetIndex(Integer levelSetIndex) {
        this.levelSetIndex = levelSetIndex;
    }

    public void setIndex(Long index) {
        this.index = index;
    }

    public void setGeodesicDistance(int g) {geodesicDistance = g;}
}
