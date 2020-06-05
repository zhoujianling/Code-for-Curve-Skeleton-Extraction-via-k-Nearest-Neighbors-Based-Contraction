package cn.edu.cqu.graphics.platform;

import cn.edu.cqu.graphics.Constants;
import cn.edu.cqu.graphics.model.PointCloud;
import cn.edu.cqu.graphics.protocol.InputData;
import cn.edu.cqu.graphics.protocol.SkeletonFormat;
import cn.jimmiez.pcu.io.obj.ObjData;
import cn.jimmiez.pcu.io.obj.ObjReader;
import cn.jimmiez.pcu.io.off.OffReader;
import cn.jimmiez.pcu.io.ply.PlyReader;
import cn.jimmiez.pcu.model.PointCloud3f;
import cn.jimmiez.pcu.model.Skeleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import javax.activation.UnsupportedDataTypeException;
import javax.vecmath.Point3d;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.logging.Logger;

/**
 * 全局唯一的点云读取器，
 */
@Component
public class DataLoader {


    @Autowired
    private Logger logger;


    public DataLoader() {
    }


    private PointCloud loadTxtPointCloud(File srcFile) throws FileNotFoundException {
        PointCloud data = new PointCloud();
//        srcFile = new File(DataLoader.class.getClassLoader().getResource("data/" + config.getFileName()).getFile());
//        srcFile = new File(config.getFileName());
        Scanner s = new Scanner(new FileInputStream(srcFile));
        ArrayList<Point3d> points = new ArrayList<>();
        ArrayList<Double> xlist = new ArrayList<>();
        ArrayList<Double> ylist = new ArrayList<>();
        ArrayList<Double> zlist = new ArrayList<>();
        while (s.hasNext()) {
            double x = s.nextDouble();
            double y = s.nextDouble();
            double z = s.nextDouble();

            points.add(new Point3d(x, y, z));
            xlist.add(x);
            ylist.add(y);
            zlist.add(z);
        }

        data.setPoints(points);
        s.close();

        computeMd5(data, srcFile);
        return data;
    }

    private Skeleton loadZjlSkeleton(File file) {
        Skeleton skeleton = null;
        try {
            Scanner scanner = new Scanner(file);
            scanner.nextLine();
            skeleton = new Skeleton();
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                String[] parts = line.split(" ");
                if (parts.length < 4) break;
                int vi = Integer.valueOf(parts[0]);
                double x = Double.valueOf(parts[1]);
                double y = Double.valueOf(parts[2]);
                double z = Double.valueOf(parts[3]);
                Point3d point = new Point3d(x, y, z);
                skeleton.addVertex(vi, point);
            }
            scanner.nextLine();
            scanner.nextLine();
            scanner.nextLine();
            scanner.nextLine();
            scanner.nextLine();
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                String[] parts = line.split(" ");
                if (parts.length < 2) break;
                int vi = Integer.valueOf(parts[0]);
                int vj = Integer.valueOf(parts[1]);
                Point3d pi = skeleton.getVertex(vi);
                Point3d pj = skeleton.getVertex(vj);
                if (pi == null || pj == null) {
                    throw new IllegalStateException("?");
                }
                skeleton.addEdge(vi, vj, pi.distance(pj));
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return skeleton;
    }

    public void loadSkeleton(File file, SkeletonFormat format, OnLoadDataListener listener) {
        Skeleton skeleton = null;
        try {
            switch (format) {
                case SKEL_ZJL:
                    skeleton = loadZjlSkeleton(file);
                    break;
                case SKEL_L1_MEDIAN:
                    break;
                case SKEL_ROSA:
                    break;
                default:
                    throw new UnsupportedDataTypeException("未支持的数据格式");
            }
            if (skeleton != null) {
                listener.onLoadData(skeleton);
            } else {
                listener.onError("??");
            }
        } catch (Exception e) {
            e.printStackTrace();
            listener.onError(e.getMessage());
        }
    }


    private void loadPointCloud(File file, int format, OnLoadDataListener listener) throws IOException {
        PointCloud data = null;
        switch (format) {
            case Constants.FORMAT_POINT_CLOUD_LIUJI:
                data = loadTxtPointCloud(file);
                break;
            case Constants.FORMAT_POINT_CLOUD_PLY:
                data = loadPlyPointCloud(file);
                break;
            case Constants.FORMAT_POINT_CLOUD_ROSA_OFF:
                data = loadOffPointCloud(file);
                break;
            case Constants.FORMAT_POINT_CLOUD_OBJ:
                data = loadObjPointCloud(file);
                break;
            default:
                throw new UnsupportedOperationException("unsupported file type");
        }
        if (data != null) {
            computeMd5(data, file);
            listener.onLoadData(data);
        } else {
            logger.warning("fail to load point cloud data");
            listener.onError("Cannot load data.");
        }

    }

