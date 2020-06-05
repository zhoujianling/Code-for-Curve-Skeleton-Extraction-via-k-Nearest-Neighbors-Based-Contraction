package cn.edu.cqu.graphics.platform;

import cn.jimmiez.pcu.model.Skeleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.vecmath.Point3d;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@Component
public class DataExporter {

    @Autowired
    private Logger logger;

    public void exportPointCloudNPTS(List<Point3d> points, String fileName) throws IOException {
        FileWriter writer = new FileWriter((fileName));
        for (Point3d point : points) {
            writer.write(String.format("%.4f %.4f %.4f\n", point.x, point.y, point.z));
        }
        writer.flush();
        writer.close();
        logger.info("Succeed in writing " + points.size() + " points into file " + fileName);
    }

    public void exportSkeletonOBJ(Skeleton skeleton, String filePath) throws IOException {
        FileWriter writer = new FileWriter(filePath);
        writer.write("# Written by ModelProcessingPlatform\n");
        Map<Integer, Integer> i2i = new HashMap<>();

        int index4Obj = 1;
        for (int pointIndex : skeleton.vertices()) {
            Point3d point = skeleton.getVertex(pointIndex);
            writer.write(String.format("v %.4f %.4f %.4f\n", point.x, point.y, point.z));

            i2i.put(pointIndex, index4Obj);
            index4Obj += 1;
        }
        for (int pointIndex : skeleton.vertices()) {
            for (int adjVertIndex : skeleton.adjacentVertices(pointIndex)) {
                writer.write(String.format("l %d %d\n", i2i.get(pointIndex), i2i.get(adjVertIndex)));
            }
        }
        writer.flush();
        writer.close();
        logger.info("Succeed in writing " + skeleton.vertices().size() + " skeleton points into file " + filePath);
    }
}
