package cn.edu.cqu.graphics.pipes.sample;

import cn.edu.cqu.graphics.Constants;
import cn.edu.cqu.graphics.alg.GridVoxelizer;
import cn.edu.cqu.graphics.alg.WeightedLocallyOptimalProjector;
import cn.edu.cqu.graphics.math.Optimizer;
import cn.edu.cqu.graphics.math.energy.L1Median;
import cn.edu.cqu.graphics.model.PointCloud;
import cn.edu.cqu.graphics.platform.CanvasObject;
import cn.edu.cqu.graphics.protocol.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.vecmath.Point3d;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

@Component
public class WLOPResample extends CachedPipe {

    @PipeInput
    @FromPreviousOutput(name = "Point Cloud")
    private PointCloud data;

    @Param(comment = "Iterations for WLOP", key = "iterNum", minVal = 5, maxVal = 200)
    Integer iterNum = 10;

    @Param(comment = "Num Of Samples", key = "sampleNum", minVal = 50, maxVal = 12000)
    Integer sampleNum = 7000;

    @Param(comment = "3D Grid Scale", key = "radiusScale", minVal = 1, maxVal = 15)
    Integer radiusScale = 1;

    @PipeOutput(type = Constants.TYPE_DOWNSAMPLE_POINTS, name = "Down-Sampled Points", visualize = true, visible = false)
    @EnableDiskCache
    @CanvasAttr(primaryColor = {125, 125, 125}, primarySize = 4)
    private List<Point3d> samples = new ArrayList<>();

    @Autowired
    private Logger logger;

    @Autowired
    Optimizer optimizer;

    @Override
    public String getName() {
        return "WLOP";
    }

    private Point3d meanPoint(List<Integer> indices) {
        List<Point3d> points = new ArrayList<>();
        for (int i : indices) {
            points.add(data.getPoints().get(i));
        }
        return optimizer.computeMedian3d(points, L1Median.class);
    }

    @Override
    public void apply() throws FileNotFoundException {
        samples.clear();

        GridVoxelizer voxelizer = new GridVoxelizer(2.);
        List<GridVoxelizer.GridCell> uniformCells = voxelizer.voxelize(data.getPoints());
        List<Point3d> uniformPoints = new ArrayList<>();

        for (GridVoxelizer.GridCell cell : uniformCells) {
            Point3d point = meanPoint(cell.getIndices());
            uniformPoints.add(point);
            samples.add(new Point3d(point));
        }

        Collections.shuffle(samples);
        int cnt = Math.min(samples.size(), sampleNum);
        samples = samples.subList(0, cnt);
        logger.info("Resample size: " + samples.size());

        List<Point3d> ps = new ArrayList<>();
        for (Point3d p : samples) ps.add(new Point3d(p));
        CanvasObject object1 = new CanvasObject("Original Samples", Constants.TYPE_SCATTERED_POINTS, ps, pipeline.getConfig());
        object1.setAttr(attr(new int[] {255, 0, 0}, new int[] {208, 33, 94}));
        object1.setVisible(false);
        pipeline.getModel().addCanvasObject(object1);

        CanvasObject object = new CanvasObject("Re-Sampling", Constants.TYPE_SCATTERED_POINTS, samples, pipeline.getConfig());
        pipeline.getModel().addCanvasObject(object);

        WeightedLocallyOptimalProjector projector = new WeightedLocallyOptimalProjector(uniformPoints);
        double h = projector.getH() / 2.;
        h *= radiusScale;
        projector.setH(h);
        projector.project(samples, iterNum, (i) -> {
            pipeline.getModel().updateCanvasObject(samples);
//            logger.info("第" + i + "次迭代");
            logger.info("Iteration of WLOP: " + i + ".");
        });

        pipeline.getModel().removeCanvasObject(object);
    }
}
