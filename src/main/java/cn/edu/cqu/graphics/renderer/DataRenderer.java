package cn.edu.cqu.graphics.renderer;

import cn.edu.cqu.graphics.Constants;
import cn.edu.cqu.graphics.config.UiSkeletonConfig;
import cn.edu.cqu.graphics.model.*;
import cn.edu.cqu.graphics.model.common.OsculatingPlane;
import cn.edu.cqu.graphics.model.tree.TreeSkeletonNode;
import cn.edu.cqu.graphics.model.tree.TreeSkeletons;
import cn.edu.cqu.graphics.platform.CanvasObject;
import cn.edu.cqu.graphics.platform.DataManager;
import cn.edu.cqu.graphics.protocol.CanvasAttr;
import cn.jimmiez.pcu.common.graph.Graphs;
import cn.jimmiez.pcu.common.graphics.BoundingBox;
import cn.jimmiez.pcu.model.Skeleton;
import cn.jimmiez.pcu.util.Pair;
import com.sun.j3d.utils.geometry.Cylinder;
import com.sun.j3d.utils.geometry.Sphere;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import javax.media.j3d.*;
import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.lang.Math.*;

/**
 * 负责对具体类型数据的渲染、缓存
 */
@Component
public class DataRenderer {


    @Autowired
    private transient UiSkeletonConfig config;

    @Autowired
    Logger logger;

    @Autowired
    DataManager manager;

    @Autowired
    TreeRenderer treeRenderer;

