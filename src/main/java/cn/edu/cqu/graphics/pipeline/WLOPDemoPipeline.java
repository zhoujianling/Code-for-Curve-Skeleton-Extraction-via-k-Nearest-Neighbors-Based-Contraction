package cn.edu.cqu.graphics.pipeline;

import cn.edu.cqu.graphics.model.PointCloud;
import cn.edu.cqu.graphics.pipes.sample.WLOPResample;
import cn.edu.cqu.graphics.protocol.AlgorithmInput;
import cn.edu.cqu.graphics.protocol.InputData;
import cn.edu.cqu.graphics.protocol.Pipeline;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static cn.edu.cqu.graphics.Constants.TYPE_POINT_CLOUD;

@Component
public class WLOPDemoPipeline extends Pipeline {

    @AlgorithmInput(type = TYPE_POINT_CLOUD, name = "输入点云")
    PointCloud pointCloud;

    @Autowired
    WLOPResample resample;

    @Override
    protected void makeSteps() {
        steps.add(resample);
    }

    @Override
    public InputData getPrimaryInput() {
        return pointCloud;
    }

    @Override
    public String name() {
        return "WLOP";
    }
}
