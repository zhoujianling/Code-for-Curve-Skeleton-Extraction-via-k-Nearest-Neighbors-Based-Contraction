package cn.edu.cqu.graphics.pipeline;

import cn.edu.cqu.graphics.model.PointCloud;
import cn.edu.cqu.graphics.pipes.LVPOptimization;
import cn.edu.cqu.graphics.protocol.AlgorithmInput;
import cn.edu.cqu.graphics.protocol.InputData;
import cn.edu.cqu.graphics.protocol.Pipeline;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static cn.edu.cqu.graphics.Constants.TYPE_POINT_CLOUD;

@Component
public class TempPipeline extends Pipeline {

    @AlgorithmInput(type = TYPE_POINT_CLOUD, name = "点云")
    PointCloud pointCloud;

    @Autowired
    LVPOptimization lvp;

    @Override
    protected void makeSteps() {
        steps.add(lvp);
    }

    @Override
    public InputData getPrimaryInput() {
        return pointCloud;
    }

    @Override
    public String name() {
        return "lvp演示";
    }
}