    @SuppressWarnings("unchecked")
    public BranchGroup render(CanvasObject object) {
        BranchGroup bg = new BranchGroup();
        switch (object.getType()) {
            case Constants.TYPE_TEST_1: {
                bg.addChild(new Sphere(0.8f));
                break;
            }
            case Constants.TYPE_TEST_2: {
                bg.addChild(new Cylinder(0.6f, 1.8f));
                break;
            }
            case Constants.TYPE_POINT_CLOUD: {
                PointCloud cloud = (PointCloud) object.getData();
                renderPointCloud(bg, cloud);
                break;
            }
            case Constants.TYPE_NEIGHBOR_GRAPH: {
                HashMap<Long, HashMap<Long, Double>> neighborMap = (HashMap<Long, HashMap<Long, Double>>) object.getData();
                HashMap<Long, Vertex> vertexHashMap =
                        (HashMap<Long, Vertex>) manager.fetchObject(Constants.TYPE_VERTEX_INDEX, object.getConfig());
                renderNeighborGraph(neighborMap, vertexHashMap, bg);
                break;
            }
            case Constants.TYPE_NEIGHBOR_INDICES: {
                List<int[]> nearestNeighborIndices = (List<int[]>) manager.fetchObject(Constants.TYPE_NEIGHBOR_INDICES, object.getConfig());
                PointCloud pointCloud = (PointCloud) manager.fetchObject(Constants.TYPE_POINT_CLOUD, object.getConfig());
                renderNeighborGraph2(pointCloud, nearestNeighborIndices, bg);
                break;
            }
            case Constants.TYPE_GEODESIC: {
                HashMap<Long, Vector<Long>> geodesicGraph = (HashMap<Long, Vector<Long>>) object.getData();
                HashMap<Long, Vertex> vertexHashMap2 = (HashMap<Long, Vertex>) manager.fetchObject(Constants.TYPE_VERTEX_INDEX, object.getConfig());
                Vertex rootVertex = (Vertex) manager.fetchObject(Constants.TYPE_ROOT_VERTEX, object.getConfig());
                renderGeodesicGraph(geodesicGraph, vertexHashMap2, rootVertex, bg);
                break;
            }
            case Constants.TYPE_LVP_LEVEL_SET: {
                LevelSet levelSet = (LevelSet) object.getData();
                HashMap<Long, HashMap<Long, Double>> weightMap = (HashMap<Long, HashMap<Long, Double>>) manager.fetchObject(Constants.TYPE_NEIGHBOR_GRAPH, object.getConfig());
                HashMap<Long, Vertex> vertexHashMap = (HashMap<Long, Vertex>) manager.fetchObject(Constants.TYPE_VERTEX_INDEX, object.getConfig());
                renderLevelSet(levelSet, weightMap, vertexHashMap, bg);
                break;
            }
            case Constants.TYPE_LVP_SKELETON_CURVE: {
                TreeSkeletons skeletons = (TreeSkeletons) object.getData();
                renderSkeleton(skeletons, bg);
                break;
            }
            case Constants.TYPE_TRIANGLES: {
//                List<Triangle> triangles = (List<Triangle>) object.getData();
                renderTreeSurface(object, bg);
                break;
            }
            case Constants.TYPE_CURV: {
                renderCurvature(object, bg);
                break;
            }
            case Constants.TYPE_VERTEX_INDEX: {
                renderDownSampledResult(object, bg);
                break;
            }
            case Constants.TYPE_COMMON_SKELETON_NODES: {
                HashMap<Long, CommonSkeletonNode> nodes = (HashMap<Long, CommonSkeletonNode>) object.getData();
                CanvasAttr attr = object.getAttr();
                renderSkeletonNodes(nodes, bg, attr);
                break;
            }
            case Constants.TYPE_COMMON_SKELETON_CURVE: {
                Skeleton skeleton = (Skeleton) object.getData();
                CanvasAttr attr = object.getAttr();
                renderSkeleton(skeleton, bg, attr);
                break;
            }
            case Constants.TYPE_POINT_CLOUD_SKINNED: {
                List<Point3d> pointCloud = (List<Point3d>) object.getData();
                Collection<List<Integer>> indiceList = (Collection<List<Integer>>) object.getSecondaryData();
                renderSkinnedPointCloud(pointCloud, indiceList, bg);
                break;
            }
            case Constants.TYPE_MERGE_HISTORY: {
//                List<Long> currentVertices = (List<Long>) object.getData();
                Point3d[] currentVertices = (Point3d[]) object.getData();
                HashMap<Long, Vertex> vertexHashMap = (HashMap<Long, Vertex>) manager.fetchObject(Constants.TYPE_VERTEX_INDEX, object.getConfig());
                List<Point3d> point3ds = new ArrayList<>();
                for (Point3d p: currentVertices) {
//                    point3ds.add(vertexHashMap.get(key).getPosition());
                    point3ds.add(p);
                }
                CanvasAttr attr = object.getAttr();
                renderScatteredPoints(point3ds, bg, attr);
                break;
            }
            case Constants.TYPE_OSCULATING_PLANE: {
                OsculatingPlane plane = (OsculatingPlane) object.getData();
                CanvasAttr attr = object.getAttr();
                renderOsculatingPlane(plane, bg, attr);
                break;
            }
            case Constants.TYPE_COLLAPSED_POINTS: {
                List<Point3d> point3ds = (List<Point3d>) object.getData();
                List<Boolean> okay = (List<Boolean>) object.getSecondaryData();
                CanvasAttr attr = object.getAttr();
                renderCollapsedPoints(point3ds, okay, bg, attr);
                break;
            }
            case Constants.TYPE_SCATTERED_POINTS:
            case Constants.TYPE_DOWNSAMPLE_POINTS:
            case Constants.TYPE_COLLAPSED_POINTS_2:
            case Constants.TYPE_BRIDGE_POINTS:
            case Constants.TYPE_MOVED_POINTS: {
                List<Point3d> point3ds = (List<Point3d>) object.getData();
                CanvasAttr attr = object.getAttr();
                renderScatteredPoints((point3ds), bg, attr);
                break;
            }
            case Constants.TYPE_POINT: {
                Point3d point = (Point3d) object.getData();
                CanvasAttr attr = object.getAttr();
                renderSinglePoint(point, bg, attr);
                break;
            }
            case Constants.TYPE_LVP_DEMO: {
                List<Point3d> point3ds = (List<Point3d>) object.getData();
                renderLVPDemo(point3ds, bg);
                break;
            }
            case Constants.TYPE_SPHERE: {
                cn.jimmiez.pcu.common.graphics.shape.Sphere s = (cn.jimmiez.pcu.common.graphics.shape.Sphere) object.getData();
                renderSphere(s, bg);
                break;
            }
            default:
                break;
        }
        bg.setCapability(BranchGroup.ALLOW_DETACH);
        return bg;
    }

