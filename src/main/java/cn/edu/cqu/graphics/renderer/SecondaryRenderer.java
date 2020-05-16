package cn.edu.cqu.graphics.renderer;

import cn.edu.cqu.graphics.Constants;
import cn.edu.cqu.graphics.config.FissionConfig;
import cn.edu.cqu.graphics.math.Optimizer;
import cn.edu.cqu.graphics.math.energy.L1Median;
import cn.edu.cqu.graphics.math.energy.L1MedianVariancePenalty;
import cn.edu.cqu.graphics.math.energy.L1MedianWu;
import cn.edu.cqu.graphics.model.Color;
import cn.edu.cqu.graphics.model.Vertex;
import cn.edu.cqu.graphics.model.tree.TreeSkeletonNode;
import cn.edu.cqu.graphics.model.tree.TreeSkeletons;
import cn.edu.cqu.graphics.pipes.lvp.InitialSkeletonNovel;
import cn.edu.cqu.graphics.pipes.lvp.Refinement;
import cn.edu.cqu.graphics.platform.DataManager;
import cn.edu.cqu.graphics.protocol.FromPreviousOutput;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.media.j3d.*;
import javax.vecmath.Color3f;
import javax.vecmath.Point3d;
import java.util.*;
import java.util.logging.Logger;


/**
 * 焦点追踪？
 */
@Component
public class SecondaryRenderer {

    @FromPreviousOutput
//    private HashMap<Long, TreeSkeletonNode> index2Cluster;
    private TreeSkeletons index2Cluster;

    @FromPreviousOutput
    private HashMap<Long, Vertex> index2Vertex;

    @Autowired
    private Refinement refinement;

    @Autowired
    private InitialSkeletonNovel skeleton;

    @Autowired
    private FissionConfig config;

//    @Autowired
//    private Set<Long> rootVertices;

    @Autowired
    private Logger logger;

    @Autowired
    private Optimizer optimizer;

    private BranchGroup reserveBranch;
    private Vector<HashSet<Long>> clusters = new Vector<>();
    private Timer timer = new Timer();
    private HashMap<Long, Shape3D> vertex2Shape = new HashMap<>();
    private Random random = new Random();

    public SecondaryRenderer(DataManager manager) {
        manager.searchCache(this);
    }

    public BranchGroup fissionNode() {
        BranchGroup group = new BranchGroup();

        switch (config.getType()) {
            case Constants.SHOW_DATA_TYPE_CUSTOM:
                group.addChild(customCluster());
                break;
            case Constants.SHOW_DATA_TYPE_FISSION:
                reserveBranchProcess(group);
                group.addChild(reserveBranch);
                break;
            case Constants.SHOW_DATA_TYPE_EXTRACTING:
                group.addChild(extractingClusters());
                break;
        }
        return group;
    }

    private BranchGroup extractingClusters() {
        BranchGroup bg = new BranchGroup();
        List<Map.Entry<Long, Double>> entries = skeleton.getDistanceEntrySet();
        for (int i = entries.size() / 10 * 9; i < entries.size() / 10 * 10; i ++) {
            Long vertexIndex = entries.get(i).getKey();
            Vertex vertex = index2Vertex.get(vertexIndex);
            if (!entries.get(i).getValue().equals(Double.MAX_VALUE)) {
                bg.addChild(vertex.pointsShape(5.0f, 0, 0, 1));
            }
        }
        return bg;
    }


    private void reserveBranchProcess(BranchGroup group) {
//        TreeSkeletonNode cluster = refinement.getSnapshot();
        TreeSkeletonNode cluster = index2Cluster.getNode(config.getShowFissionIndex());
        uniqueCluster(cluster, group); // 添加原始数据
        reserveBranch = new BranchGroup();
        reserveBranch.setCapability(BranchGroup.ALLOW_CHILDREN_WRITE);
        reserveBranch.setCapability(BranchGroup.ALLOW_CHILDREN_READ);
        reserveBranch.setCapability(BranchGroup.ALLOW_CHILDREN_EXTEND);
        reserveBranch.setCapability(BranchGroup.ALLOW_DETACH);
    }

    private BranchGroup customCluster() {
        BranchGroup group = new BranchGroup();
//        for (Long vertexIndex : rootVertices) {
//            Vertex vertex = index2Vertex.get(vertexIndex);
//            Shape3D shape = vertex.pointsShape(5.0f, 0,0, 1);
//            group.addChild(shape);
//        }
//
//        Point3d p = optimizer.computeMedian3d(rootVertices, index2Vertex, L1MedianWu.class);
//        group.addChild(pointShape(p, config.getL1VarColor()));
//        logger.info("Fission, p.x : " + p.x + " p.y: " + p.y + " p.z: " + p.z);
        return group;
    }


