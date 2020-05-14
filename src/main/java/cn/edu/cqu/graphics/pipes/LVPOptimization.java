package cn.edu.cqu.graphics.pipes;

import cn.edu.cqu.graphics.math.Optimizer;
import cn.edu.cqu.graphics.math.energy.L1MedianVariancePenalty;
import cn.edu.cqu.graphics.model.PointCloud;
import cn.edu.cqu.graphics.protocol.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.vecmath.Point3d;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

import static cn.edu.cqu.graphics.Constants.TYPE_LVP_DEMO;

@Component
public class LVPOptimization extends CachedPipe {

    @PipeInput
    @FromPreviousOutput(name = "输入点云")
    PointCloud pointCloud;

    // 点云， 第一个点是 lvp 点，先渲染点云，再渲染 lvp点就行了
    @PipeOutput(name = "lvp", type = TYPE_LVP_DEMO, visualize = true)
    List<Point3d> lvpDemo = new ArrayList<>();

    @Param(key = "k4lvp", comment = "lambda", minVal = 1, maxVal = 500000)
    Integer k = 30;

    @Autowired
    Optimizer optimizer;

    @Override
    public String getName() {
        return "lvp优化";
    }

    @Override
    public void apply() throws FileNotFoundException {
        lvpDemo = new ArrayList<>();
        L1MedianVariancePenalty.setK(k);
        Point3d lvp = optimizer.computeMedian3d(pointCloud.getPoints(), L1MedianVariancePenalty.class);
        lvpDemo.add(lvp);
        lvpDemo.addAll(pointCloud.getPoints());
    }

}
