package cn.edu.cqu.graphics.model;

import javax.media.j3d.Shape3D;
import javax.vecmath.Point3d;
import java.util.List;

/**
 * 可能是被降采样后的点，也可能是原始点
 */
public interface Vertex extends Movable{

    Shape3D pointsShape(float size, float r, float g, float b);

    Long getIndex();

    List<Point3d> getPoints();

    Point3d getPosition();
    void setPosition(Point3d p);

    double distance(Vertex vertex);

}