    /**
     * 似乎是为了发现。。。单个 cluster
     * @param group
     */
    private void uniqueCluster(TreeSkeletonNode cluster, BranchGroup group) {
        if (cluster == null || cluster.getVertices().size() < 1) return;
        group.removeAllChildren();
        for (Long vertexIndex : cluster.getVertices()) {
            Vertex vertex = index2Vertex.get(vertexIndex);
            Shape3D shape = vertex.pointsShape(5.0f, 0,0, 1);
            vertex2Shape.put(vertex.getIndex(), shape);

            group.addChild(shape);
        }

        group.addChild(pointShape(optimizer.computeMedian3d(cluster.getVertices(), index2Vertex, L1Median.class), config.getL1Color()));
        group.addChild(pointShape(optimizer.computeMedian3d(cluster.getVertices(), index2Vertex, L1MedianVariancePenalty.class), config.getL1VarColor()));
        Point3d p3 = optimizer.computeMedian3d(cluster.getVertices(), index2Vertex, L1MedianWu.class);
        p3.add(new Point3d(0, 0, 0.003));
        group.addChild(pointShape(p3, new Color(0, 0, 255)));
//        group.addChild(cluster.getCentroidShape(index2Vertex));

//        group.addChild(cluster.getOriginalCentroidShape(index2Vertex));
    }

    public void start(UI ui) {
//        TreeSkeletonNode cluster = refinement.getSnapshot();
        TreeSkeletonNode cluster = index2Cluster.getNode(config.getShowFissionIndex());
        Vector<Point3d> kmeansCentroids = refinement.guessInitialKMeansCentroid(cluster);
        for (Point3d p : kmeansCentroids) {
            BranchGroup s = pointShape(p, new Color(255, 100, 80));
            reserveBranch.addChild(s);
        }
        List<Color3f> colors = new ArrayList<>();
        for (int i = 0; i < kmeansCentroids.size(); i++) {
            colors.add(new Color3f(random.nextFloat(), random.nextFloat(), random.nextFloat()));
        }


//        for (Point3d p : kmeansCentroids) {
//            System.out.println(String.format("before fission, %.3f, %.3f, %.3f", p.x, p.y, p.z));
//        }
//
        Refinement.DEMO_STATE = true;
        new Thread(() ->  {
//                Thread.sleep(100L);
                refinement.kMeans(cluster, kmeansCentroids, clusters, 25);
        }).start();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                synchronized (Refinement.class) {
                    reserveBranch.removeAllChildren();
                    for (Point3d p : kmeansCentroids) {
                        reserveBranch.addChild(pointShape(p, new Color(0, 255, 255)));
                    }
                    for (int index = 0; index < clusters.size(); index++) {
                        HashSet<Long> verticeSet = clusters.get(index);
                        Color3f color = colors.get(index);
                        for (Long vertexIndex : verticeSet) {
                            Shape3D shape = vertex2Shape.get(vertexIndex);
                            shape.getAppearance().getColoringAttributes().setColor(color);
                        }
                    }
                }
                ui.repaint();
            }
        }, 100, 300L);
//        while (true) {
//            ui.repaint();
//            System.out.println("SHIT--------------------");
//            Thread.sleep(500L);
//        }
    }

    private BranchGroup pointShape(Point3d centroid, Color color) {
        BranchGroup bg = new BranchGroup();
        Shape3D shape = new Shape3D();
        PointArray pa = new PointArray(1, PointArray.COORDINATES);
        pa.setCoordinates(0, new Point3d[] {centroid});
        shape.setGeometry(pa);
        Appearance ap = new Appearance();
        ColoringAttributes ca = new ColoringAttributes();

        ca.setColor(color.r / 255.0f, color.g / 255.0f, color.b / 255.0f);
//        ca.setColor(1.0f, 0.0f, 0.0f);
        ap.setColoringAttributes(ca);
        ap.setMaterial(null);
        ap.setPolygonAttributes(new PolygonAttributes(PolygonAttributes.POLYGON_POINT, PolygonAttributes.CULL_BACK, 0));
        ap.setPointAttributes(new PointAttributes(8, false));
        shape.setAppearance(ap);
        bg.addChild(shape);
        bg.setCapability(BranchGroup.ALLOW_DETACH);
        return bg;
    }

    public interface UI {
        void repaint();
    }

}
