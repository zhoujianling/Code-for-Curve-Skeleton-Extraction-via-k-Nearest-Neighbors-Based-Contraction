package cn.edu.cqu.graphics.model;

import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;

public interface Movable {
    Vector3d getCurrentVelocity();
    void setCurrentVelocity(Vector3d v);
    Vector3d getAcceleration();
    void setAcceleration(Vector3d v);
    /**getBaryCenter**/
    Point3d getPosition();
    void setPosition(Point3d p);
    double getMass();
    void setMass(double m);

}