    private void renderSinglePoint(Point3d point, BranchGroup bg, CanvasAttr attr) {
        Shape3D shape = new Shape3D();
        shape.setCapability(Shape3D.ALLOW_APPEARANCE_READ);
        PointArray pa = new PointArray(1, PointArray.COORDINATES);
        pa.setCoordinates(0, new Point3d[]{point});
        shape.setGeometry(pa);
        Appearance ap = new Appearance();

        ColoringAttributes ca = new ColoringAttributes();

        ap.setCapability(Appearance.ALLOW_COLORING_ATTRIBUTES_READ);
        ca.setColor(.9f, .1f, 0.1f);


        ap.setColoringAttributes(ca);
        ap.setMaterial(null);
        ap.setPolygonAttributes(new PolygonAttributes(PolygonAttributes.POLYGON_POINT, PolygonAttributes.CULL_BACK, 0));
        ap.setPointAttributes(new PointAttributes(7, false));
        shape.setAppearance(ap);

        bg.addChild(shape);
    }

    /**
     * 渲染蒙皮点云，用于骨架分支的居中操作
     * @param pointCloud 降采样后的点云
     * @param indiceList 通过一定机制分割后的索引列表们
     */
    private void renderSkinnedPointCloud(List<Point3d> pointCloud, Collection<List<Integer>> indiceList, BranchGroup bg) {
        BranchGroup totalGroup = new BranchGroup();
        Random random = new Random(System.currentTimeMillis());
        for (List<Integer> indices : indiceList) {
            List<Point3d> points = new ArrayList<>();
            for (int index : indices) points.add(pointCloud.get(index));

            Shape3D shape = new Shape3D();
            shape.setCapability(Shape3D.ALLOW_APPEARANCE_READ);
            PointArray pa = new PointArray(points.size(), PointArray.COORDINATES);
            pa.setCoordinates(0, points.toArray(new Point3d[points.size()]));
            shape.setGeometry(pa);
            Appearance ap = new Appearance();

            ColoringAttributes ca = new ColoringAttributes();

            ap.setCapability(Appearance.ALLOW_COLORING_ATTRIBUTES_READ);

            ca.setColor(random.nextFloat(), random.nextFloat(), random.nextFloat());


            ap.setColoringAttributes(ca);
            ap.setMaterial(null);
            ap.setPolygonAttributes(new PolygonAttributes(PolygonAttributes.POLYGON_POINT, PolygonAttributes.CULL_BACK, 0));
            ap.setPointAttributes(new PointAttributes(3, false));
            shape.setAppearance(ap);

            totalGroup.addChild(shape);

        }
        bg.addChild(totalGroup);
    }

    private void renderSphere(cn.jimmiez.pcu.common.graphics.shape.Sphere s, BranchGroup bg) {
        Appearance ap = new Appearance();
        ColoringAttributes ca = new ColoringAttributes();
        ca.setColor(.7f, 0.7f, 0.7f);
        ap.setColoringAttributes(ca);
        ap.setMaterial(null);

        Sphere sphere = new Sphere((float) s.getRadius(), 16, ap);
        bg.addChild(sphere);
    }

    private void renderCurvature(CanvasObject object, BranchGroup bg) {
        cn.jimmiez.pcu.util.Pair<List, List> pair = (cn.jimmiez.pcu.util.Pair<List, List>) object.getData();
        List<Point3d> points = pair.getKey();
        List<Double> cs = pair.getValue();

        BranchGroup shapes = new BranchGroup();
        for (int i = 0; i < points.size(); i ++) {
            Point3d[] ps = new Point3d[1];
            ps[0] = points.get(i);
            double sigma = cs.get(i);
            Shape3D shape = new Shape3D();
            PointArray pa = new PointArray(ps.length, PointArray.COORDINATES);
            pa.setCoordinates(0, ps);
            shape.setGeometry(pa);
            Appearance ap = new Appearance();
            ColoringAttributes ca = new ColoringAttributes();

            ca.setColor((float) sigma, 0f, 0f);
            ap.setColoringAttributes(ca);
//            ap.setMaterial(null);
//            ap.setPolygonAttributes(new PolygonAttributes(PolygonAttributes.POLYGON_POINT, PolygonAttributes.CULL_BACK, 0));
            ap.setPointAttributes(new PointAttributes(3, false));
            shape.setAppearance(ap);
            shapes.addChild(shape);
        }
        bg.addChild(shapes);
        bg.setCapability(BranchGroup.ALLOW_DETACH);
    }

