package cn.edu.cqu.graphics.renderer;

import cn.edu.cqu.graphics.Constants;
import cn.edu.cqu.graphics.config.SpringContext;
import cn.edu.cqu.graphics.config.UiSkeletonConfig;
import cn.edu.cqu.graphics.model.Color;
import cn.edu.cqu.graphics.model.LevelSet;
import cn.edu.cqu.graphics.model.Vertex;
import cn.edu.cqu.graphics.model.tree.TreeSkeletonNode;
import cn.edu.cqu.graphics.model.tree.TreeSkeletons;
import cn.edu.cqu.graphics.platform.CanvasObject;
import cn.edu.cqu.graphics.platform.CanvasObjectListModel;
import cn.edu.cqu.graphics.platform.DataManager;
import cn.edu.cqu.graphics.platform.MemCachePool;
import cn.edu.cqu.graphics.protocol.FromPreviousOutput;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sun.j3d.utils.geometry.Sphere;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.media.j3d.*;
import javax.vecmath.Color3f;
import javax.vecmath.Matrix4d;
import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 建议所有数据从外界送入，不从MemCache读入
 * MemCache只给PipeLine内部使用
 */
@Component
public class MainRenderer {

    @FromPreviousOutput(name = "顶点索引")
    private HashMap<Long, Vertex> index2Vertex;

    @FromPreviousOutput(name = "树骨架")
    private TreeSkeletons skeletons;

    @FromPreviousOutput(name = "邻居图")
    private HashMap<Long, HashMap<Long, Double>> weightMap;

    @FromPreviousOutput(name = "水平集")
    private LevelSet data;


    @Autowired
    private transient UiSkeletonConfig config;

    @FromPreviousOutput(name = "顶点半径索引")
    private transient HashMap<Long, Float> index2Radius;

    @Autowired
    DataManager manager;

    @Autowired
    DataRenderer dataRenderer;

    /**
     * 注意不同Pipeline的数据要隔离
     * 不同时间new出来的CanvasObject的内存位置必然不同，所以可以当key
     * 使用弱引用管理缓存
     */
    private Map<CanvasObject, BranchGroup> cache = new WeakHashMap<>();

    private BranchGroup mainBranch = new BranchGroup();

    private CanvasObjectListModel model = null;

    private List<Point3d> edgePoints = new ArrayList<>();
    private Random random = new Random(System.currentTimeMillis());
    private HashMap<Integer, Color3f> levelSetColors = new HashMap<>();

    public MainRenderer() {
    }

    public BranchGroup render() {
//        manager.searchCache(this);

//        createSceneGraph();

//        root = skeletons.getTrunkRootClusterNode();

        model.addCanvasObject(new CanvasObject("测试球", Constants.TYPE_TEST_1, null, MemCachePool.NO_CONFIG));
        mainBranch.setCapability(BranchGroup.ALLOW_CHILDREN_WRITE);
        mainBranch.setCapability(BranchGroup.ALLOW_CHILDREN_READ);
        mainBranch.setCapability(BranchGroup.ALLOW_CHILDREN_EXTEND);
        mainBranch.setCapability(BranchGroup.ALLOW_DETACH);
        for (CanvasObject object : model.getData()) {
            mainBranch.addChild(dataRenderer.render(object));
        }
//        return createSceneGraph();

        return mainBranch;
    }

    /**
     * 创建场景
     * @return 主场景图
     */
    private BranchGroup createSceneGraph() {
        BranchGroup bg = new BranchGroup();
        BranchGroup group = new BranchGroup();


//        group.addChild(skeletonGroup);// 添加骨架点
        group.addChild(edgeShape(edgePoints, false));//添加骨架线

        group.addChild(edgeShape(levelSetEdge(), false));//levelset划分后的图边
        group.addChild(edgeShape(differentLevelAdjacentGraph(), true));
        group.addChild(guessPointShape());
//        group.addChild(radiusSphere());
        //________________________________________________
        //________________________________________________

        //以下代码似乎是为了保存旋转矩阵的。
        BoundingSphere sphere = (BoundingSphere) group.getBounds();
        double s = 1.0 / sphere.getRadius();
        Point3d center = new Point3d();
        sphere.getCenter(center);
        Matrix4d mat = new Matrix4d(new double[]{s, 0, 0, -s * center.x,
                0, s, 0, -s * center.y,
                0, 0, s, -s * center.z,
                0, 0, 0, 1});
        TransformGroup tg = new TransformGroup(new Transform3D(mat));
        makePointCloudTransparent(group, "points");
        tg.addChild(group);
        bg.addChild(tg);
        return bg;
    }

    public BranchGroup skeletonCurve() {
        BranchGroup bg = new BranchGroup();
        bg.addChild(edgeShape(edgePoints, false));
        return bg;
    }