    public void loadData(File file, int type, OnLoadDataListener listener) {
        int pointIndex = file.getName().lastIndexOf(".");//.lastIndexOf(".");
        String suffix = file.getName().substring(pointIndex + 1);
        int format = 0;
        // 写的什么SB代码。。。。
        try {
            switch (type) {
                case Constants.TYPE_POINT_CLOUD: {
                    if (suffix.equals("txt")) {
                        format = Constants.FORMAT_POINT_CLOUD_LIUJI;
                    } else if (suffix.equals("off")) {
                        format = Constants.FORMAT_POINT_CLOUD_ROSA_OFF;
                    } else if (suffix.equals("ply")) {
                        format = Constants.FORMAT_POINT_CLOUD_PLY;
                    } else if (suffix.equals("obj")) {
                        format = Constants.FORMAT_POINT_CLOUD_OBJ;
                    }
                    loadPointCloud(file, format, listener);
                    break;
                }
                case Constants.TYPE_COMMON_SKELETON_CURVE: {
                    Skeleton skeleton = null;
                    if (suffix.equals(SkeletonFormat.SKEL_ZJL.getSuffix())) {
                        skeleton = loadZjlSkeleton(file);
                    } else if (suffix.equals(SkeletonFormat.SKEL_L1_MEDIAN.getSuffix())) {
                        // do something
                    } else if (suffix.equals(SkeletonFormat.SKEL_ROSA.getSuffix())) {
                        skeleton = loadRosaSkeleton(file);
                    }
                    if (skeleton != null) {
                        listener.onLoadData(skeleton);
                    } else {
                        listener.onError("导入骨架失败");
                    }
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Skeleton loadRosaSkeleton(File file) {
        Skeleton skeleton = null;
        try {
            Scanner scanner = new Scanner(file);
            skeleton = new Skeleton();
            int vertexNum = 0;
            int edgeNum = 0;
            if (scanner.hasNextLine()) {
                String firstLine = scanner.nextLine();
                String[] subStrings = firstLine.split(" ");
                for (String subString : subStrings) {
                    if (subString.startsWith("NV:")) {
                        vertexNum = Integer.valueOf(subString.substring(3));
                    } else if (subString.startsWith("NE:")) {
                        edgeNum = Integer.valueOf(subString.substring(3));
                    }
                }
            }
            for (int i = 0; i < vertexNum; i ++) {
                String line = scanner.nextLine();
                String[] subStrings = line.split(" ");
                double x = Double.valueOf(subStrings[1]);
                double y = Double.valueOf(subStrings[2]);
                double z = Double.valueOf(subStrings[3]);
                skeleton.addVertex(i + 1, new Point3d(x, y, z));
            }
            for (int i = 0; i < edgeNum; i ++) {
                String line = scanner.nextLine();
                String[] subStrings = line.split(" ");
                int vi = Integer.valueOf(subStrings[1]);
                int vj = Integer.valueOf(subStrings[2]);
                skeleton.addEdge(vi, vj, skeleton.getVertex(vi).distance(skeleton.getVertex(vj)));
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return skeleton;
    }

    private PointCloud loadObjPointCloud(File file) {
        ObjReader reader = new ObjReader();
        ObjData data = reader.read(file);
        // List<float[]> points = data.get(ObjReader.ObjDataType.V_GEOMETRIC_VERTICES);
        PointCloud pointCloud = new PointCloud();
        for (double[] fs : data.vertices()) {
            pointCloud.getPoints().add(new Point3d(fs[0], fs[1], fs[2]));
        }
        return pointCloud;
    }

    private PointCloud loadOffPointCloud(File file) throws IOException {
        OffReader reader = new OffReader();
        PointCloud3f pc = reader.read(file, PointCloud3f.class);
        PointCloud pointCloud = new PointCloud();
        for (float[] fs : pc.getPoints()) {
            pointCloud.getPoints().add(new Point3d(fs[0], fs[1], fs[2]));
        }
        return pointCloud;
    }

    private PointCloud loadPlyPointCloud(File file) {
        PlyReader reader = new PlyReader();
        PointCloud3f pc = reader.read(file, PointCloud3f.class);
        PointCloud pointCloud = new PointCloud();
        for (float[] fs : pc.getPoints()) {
            pointCloud.getPoints().add(new Point3d(fs[0], fs[1], fs[2]));
        }
        return pointCloud;
    }


    private void computeMd5(InputData data, File srcFile) throws FileNotFoundException {
        try {
            String md5Hex = DigestUtils.md5DigestAsHex(new FileInputStream(srcFile)).substring(0, 7);
            data.setMd5(md5Hex);
//            logger.info("点云文件md5：" + md5Hex);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