    private void renderLVPDemo(List<Point3d> point3ds, BranchGroup bg) {
        bg.addChild(pointShape(point3ds.get(0), new Color(255, 0, 0), 15));
        point3ds.remove(0);
        bg.addChild(pointsShape(point3ds.toArray(new Point3d[point3ds.size()]), new Color(0, 0, 255), 5));
    }

    private BranchGroup pointsShape(Point3d[] ps, Color color, int size) {
        BranchGroup bg = new BranchGroup();
        Shape3D shape = new Shape3D();
        PointArray pa = new PointArray(ps.length, PointArray.COORDINATES);
        pa.setCoordinates(0, ps);
        shape.setGeometry(pa);
        Appearance ap = new Appearance();
        ColoringAttributes ca = new ColoringAttributes();

        ca.setColor(color.r / 255.0f, color.g / 255.0f, color.b / 255.0f);
        ap.setColoringAttributes(ca);
        ap.setMaterial(null);
        ap.setPolygonAttributes(new PolygonAttributes(PolygonAttributes.POLYGON_POINT, PolygonAttributes.CULL_BACK, 0));
        ap.setPointAttributes(new PointAttributes(size, false));
        shape.setAppearance(ap);
        bg.addChild(shape);
        bg.setCapability(BranchGroup.ALLOW_DETACH);
        return bg;
    }

    // size = 8
    private BranchGroup pointShape(Point3d centroid, Color color, int size) {
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
        ap.setPointAttributes(new PointAttributes(size, false));
        shape.setAppearance(ap);
        bg.addChild(shape);
        bg.setCapability(BranchGroup.ALLOW_DETACH);
        return bg;
    }

    private void renderOsculatingPlane(OsculatingPlane plane, BranchGroup bg, CanvasAttr attr) {
        if (plane.auxPoint == null || plane.center == null) {
            return;
        }
        int sampleCnt = 36;
        List<Point3d> list = new ArrayList<>();
        double r = plane.auxPoint.distance(plane.center);
        list.add(plane.auxPoint);
        for (int i = 0; i < sampleCnt; i ++) {
            Point3d ob = new Point3d(r * cos(PI * 2 / sampleCnt * i), r * sin(PI * 2 / sampleCnt * i), 0);
            Point3d point3d = plane.transformToWorld(ob);
            list.add(point3d);
            list.add(point3d);
        }
        list.add(plane.auxPoint);
        renderScatteredPoints(list, bg, attr);
    }

    private void renderNeighborGraph2(PointCloud pointCloud, List<int[]> nearestNeighborIndices, BranchGroup bg) {
        List<Point3d> list = new Vector<>();
        for (int i = 0; i < nearestNeighborIndices.size(); i ++) {
            int[] indice = nearestNeighborIndices.get(i);
            for (int j : indice) {
                list.add(pointCloud.getPoints().get(i));
                list.add(pointCloud.getPoints().get(j));
            }
        }
        Shape3D shape3D = edgeShape(list);
        bg.addChild(shape3D);

    }

