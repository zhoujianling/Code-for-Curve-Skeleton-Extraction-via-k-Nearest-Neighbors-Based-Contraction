package cn.edu.cqu.graphics.pipeline;

import cn.edu.cqu.graphics.model.PointCloud;
import cn.edu.cqu.graphics.pipes.MovingLeastSquare;
import cn.edu.cqu.graphics.pipes.collapse.BuildSkeletonConnections;
import cn.edu.cqu.graphics.pipes.collapse.IterativeContract;
import cn.edu.cqu.graphics.pipes.collapse.PointsRedistribution;
import cn.edu.cqu.graphics.pipes.sample.WLOPResample;
import cn.edu.cqu.graphics.protocol.AlgorithmInput;
import cn.edu.cqu.graphics.protocol.InputData;
import cn.edu.cqu.graphics.protocol.Pipeline;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static cn.edu.cqu.graphics.Constants.TYPE_POINT_CLOUD;

@Component
public class IterativeUpdatePipeline extends Pipeline {

    @AlgorithmInput(type = TYPE_POINT_CLOUD, name = "Point Cloud")
    PointCloud pointCloud;

    @Autowired
    IterativeContract iterativeContract;


    @Autowired
//    GridDownSample downsample1;
            WLOPResample downsample1;
//    OctreeDownSample downsample1;
//    NewOctreeDownsample downsample1;
//    NoDownSample downsample1;

    @Autowired
    MovingLeastSquare mls;

    @Autowired
    PointsRedistribution pointRedis;

    @Autowired
    BuildSkeletonConnections bsc;

    @Override
    protected void makeSteps() {
        steps.add(downsample1);
        steps.add(iterativeContract);
//        steps.add(pointRedis);
        steps.add(bsc);
    }

    @Override
    public InputData getPrimaryInput() {
        return pointCloud;
    }

    @Override
    public String name() {
        return "KNN-Contraction";
    }
}
