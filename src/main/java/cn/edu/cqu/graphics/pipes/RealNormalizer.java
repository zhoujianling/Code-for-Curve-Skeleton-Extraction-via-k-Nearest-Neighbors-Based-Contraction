package cn.edu.cqu.graphics.pipes;

import cn.edu.cqu.graphics.Constants;
import cn.edu.cqu.graphics.model.PointCloud;
import cn.edu.cqu.graphics.platform.CanvasObject;
import cn.edu.cqu.graphics.protocol.*;
import cn.jimmiez.pcu.common.graphics.BoundingBox;
import cn.jimmiez.pcu.util.PcuCommonUtil;
import org.springframework.stereotype.Component;

import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;
import java.io.FileNotFoundException;

@Component
public class RealNormalizer extends CachedPipe {

    @PipeInput
    @FromPreviousOutput(name = "输入点云")
    protected PointCloud data;

    @Param(comment = "输入归一化", key = "enableNormalize")
    private Boolean enableNormalize = false;

    @Override
    public String getName() {
        return "点云归一化";
    }

    @Override
    public void apply() throws FileNotFoundException {
        if (enableNormalize) {
            BoundingBox box = BoundingBox.of(data.getPoints());
            Vector3d bboxCenter = new Vector3d(- box.getCenter().x, - box.getCenter().y, - box.getCenter().z);
            for (Point3d p : data.getPoints()) p.add(bboxCenter);
            double maxLength = 2 * PcuCommonUtil.max(box.getxExtent(), box.getyExtent(), box.getzExtent());
            maxLength = Math.max(1E-7, maxLength);
            double ratio = 1.0 / maxLength;
            for (Point3d p : data.getPoints()) p.scale(ratio);

            CanvasObject object = new CanvasObject("归一化点云", Constants.TYPE_SCATTERED_POINTS, data.getPoints(), pipeline.getConfig());
            CanvasAttr attr = attr(new int[] {125, 125, 125}, new int[] {0, 0, 0}, 2);
            object.setAttr(attr);
            object.setVisible(false);

            pipeline.getModel().addCanvasObject(object);
        }
    }
}