    private void renderSkeleton(Skeleton skeleton, BranchGroup bg, CanvasAttr attr) {
        Shape3D lineShape = new Shape3D();
        int edgeCnt = Graphs.edgesCountOf(skeleton);
        if (edgeCnt < 1) {
            logger.warning("边的数量为0");
            return;
        }
        LineArray lineArray = new LineArray(edgeCnt, LineArray.COORDINATES);
        List<Point3d> points = new ArrayList<>();
        for (int vi : skeleton.vertices()) points.add(skeleton.getVertex(vi));
        BoundingBox box = BoundingBox.of(points);
        double diagonalLen = box.diagonalLength();
        logger.info("对角线长度：" + diagonalLen);
        float nodeSize = (float) (diagonalLen * 0.009);
        // 通用骨架 4.8
        float lineWidth = (float) (diagonalLen * Constants.CURVE_RATIO);
        // 如果是树，改成 2.8
//        float lineWidth = (float) (diagonalLen * 2.8);

        Point3d[] ends = new Point3d[edgeCnt];
        Set<Pair<Integer, Integer>> set = new HashSet<>();
        int cnt = 0;
        for (Integer vertexIndex : skeleton.vertices()) {
            for (Integer adjacentVertexIndex : skeleton.adjacentVertices(vertexIndex)) {
                if (! set.contains(new Pair<>(vertexIndex, adjacentVertexIndex))) {
                    ends[cnt ++] = skeleton.getVertex(vertexIndex);
                    ends[cnt ++] = skeleton.getVertex(adjacentVertexIndex);
                    set.add(new Pair<>(vertexIndex, adjacentVertexIndex));
                    set.add(new Pair<>(adjacentVertexIndex, vertexIndex));
                }
            }
        }
        lineArray.setCoordinates(0, ends);
        Appearance ap = new Appearance();
        ColoringAttributes ca = new ColoringAttributes();
        ca.setColor(1.0f, 0.0f, 0.f);
        ap.setColoringAttributes(ca);
        ap.setMaterial(null);
        ap.setLineAttributes(new LineAttributes(lineWidth, LineAttributes.PATTERN_SOLID, false));
        lineShape.setAppearance(ap);
        lineShape.setGeometry(lineArray);

        Appearance ap2 = new Appearance();

        ColoringAttributes ca2 = new ColoringAttributes();

        ap2.setCapability(Appearance.ALLOW_COLORING_ATTRIBUTES_READ);
        ca2.setColor(0f, 1.0f, .0f);

        ap2.setColoringAttributes(ca2);
        ap2.setMaterial(null);

        bg.addChild(lineShape);

        Transform3D transform3D = new Transform3D();
        for (int id : skeleton.vertices()) {
            Point3d point = skeleton.getVertex(id);
            if (Double.isNaN(point.x) || Double.isNaN(point.y) || Double.isNaN(point.z)) continue;
            TransformGroup tg = new TransformGroup();
            transform3D.set(new Vector3d(point.x, point.y, point.z));
            Sphere nodeShape = new Sphere(nodeSize );
            nodeShape.setAppearance(ap2);
            tg.addChild(nodeShape);
            tg.setTransform(transform3D);
            bg.addChild(tg);
        }
    }

    private void renderSkeletonNodes(HashMap<Long, CommonSkeletonNode> nodes, BranchGroup bg, CanvasAttr attr) {
        renderScatteredPoints(nodes.values().stream()
                        .map(CommonSkeletonNode::getCentroid)
                .collect(Collectors.toList()), bg, attr);
    }

