package cn.edu.cqu.graphics.pipes.sample;

import cn.edu.cqu.graphics.Constants;
import cn.edu.cqu.graphics.alg.GridVoxelizer;
import cn.edu.cqu.graphics.math.Optimizer;
import cn.edu.cqu.graphics.math.energy.L1Median;
import cn.edu.cqu.graphics.model.PointCloud;
import cn.edu.cqu.graphics.protocol.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.vecmath.Point3d;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@Component
public class GridDownSample extends CachedPipe {

    @PipeInput
    @FromPreviousOutput(name = "输入点云")
    protected PointCloud data;

//    @Param(comment = "WLOP迭代次数", key = "wlopIterNum", minVal = 5, maxVal = 200)
//    Integer iterNum = 30;
//
//    @Param(comment = "采样点数量", key = "wlopsampleNum", minVal = 50, maxVal = 12000)
//    Integer sampleNum = 1500;
//
//    @Param(comment = "WLOP初始半径", key = "wlopradiusScale", minVal = 1, maxVal = 15)
//    Integer radiusScale = 1;

    @PipeOutput(type = Constants.TYPE_DOWNSAMPLE_POINTS, name = "降采样点", visualize = true, visible = true)
    @CanvasAttr(primaryColor = {125, 125, 125}, primarySize = 4)
    public List<Point3d> downsampled = new ArrayList<>();//格子index到格子的映射

    @Autowired
    Optimizer optimizer;

    @Autowired
    Logger logger;

    // -1 表示自动
    @Param(comment = "采样倍率", key = "sampleRatio", minVal = 0, maxVal = 11)
    Integer ratio = 3;

    @Override
    public String getName() {
        return "体素化降采样";
    }

    @Override
    public void apply() throws FileNotFoundException {
        downsampled.clear();
        List<GridVoxelizer.GridCell> cells = new ArrayList<>();
        double sampleRatio = 7.0;
        if (ratio <= 0) {
            GridVoxelizer voxelizer = new GridVoxelizer(sampleRatio);
            int num = 1;
            while (num < 8000) {
                cells = voxelizer.voxelize(data.getPoints());
                num = cells.size();
                sampleRatio -= 0.5;
                voxelizer.setCellRatio(sampleRatio);
            }
            ratio = (int)sampleRatio;
        } else {
            GridVoxelizer voxelizer = new GridVoxelizer(ratio);
            cells = voxelizer.voxelize(data.getPoints());
        }
        for (GridVoxelizer.GridCell cell : cells) {
            downsampled.add(meanPoint(cell.getIndices()));
        }
        logger.info("Sample ratio: " + ratio + " Samples num: " + downsampled.size());
//        downsampled.clear();
//        downsampled.addAll(data.getPoints());
    }

    private Point3d meanPoint(List<Integer> indices) {
        List<Point3d> points = new ArrayList<>();
        for (int i : indices) {
            points.add(data.getPoints().get(i));
        }
        return optimizer.computeMedian3d(points, L1Median.class);
    }
}
