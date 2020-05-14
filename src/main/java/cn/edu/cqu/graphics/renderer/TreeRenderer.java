package cn.edu.cqu.graphics.renderer;

import cn.edu.cqu.graphics.config.AlgorithmConfig;
import cn.edu.cqu.graphics.config.UiTreeConfig;
import cn.edu.cqu.graphics.model.Color;
import cn.edu.cqu.graphics.model.tree.TreeSkeletonNode;
import cn.edu.cqu.graphics.model.tree.TreeSkeletons;
import cn.edu.cqu.graphics.model.tree.Triangle;
import cn.edu.cqu.graphics.platform.DataManager;
import cn.edu.cqu.graphics.protocol.FromPreviousOutput;
import com.sun.j3d.utils.geometry.GeometryInfo;
import com.sun.j3d.utils.geometry.NormalGenerator;
import com.sun.j3d.utils.image.TextureLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.media.j3d.*;
import javax.swing.*;
import javax.vecmath.Color3f;
import javax.vecmath.Point3d;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
public class TreeRenderer {

    @FromPreviousOutput(name = "局部三角形")
    private transient List<Triangle> triangles;


    @FromPreviousOutput(name = "树骨架")
    private TreeSkeletons index2Clutser;

    @Autowired
    private transient UiTreeConfig config;

    @Autowired
    DataManager manager;

    private Random random = new Random();

    public TreeRenderer() {}

    public BranchGroup render(AlgorithmConfig algorithmConfig) {
        manager.searchCache(this, algorithmConfig);

        BranchGroup group = new BranchGroup();

        Shape3D shape = addTriangles();
        if (config.isShowCircle()) {
            group.addChild(circles());
        }
        group.addChild(shape);

        TexCoordGeneration tcg = new TexCoordGeneration(TexCoordGeneration.OBJECT_LINEAR, TexCoordGeneration.TEXTURE_COORDINATE_2);
        shape.getAppearance().setTexCoordGeneration(tcg);


        TransparencyAttributes ta = new TransparencyAttributes();
        ta.setTransparencyMode(TransparencyAttributes.BLENDED);
        ta.setTransparency(config.getSurfaceTransparency());
        if (Math.abs(config.getSurfaceTransparency() - 0) > 1e-5) {
            shape.getAppearance().setTransparencyAttributes(ta);
        }

        return group;
    }


    private Shape3D addTriangles() {
        Shape3D shape3D = new Shape3D();
        if (triangles.size() < 1) return shape3D;
        GeometryInfo info = new GeometryInfo(GeometryInfo.TRIANGLE_ARRAY);
        List<Point3d> points = new ArrayList<>();
        for (Triangle tri : triangles) {
            points.addAll(tri.pointlist());
        }

        Point3d[] pointsArray = new Point3d[points.size()];
        info.setCoordinates( points.toArray(pointsArray));
        //#############
        Appearance ap = new Appearance();

        ColoringAttributes ca = new ColoringAttributes();
        ca.setColor(0.2f, 0.0f, 0.0f);
        ap.setColoringAttributes(ca);

        Material m = new Material();
        m.setShininess(120.0f);
        m.setEmissiveColor(new Color3f(0.0f, 0, 0));
        m.setDiffuseColor(1, 1, 1); // 漫反射
        m.setSpecularColor(0, 0, 0); // 镜面反射
        ap.setMaterial(m);
        //##############
        NormalGenerator normalGenerator = new NormalGenerator();
        normalGenerator.generateNormals(info);

//        Stripifier stripifier = new Stripifier();
//        stripifier.stripify(info);
        //#############

//        info.getGeometryArray().setTexture
        shape3D.setGeometry(info.getGeometryArray());
        shape3D.setAppearance(ap);
        return shape3D;
    }

    private BranchGroup circles() {
        BranchGroup group = new BranchGroup();
        for (TreeSkeletonNode cluster : index2Clutser.nodes()) {
            List<Point3d> circlePoints = cluster.getCirclePoints();
            List<Point3d> edgePoints = new ArrayList<>();
            for (int i = 0; i < circlePoints.size(); i++) {
                Point3d curr = circlePoints.get(i % circlePoints.size());
                Point3d next = circlePoints.get((i + 1) % circlePoints.size());
                edgePoints.add(curr);
                edgePoints.add(next);
            }
            group.addChild(edgeShape(edgePoints));
        }
        return group;
    }

    private Shape3D edgeShape(List<Point3d> points) {
        Shape3D shape3D = new Shape3D();
        if (points.size() < 1) return shape3D;
        LineArray lineArray = new LineArray(points.size(), LineArray.COORDINATES);
        lineArray.setCoordinates(0, points.toArray(new Point3d[points.size()]));
        Appearance ap = new Appearance();

        ColoringAttributes ca = new ColoringAttributes();
        Color color = config.getCircleColor();
        ca.setColor(color.r / 256.0f, color.g / 256.0f, color.b / 256.0f);
        ap.setColoringAttributes(ca);

        ap.setMaterial(null);
        ap.setLineAttributes(new LineAttributes(config.getCircleWidth(), LineAttributes.PATTERN_SOLID, false));
        Shape3D lines = new Shape3D();
        lines.setAppearance(ap);
        lines.setGeometry(lineArray);
        return lines;
    }

    private Texture2D barkTexture(JFrame container) {
        URL filename = getClass().getClassLoader().getResource("bark.jpg");
        TextureLoader loader = new TextureLoader(filename, container);
        ImageComponent2D image = loader.getImage();
        if (image == null) {
            System.out.println("ERROR, CANNOT load imge.\n");
            return null;
        }
        Texture2D texture2D = new Texture2D(Texture.BASE_LEVEL, Texture.RGB, image.getWidth(), image.getHeight());
        texture2D.setImage(0, image);
        texture2D.setEnable(true);
        texture2D.setMagFilter(Texture.BASE_LEVEL_LINEAR); // 纹元到像素的映射滤波器
        texture2D.setMinFilter(Texture.BASE_LEVEL_LINEAR);
        return texture2D;
    }

}
