package cn.edu.cqu.graphics.model;

import javax.media.j3d.*;
import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * 代表空间分割的格子
 *
 * @author liuji
 */
public class OctreeVertex implements Vertex, Serializable{

    private Long index;

    /**
     * 格子包含的点云
     */
    private List<Point3d> points = new ArrayList<>();
    /**
     * 格子中心点
     */
    private Point3d center = new Point3d();
    private Vector3d velocity = new Vector3d(0, 0, 0);
    private Vector3d acceleration = new Vector3d(0, 0, 0);
    private double mass = 1;

    /**kryo no-arg ctr**/
    @SuppressWarnings("unused")
    public OctreeVertex(){}

    public OctreeVertex(long index, HashMap<Long, Vertex> map) {
        this.index = index;
        map.put(this.index, this);
    }


    public Shape3D pointsShape(float size, float r, float g, float b) {
        Shape3D shape = new Shape3D();
        if (points.size() < 1) return shape;
//        shape.setCapability(Shape3D.ALLOW_APPEARANCE_WRITE);
        shape.setCapability(Shape3D.ALLOW_APPEARANCE_READ);
//        shape.setName("points");
        PointArray pa = new PointArray(points.size(), PointArray.COORDINATES);
        pa.setCoordinates(0, points.toArray(new Point3d[points.size()]));
        shape.setGeometry(pa);
        Appearance ap = new Appearance();


        ColoringAttributes ca = new ColoringAttributes();

        ap.setCapability(Appearance.ALLOW_COLORING_ATTRIBUTES_READ);
        ca.setCapability(ColoringAttributes.ALLOW_COLOR_WRITE);

        ca.setColor(r, g, b);

//        ca.setColor(0.5f, 0.5f, 0.5f);
//        ca.setColor(0.7f, 0.7f, 0.7f);
//        ca.setColor(1.0f, 1.0f, 1.0f); //white color


        ap.setColoringAttributes(ca);
        ap.setMaterial(null);
        ap.setPolygonAttributes(new PolygonAttributes(PolygonAttributes.POLYGON_POINT, PolygonAttributes.CULL_BACK, 0));
        ap.setPointAttributes(new PointAttributes(size, false));
//        ap.setPointAttributes(new PointAttributes(1, false));
        shape.setAppearance(ap);

        return shape;
    }

    public Long getIndex() {
        return this.index;
    }

    public List<Point3d> getPoints() {return this.points;}

    @Override
    public Vector3d getCurrentVelocity() {
        return velocity;
    }

    @Override
    public void setCurrentVelocity(Vector3d v) {
        this.velocity = v;
    }

    @Override
    public Vector3d getAcceleration() {
        return this.acceleration;
    }

    @Override
    public void setAcceleration(Vector3d v) {
        this.acceleration = v;
    }

    public Point3d getPosition() {
        return center;
    }

    public void setPosition(Point3d p) {
        this.center = p;
    }

    @Override
    public double getMass() {
        return mass;
    }

    @Override
    public void setMass(double m) {
        this.mass = m;
    }

    public double distance(Vertex vertex) {
        double result = 1.0;
        double dis = this.center.distance(vertex.getPosition());
        result *= dis;

        return result;
    }


}
