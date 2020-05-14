package cn.edu.cqu.graphics.pipeline;

import cn.edu.cqu.graphics.Constants;
import cn.edu.cqu.graphics.model.PointCloud;
import cn.edu.cqu.graphics.pipes.Interpolater;
import cn.edu.cqu.graphics.pipes.lvp.BuildGraph;
import cn.edu.cqu.graphics.pipes.lvp.InitialSkeleton;
import cn.edu.cqu.graphics.pipes.lvp.Refinement;
import cn.edu.cqu.graphics.pipes.sample.OctreeDownSample;
import cn.edu.cqu.graphics.pipes.tree.SurfaceCompletion;
import cn.edu.cqu.graphics.pipes.tree.TwigSynthesis;
import cn.edu.cqu.graphics.protocol.AlgorithmInput;
import cn.edu.cqu.graphics.protocol.InputData;
import cn.edu.cqu.graphics.protocol.Pipeline;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 一个骨架提取的 pipeline，不会局限在提取树木的骨架上
 */
@Component
public class LvpPipeline extends Pipeline {

    @AlgorithmInput(name = "点云", type = Constants.TYPE_POINT_CLOUD)
    private PointCloud pointCloud;

    @Autowired
    private OctreeDownSample step1;

    @Autowired
    private BuildGraph step2;

    @Autowired
    private InitialSkeleton step3;

    @Autowired
    private Refinement step4;

    @Autowired
    private TwigSynthesis step5;

    @Autowired
    private Interpolater step6;

    @Autowired
    private SurfaceCompletion step7;

    public LvpPipeline() {
    }

    protected void makeSteps() {
        steps.clear();
        steps.add(step1);
        steps.add(step2);
        steps.add(step3);
        steps.add(step4);
        steps.add(step5);
        steps.add(step6);
        steps.add(step7);
    }


    @Override
    public InputData getPrimaryInput() {
        return pointCloud;
    }

    @Override
    public String name() {
        return "LVP";
    }

}