    private static boolean showCollapse = true;
    private void renderCollapsedPoints(List<Point3d> points, List<Boolean> okay, BranchGroup bg, CanvasAttr attr) {
        if (points.size() < 1) {
            logger.info("渲染的散点数量为0。");
            return;
        }
        List<Point3d> okayPoints = new ArrayList<>();
        List<Point3d> notOkayPoints = new ArrayList<>();
        for (int i = 0; i < points.size(); i ++) {
            Point3d p = points.get(i);
            if (okay.get(i)) okayPoints.add(p);
            else notOkayPoints.add(p);
        }

        if (okayPoints.size() > 0 && showCollapse) {
            Shape3D okayShape = new Shape3D();
            okayShape.setCapability(Shape3D.ALLOW_APPEARANCE_READ);
            PointArray pa = new PointArray(okayPoints.size(), PointArray.COORDINATES);
            pa.setCoordinates(0, okayPoints.toArray(new Point3d[okayPoints.size()]));
            okayShape.setGeometry(pa);
            Appearance ap = new Appearance();

            ColoringAttributes ca = new ColoringAttributes();

            ap.setCapability(Appearance.ALLOW_COLORING_ATTRIBUTES_READ);
//        System.out.println("r: " + color.r + " g: " + color.g + " b: " + color.b);
            Color color = new Color(attr.primaryColor()[0], attr.primaryColor()[1], attr.primaryColor()[2]);
            ca.setColor(color.r * 1.0f / 255.0f, color.g * 1.0f / 255.0f, color.b * 1.0f / 255.0f);


            ap.setColoringAttributes(ca);
            ap.setMaterial(null);
            ap.setPolygonAttributes(new PolygonAttributes(PolygonAttributes.POLYGON_POINT, PolygonAttributes.CULL_BACK, 0));
            ap.setPointAttributes(new PointAttributes(attr.primarySize(), false));
            okayShape.setAppearance(ap);

            bg.addChild(okayShape);
        }
        if (notOkayPoints.size() > 0) {
            Shape3D notOkayShape = new Shape3D();
            notOkayShape.setCapability(Shape3D.ALLOW_APPEARANCE_READ);
            PointArray pa2 = new PointArray(notOkayPoints.size(), PointArray.COORDINATES);
            pa2.setCoordinates(0, notOkayPoints.toArray(new Point3d[notOkayPoints.size()]));
            notOkayShape.setGeometry(pa2);
            Appearance ap2 = new Appearance();

            ColoringAttributes ca2 = new ColoringAttributes();

            ap2.setCapability(Appearance.ALLOW_COLORING_ATTRIBUTES_READ);
//        System.out.println("r: " + color.r + " g: " + color.g + " b: " + color.b);
            Color color2 = new Color(attr.secondaryColor()[0], attr.secondaryColor()[1], attr.secondaryColor()[2]);
            ca2.setColor(color2.r * 1.0f / 255.0f, color2.g * 1.0f / 255.0f, color2.b * 1.0f / 255.0f);

            ap2.setColoringAttributes(ca2);
            ap2.setMaterial(null);
            ap2.setPolygonAttributes(new PolygonAttributes(PolygonAttributes.POLYGON_POINT, PolygonAttributes.CULL_BACK, 0));
            ap2.setPointAttributes(new PointAttributes(attr.primarySize(), false));
            notOkayShape.setAppearance(ap2);

            bg.addChild(notOkayShape);

        }

    }

    private void renderScatteredPoints(List<Point3d> points, BranchGroup bg, CanvasAttr attr) {
        if (points.size() < 1) {
            logger.info("渲染的散点数量为0。");
            return;
        }
        Shape3D shape = new Shape3D();
        shape.setCapability(Shape3D.ALLOW_APPEARANCE_READ);
        PointArray pa = new PointArray(points.size(), PointArray.COORDINATES);
        pa.setCoordinates(0, points.toArray(new Point3d[points.size()]));
        shape.setGeometry(pa);
        Appearance ap = new Appearance();

        ColoringAttributes ca = new ColoringAttributes();

        ap.setCapability(Appearance.ALLOW_COLORING_ATTRIBUTES_READ);
//        System.out.println("r: " + color.r + " g: " + color.g + " b: " + color.b);
        Color color = new Color(attr.primaryColor()[0], attr.primaryColor()[1], attr.primaryColor()[2]);
        ca.setColor(color.r * 1.0f / 255.0f, color.g * 1.0f / 255.0f, color.b * 1.0f / 255.0f);


        ap.setColoringAttributes(ca);
        ap.setMaterial(null);
        ap.setPolygonAttributes(new PolygonAttributes(PolygonAttributes.POLYGON_POINT, PolygonAttributes.CULL_BACK, 0));
        ap.setPointAttributes(new PointAttributes(attr.primarySize(), false));
        shape.setAppearance(ap);

        bg.addChild(shape);

    }

