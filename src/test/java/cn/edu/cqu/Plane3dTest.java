package cn.edu.cqu;

import cn.edu.cqu.graphics.model.common.Plane3d;
import org.junit.Test;

import javax.vecmath.Point3d;
import static org.junit.Assert.*;

public class Plane3dTest {


    @Test
    public void testDistance() {
        Plane3d plane1 = new Plane3d(1.0, 1.0, 1.0, - 1.0);
        Point3d point = new Point3d(1.0 , 0 , 0);
        double distance = plane1.distance(point);
        assertEquals(0, distance, 1E-6);

        point = new Point3d(0 , 1.0 , 0);
        distance = plane1.distance(point);
        assertEquals(0, distance, 1E-6);

        point = new Point3d(.0 , 0 , 0);
        distance = plane1.distance(point);
        assertEquals(Math.sqrt(3.0) / 3.0, distance, 1E-6);

        plane1 = new Plane3d(1.0, 0, 0, - 1.0);
        distance = plane1.distance(point);
        assertEquals(1.0, distance, 1E-6);
    }
}