    private void initLevelSetColor() {

        for (TreeSkeletonNode cluster : skeletons.nodes()) {
            Color3f c = levelSetColors.get(cluster.getLevelSetIndex());
            if (c == null) {
                c = new Color3f(random.nextFloat(), random.nextFloat(), random.nextFloat());
                levelSetColors.put(cluster.getLevelSetIndex(), c);
            }
        }
    }

    /**
     * 用来把点云设置成透明的，首先从场景图中找到代表点云的Shape3d节点，然后设置其透明度属性
     * 为什么不在创建这个节点的时候就设置，反而现在去一个个找呢？
     * 一言难尽。。。。
     * 为了保持变换矩阵的一致性。。。
     * @param group
     * @param name 可以是 points  skeleton
     */
    private void makePointCloudTransparent(BranchGroup group, String name) {
        List<Shape3D> nodes = new Vector<>();
        findPointsCloudNode(group, nodes, name);
        TransparencyAttributes ta = new TransparencyAttributes();
        ta.setTransparencyMode(TransparencyAttributes.BLENDED);
        ta.setTransparency(1.0f);
//        ta.setTransparency(0.75f);
//        ta.setTransparency(0.0f);
        for (Shape3D node : nodes) {
            node.getAppearance().setTransparencyAttributes(ta);
        }
    }

    private void findPointsCloudNode(BranchGroup group, List<Shape3D> nodes, String name) {
        if (group == null) return;
        Enumeration<Node> children = group.getAllChildren();
        List<BranchGroup> parents = new Vector<>();
        while (children.hasMoreElements()) {
            Node node = children.nextElement();
            if (node instanceof Shape3D
                    && node.getName() != null
                    && node.getName().equals(name)) {
                nodes.add((Shape3D) node);
            } else if (node instanceof BranchGroup) {
                parents.add((BranchGroup) node);
            }
        }
        for (BranchGroup child : parents) {
            findPointsCloudNode(child, nodes, name);
        }
    }


    private List<Point3d> loadGuessPoints(String guessFileName) throws FileNotFoundException {
        FileReader reader = new FileReader(guessFileName);
        Gson gson = new Gson();
        return gson.fromJson(reader, new TypeToken<List<Point3d>>() {}.getType());

    }