    private void renderDownSampledResult(CanvasObject object, BranchGroup bg) {
        HashMap<Long, Vertex> vertices = (HashMap<Long, Vertex>) object.getData();
        List<Point3d> points = vertices.values().stream().map(Vertex::getPosition).collect(Collectors.toList());

        Shape3D shape = new Shape3D();
        shape.setCapability(Shape3D.ALLOW_APPEARANCE_READ);
        PointArray pa = new PointArray(points.size(), PointArray.COORDINATES);
        pa.setCoordinates(0, points.toArray(new Point3d[points.size()]));
        shape.setGeometry(pa);
        Appearance ap = new Appearance();

        ColoringAttributes ca = new ColoringAttributes();

        ap.setCapability(Appearance.ALLOW_COLORING_ATTRIBUTES_READ);
        ca.setColor(1.0f, 0.0f, 0.0f);


        ap.setColoringAttributes(ca);
        ap.setMaterial(null);
        ap.setPolygonAttributes(new PolygonAttributes(PolygonAttributes.POLYGON_POINT, PolygonAttributes.CULL_BACK, 0));
        ap.setPointAttributes(new PointAttributes(3, false));
        shape.setAppearance(ap);

        bg.addChild(shape);
    }

    private void renderTreeSurface(CanvasObject object, BranchGroup bg) {
        bg.addChild(treeRenderer.render(object.getConfig()));
    }

    private void renderSkeleton(TreeSkeletons skeletons, BranchGroup bg) {
        List<Point3d> list = new Vector<>();
        tree2EdgePoints(skeletons.getTrunkRootClusterNode(), skeletons, list);
        bg.addChild(edgeShape(list));
    }

    private void renderLevelSet(LevelSet levelSet,
                                HashMap<Long, HashMap<Long, Double>> map,
                                HashMap<Long, Vertex> index2Vertex,
                                BranchGroup bg) {
        throw new NotImplementedException();
    }

    private void renderGeodesicGraph(HashMap<Long, Vector<Long>> paths,
                                     HashMap<Long, Vertex> index2Vertex,
                                     Vertex rootVertex,
                                     BranchGroup bg) {
        List<Point3d> list = new ArrayList<>();
        for (Long targetIndex : paths.keySet()) {
            Vector<Long> path = paths.get(targetIndex);
            Long prev = rootVertex.getIndex();
            for (Long index : path) {
                if (index2Vertex.get(index) == null) continue;
                list.add(index2Vertex.get(prev).getPosition());
                list.add(index2Vertex.get(index).getPosition());
                prev = index;
            }
        }
        bg.addChild(edgeShape(list));
    }

    private void renderNeighborGraph(HashMap<Long, HashMap<Long, Double>> neighborMap, HashMap<Long, Vertex> index2Vertex, BranchGroup bg) {
        List<Point3d> list = new ArrayList<>();
        for (Long indexA : neighborMap.keySet()) {
            HashMap<Long, Double> edges = neighborMap.get(indexA);
            for (Long indexB: edges.keySet()) {
                list.add(index2Vertex.get(indexA).getPosition());
                list.add(index2Vertex.get(indexB).getPosition());
            }
        }
        bg.addChild(edgeShape(list));
    }

    private Shape3D edgeShape(List<Point3d> points) {
        if (points.size() < 1) return new Shape3D();
        LineArray lineArray = new LineArray(points.size(), LineArray.COORDINATES);
        lineArray.setCoordinates(0, points.toArray(new Point3d[points.size()]));
        Appearance ap = new Appearance();
        ColoringAttributes ca = new ColoringAttributes();
//        ca.setColor(0.66f, 0.82f, 0.55f);
//        ca.setColor(random.nextFloat(), 0.0f, 0.0f);
        Color color = config.getSkeletonCurveColor();
        ca.setColor(color.r / 256.0f, color.g / 256.0f, color.b / 256.0f);
        ap.setColoringAttributes(ca);
        ap.setMaterial(null);
//        ap.setPointAttributes(new PointAttributes(config.getSkeletonCurveWidth(), false));
        ap.setLineAttributes(new LineAttributes((int)config.getSkeletonCurveWidth(), LineAttributes.PATTERN_SOLID, false));
        Shape3D lines = new Shape3D();
        lines.setAppearance(ap);
        lines.setGeometry(lineArray);
        return lines;
    }

