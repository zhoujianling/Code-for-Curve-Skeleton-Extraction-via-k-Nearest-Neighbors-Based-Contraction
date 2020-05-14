package cn.edu.cqu.graphics.ui;

import com.sun.j3d.utils.behaviors.mouse.MouseBehaviorCallback;
import com.sun.j3d.utils.behaviors.mouse.MouseRotate;
import com.sun.j3d.utils.behaviors.mouse.MouseTranslate;
import com.sun.j3d.utils.behaviors.mouse.MouseWheelZoom;
import com.sun.j3d.utils.universe.SimpleUniverse;

import javax.media.j3d.*;
import javax.vecmath.Matrix4d;
import javax.vecmath.Point3d;
import java.awt.*;
import java.awt.image.BufferedImage;


public class MainCanvas extends Canvas3D{

//    private List<>
    private TransformGroup rootTG = null;
    private MouseRotate mouseRotate = null;
    private MouseWheelZoom mouseWheelZoom = null;
    private MouseTranslate mouseMove = null;
    private BoundingSphere boundingSphere = null;
    private SimpleUniverse su = null;
    private double tz = 0.0; // 相机视口的 Z 轴坐标

    private View view = null;

    private Canvas3D offScreenCanvas;

    public MainCanvas(GraphicsConfiguration gc, BranchGroup canvasContent) {
        super(gc);
        // 创建根分支
        BranchGroup root = new BranchGroup();
        boundingSphere = getBoundingSphere();

        // 创建变换组
        rootTG = new TransformGroup();

        rootTG.setCapability(TransformGroup.ALLOW_TRANSFORM_READ);
        rootTG.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);

        mouseRotate = new MouseRotate(rootTG);
        mouseRotate.setSchedulingBounds(boundingSphere);
        mouseMove = new MouseTranslate(rootTG);
        mouseMove.setSchedulingBounds(boundingSphere);
        mouseWheelZoom = new MouseWheelZoom(rootTG);
        mouseWheelZoom.setSchedulingBounds(boundingSphere);
        rootTG.addChild(mouseRotate);
        rootTG.addChild(mouseMove);
        rootTG.addChild(mouseWheelZoom);
        // 创建场景
        BranchGroup bg = canvasContent;
        bg.compile();

        // root->rootTG->bg
        root.addChild(rootTG);
        rootTG.addChild(bg);
        // 设置背景
        setupBackground(root);
        // 创建Universe
        su = new SimpleUniverse(this);
        // 设置视点位置
        su.getViewingPlatform().setNominalViewingTransform();
        su.addBranchGraph(root);
        view = su.getViewer().getView();

        //获取相机坐标
        Transform3D tr = new Transform3D();
        double[] mat = new double[16];
        su.getViewingPlatform().getViewPlatformTransform().getTransform(tr);
        tr.get(mat);
        tz = mat[11];

        offScreenCanvas = new Canvas3D(gc, true);
        Screen3D sOn = getScreen3D();
        Screen3D sOff = offScreenCanvas.getScreen3D();
        sOff.setSize(new Dimension(800, 800));
        sOff.setPhysicalScreenWidth(sOn.getPhysicalScreenWidth());
        sOff.setPhysicalScreenHeight(sOn.getPhysicalScreenHeight());
        Point loc = getLocationOnScreen();
        offScreenCanvas.setOffScreenLocation(loc);

//        su.getViewer().getView().addCanvas3D(offScreenCanvas);
    }

    public BoundingSphere getBoundingSphere() {
        return new BoundingSphere(new Point3d(0, 0, 0), 2.0);
    }

    private void setupBackground(BranchGroup root) {
        Background back = new Background(1.0f, 1.0f, 1.0f);
        back.setApplicationBounds(boundingSphere);
        root.addChild(back);
    }

    public TransformGroup getTr() {
        return rootTG;
    }

    public MouseWheelZoom getMouseWheelZoom() {
        return mouseWheelZoom;
    }

    public void setEvent(MouseBehaviorCallback callback) {
        this.mouseWheelZoom.setupCallback(callback);
        this.mouseRotate.setupCallback(callback);
        this.mouseMove.setupCallback(callback);
    }

    public double getTz() {
        return tz;
    }

    public Matrix4d getCamera() {
        Matrix4d matrix = new Matrix4d();
        Transform3D tr = new Transform3D();
        rootTG.getTransform(tr);
        tr.get(matrix);
        return matrix;
    }

    public void setCamera(Matrix4d matrix) {
        Transform3D tr = new Transform3D();
        rootTG.getTransform(tr);
        tr.set(matrix);
        rootTG.setTransform(tr);
    }

    public BufferedImage snapshot(int w, int h) {
        Screen3D sOn = getScreen3D();
        Screen3D sOff = offScreenCanvas.getScreen3D();
        sOff.setSize(sOn.getSize().width, sOn.getSize().height);

        view.stopView();
        view.addCanvas3D(offScreenCanvas);
//        // ===
//        // ===
        BufferedImage bufferedImage = new BufferedImage(sOn.getSize().width, sOn.getSize().height, BufferedImage.TYPE_INT_ARGB);
        ImageComponent2D buffer = new ImageComponent2D(ImageComponent.FORMAT_RGBA, bufferedImage);
        offScreenCanvas.setOffScreenBuffer(buffer);
        view.startView();
        offScreenCanvas.renderOffScreenBuffer();
        offScreenCanvas.waitForOffScreenRendering();
        bufferedImage = offScreenCanvas.getOffScreenBuffer().getImage();
        view.removeCanvas3D(offScreenCanvas);
        return bufferedImage;
    }

}