    @SuppressWarnings("Duplicates")
    private List<Point3d> levelSetEdge() {
        List<Point3d> list = new ArrayList<>();
        if (! config.isShowLevelSetGraph()) {return list;}
        try {
            FileReader reader = new FileReader("map.txt");
            HashMap<Long, HashMap<Long, Double>> map = new Gson()
                    .fromJson(reader, new TypeToken<HashMap<Long, HashMap<Long, Double>>>(){}.getType());
            for (Long outterIndex : map.keySet()) {
                HashMap<Long, Double> innerMap = map.get(outterIndex);
                for (Long innerKey : innerMap.keySet()) {
                    list.add(index2Vertex.get(outterIndex).getPosition());
                    list.add(index2Vertex.get(innerKey).getPosition());
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return list;
    }

    private Shape3D guessPointShape() {
        Shape3D shape = new Shape3D();
        if (!config.isShowGuessPoint()) {return shape;}
        try {
            List<Point3d> list = loadGuessPoints("guess.txt");
            PointArray pa = new PointArray(list.size(), PointArray.COORDINATES);
            pa.setCoordinates(0, list.toArray(new Point3d[list.size()]));
            shape.setGeometry(pa);
            Appearance ap = new Appearance();
            ColoringAttributes ca = new ColoringAttributes();

            UiSkeletonConfig config = SpringContext.instance().getBean(UiSkeletonConfig.class);
            Color c = config.getGuessPointColor();
            ca.setColor(c.r / 255.0f, c.g / 255.0f, c.b / 255.0f);
            ap.setColoringAttributes(ca);
            ap.setMaterial(null);
            ap.setPolygonAttributes(new PolygonAttributes(PolygonAttributes.POLYGON_POINT, PolygonAttributes.CULL_BACK, 0));
            ap.setPointAttributes(new PointAttributes(config.getGuessPointSize(), false));
            shape.setAppearance(ap);
        } catch (FileNotFoundException | IllegalArgumentException e) {
            e.printStackTrace();
        }
        return shape;
    }


    /**
     * 顶点间连线的模型
     *
     * @return
     */
    private Shape3D edgeShape(List<Point3d> points, boolean randomColor) {
        if (points.size() < 1) return new Shape3D();
        LineArray lineArray = new LineArray(points.size(), LineArray.COORDINATES);
        lineArray.setCoordinates(0, points.toArray(new Point3d[points.size()]));
        Appearance ap = new Appearance();
        ColoringAttributes ca = new ColoringAttributes();
//        ca.setColor(0.66f, 0.82f, 0.55f);
//        ca.setColor(random.nextFloat(), 0.0f, 0.0f);
        Color color = config.getSkeletonCurveColor();
        ca.setColor(color.r / 256.0f, color.g / 256.0f, color.b / 256.0f);
        if (randomColor) {
            ca.setColor(random.nextFloat(), random.nextFloat(), random.nextFloat());
        }
        ap.setColoringAttributes(ca);
        ap.setMaterial(null);
//        ap.setPointAttributes(new PointAttributes(config.getSkeletonCurveWidth(), false));
        ap.setLineAttributes(new LineAttributes((int)config.getSkeletonCurveWidth(), LineAttributes.PATTERN_SOLID, false));
        Shape3D lines = new Shape3D();
        lines.setAppearance(ap);
        lines.setGeometry(lineArray);
        lines.setName("skeleton");
        return lines;
    }


    private List<Point3d> differentLevelAdjacentGraph() {
        List<Point3d> result = new ArrayList<>();
        if (!config.isShowNeighborhoodGraph()) {return result;}
        for (TreeSkeletonNode cluster : data.getLevelSets()) {
//            List<Point3d> list = new ArrayList<>();
            for (Long indexA : cluster.getVertices()) {
                HashMap<Long, Double> edges = weightMap.get(indexA);
                for (Long indexB: edges.keySet()) {
//                    list.add(index2Vertex.getNode(indexA).getPosition());
//                    list.add(index2Vertex.getNode(indexB).getPosition());
                    result.add(index2Vertex.get(indexA).getPosition());
                    result.add(index2Vertex.get(indexB).getPosition());
                }
            }
//            result.add(list);
        }
        return result;
    }

    private BranchGroup radiusSphere() {
        BranchGroup group = new BranchGroup();
        Transform3D t3 = new Transform3D();
        for (Long clusterIndex : index2Radius.keySet()) {
            TreeSkeletonNode cluster = skeletons.getNode(clusterIndex);
            t3.set(new Vector3d(cluster.getCentroid().x, cluster.getCentroid().y, cluster.getCentroid().z));

            float radius = index2Radius.get(clusterIndex);
            Node sphere = new Sphere(radius);
            TransformGroup tg = new TransformGroup();
            tg.addChild(sphere);
            tg.setTransform(t3);
            group.addChild(tg);
        }
        return group;
    }


    private BranchGroup createLevelSetShape() {
        BranchGroup bg = new BranchGroup();
//        int cnt = 0;
        for (TreeSkeletonNode cluster : data.getLevelSets()) {
            Shape3D shape = new Shape3D();
            List<Point3d> points = cluster.getVertices().stream().map(index -> index2Vertex.get(index).getPosition()).collect(Collectors.toList());
            if (points.size() < 1) continue;
            PointArray pa = new PointArray(points.size(), PointArray.COORDINATES);
            pa.setCoordinates(0, points.toArray(new Point3d[points.size()]));
            shape.setGeometry(pa);
            Appearance ap = new Appearance();
            ColoringAttributes ca = new ColoringAttributes();

            ca.setColor(random.nextFloat(), random.nextFloat(), random.nextFloat());
//            if (cnt == 8) {
//                ca.setColor(1.0f, 0.0f, 0.0f);
//            }
//            cnt += 1;
            ap.setColoringAttributes(ca);
            ap.setMaterial(null);
            ap.setPolygonAttributes(new PolygonAttributes(PolygonAttributes.POLYGON_POINT, PolygonAttributes.CULL_BACK, 0));
            ap.setPointAttributes(new PointAttributes(6, false));
            shape.setAppearance(ap);
            bg.addChild(shape);
        }
        return bg;
    }

    private BranchGroup axisXYZ() {
        BranchGroup bg = new BranchGroup();

        return bg;
    }

    /**
     * 需要处理：
     * 1、不同Pipeline
     * 2、数据修改
     * 2、数据增加
     * 3、数据删除
     * 4、不变数据缓存
     */
    public void updateUI() {
        mainBranch.removeAllChildren();
        List<CanvasObject> list = model.getData();
        for (CanvasObject object : list) {
            BranchGroup bg = null;
            BranchGroup cachedBG = cache.get(object);//小心弱引用

            if (! object.isVisible()) continue;

            if (cachedBG == null) {
                bg = dataRenderer.render(object);
                cache.put(object, bg);
            } else {
                if (object.hasChanged()) {
                    bg = dataRenderer.render(object);
                    cache.put(object, bg);
                } else {
                    bg = cachedBG;
                }
            }
            mainBranch.addChild(bg);
            object.setChanged(false);
        }
    }

    public CanvasObjectListModel getModel() {
        return model;
    }

    public BranchGroup getMainBranch() {
        return mainBranch;
    }

    public void setModel(CanvasObjectListModel m) {
        this.model = m;
    }
}