    private void renderPointCloud(BranchGroup bg, PointCloud p) {
        Color color = config.getPointCloudColor();
        List<Point3d> points = p.getPoints();

        Shape3D shape = new Shape3D();
        shape.setCapability(Shape3D.ALLOW_APPEARANCE_READ);
        PointArray pa = new PointArray(points.size(), PointArray.COORDINATES);
        pa.setCoordinates(0, points.toArray(new Point3d[points.size()]));
        shape.setGeometry(pa);
        Appearance ap = new Appearance();


        ColoringAttributes ca = new ColoringAttributes();

        ap.setCapability(Appearance.ALLOW_COLORING_ATTRIBUTES_READ);
        ca.setCapability(ColoringAttributes.ALLOW_COLOR_WRITE);

        ca.setColor(0.5f, 0.5f, 0.5f);
//        ca.setColor(0.7f, 0.7f, 0.7f);
//        ca.setColor(1.0f, 1.0f, 1.0f); //white color


        ap.setColoringAttributes(ca);
        ap.setMaterial(null);
        ap.setPolygonAttributes(new PolygonAttributes(PolygonAttributes.POLYGON_POINT, PolygonAttributes.CULL_BACK, 0));
        ap.setPointAttributes(new PointAttributes(config.getPointCloudSize(), false));
//        ap.setPointAttributes(new PointAttributes(1, false));
        shape.setAppearance(ap);

        bg.addChild(shape);

    }

    private BranchGroup pointCloud(TreeSkeletons skeletons) {
        TreeSkeletonNode root = skeletons.getTrunkRootClusterNode();
        BranchGroup pointCloud = new BranchGroup();
        addVertices(pointCloud, skeletons);
        return pointCloud;
    }

    private void addVertices(BranchGroup group, TreeSkeletons skeletons) {
//        TreeSkeletonNode root = skeletons.getTrunkRootClusterNode();
//        if (root == null) return;
//        if (!config.isShowPointCloud()) {return;}
//        long index = root.getIndex();
//        Random random = new Random(System.currentTimeMillis());
//
//        List<Color> levelSetColors = initLevelSetColor();
//        BranchGroup bg = new BranchGroup();
//        float r = random.nextFloat();//把这3行代码移到循环外，就把整个树枝染色了
//        float g = random.nextFloat();
//        float b = random.nextFloat();
//        Color color = config.getPointCloudColor();
//
//        while (true) {
//            for (Long vertexIndex : root.getVertices()) {
//                Vertex vertex = index2Vertex.get(vertexIndex);
////                mainBranch.addChild(vertex.getShape(r, g, b));
//                switch (config.getPointCloudColorMode()) {
//                    case Constants.COLOR_MODE_BRANCH:
//                        break;
//                    case Constants.COLOR_MODE_LEVEL:
//                        r = levelSetColors.get(root.getLevelSetIndex()).getX();
//                        g = levelSetColors.get(root.getLevelSetIndex()).getY();
//                        b = levelSetColors.get(root.getLevelSetIndex()).getZ();
//
//                        break;
//                    case Constants.COLOR_MODE_SINGLE:
//                    default:
//                        r = color.r / 256.0f;
//                        g = color.g / 256.0f;
//                        b = color.b / 256.0f;
//                        break;
//                }
//                bg.addChild(vertex.pointsShape(r, g, b, 0.0f));
////                mainBranch.addChild(vertex.createCenterShape());// cell 中心点
//
//            }
//            if (root.getChildren().size() != 1) break;
//            Long nextIndex = root.getChildren().iterator().next();
//            root = skeletons.getNode(nextIndex);
//        }
//
//        group.addChild(bg);
//        for (Long nodeIndex : root.getChildren()) {
//            TreeSkeletonNode node = skeletons.getNode(nodeIndex);
//            addVertices(group, node);
//        }
    }


    private void tree2EdgePoints(TreeSkeletonNode rootNode, TreeSkeletons skeletons, List<Point3d> list) {
        if (rootNode.getChildren().size() < 1) return;
        for (int i = 0; i < rootNode.getChildren().size(); i++) {
            Long childIndex = rootNode.getChildren().get(i);
            TreeSkeletonNode childCluster = skeletons.getNode(childIndex);
            if (!childCluster.isVisible()) continue;
            list.add(rootNode.getCentroid());

            list.add(childCluster.getCentroid());
            tree2EdgePoints(childCluster, skeletons, list);
        }
    }

}
