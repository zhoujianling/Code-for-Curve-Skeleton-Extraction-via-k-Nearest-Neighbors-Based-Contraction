package cn.edu.cqu.graphics;

/**
 * Created by zjl on 2017/7/7.
 *
 * //// TODO: 2018/7/22  是否可以写一个注解处理器自动生成UNIQUE的type？
 */
public class Constants {
    public static final Double INFINITE = 100000.0;

    // normal
//    public static final Double CURVE_RATIO = 4.8;
    // 怪兽 要粗一些  6.8
    // 黄桷，要细一些 0.6
    public static final Double CURVE_RATIO = 0.6;
    // 猪
//    public static final Double CURVE_RATIO = 2.8;
//    public static final Double CURVE_RATIO = 1.5;
//

    public static final String SHOW_DATA_TYPE_CUSTOM = "CUSTOM"; // rootVertices() Bean
    public static final String SHOW_DATA_TYPE_FISSION = "FISSION";
    public static final String SHOW_DATA_TYPE_EXTRACTING = "EXTRACTING";

    public static final int TYPE_DEFAULT = 0;
    public static final int TYPE_NEIGHBOR_GRAPH = 1;
    public static final int TYPE_TRIANGLES = 2;
    public static final int TYPE_GEODESIC = 3;
    public static final int TYPE_VERTEX_INDEX = 4;
    public static final int TYPE_ROOT_VERTEX = 5;
    public static final int TYPE_CLUSTER_RADII = 6;
    public static final int TYPE_POINT_CLOUD = 7; //
    public static final int TYPE_LVP_SKELETON_CURVE = 8;
    public static final int TYPE_LVP_LEVEL_SET = 9;
    public static final int TYPE_SCATTERED_POINTS = 10;
    public static final int TYPE_COMMON_SKELETON_NODES = 11;
    public static final int TYPE_COMMON_SKELETON_CURVE = 12;
    public static final int TYPE_MERGE_HISTORY = 13;
    public static final int TYPE_NEIGHBOR_INDICES = 14;
    public static final int TYPE_OCTREE = 15;
    public static final int TYPE_MINIMAL_SPANNING_TREE = 16;
    public static final int TYPE_COLLAPSED_POINTS = 17;
    public static final int TYPE_OSCULATING_PLANE = 18;
    public static final int TYPE_MOVED_POINTS = 19; // MLS输出
    public static final int TYPE_COLLAPSED_POINTS_2 = 20;
    public static final int TYPE_DOWNSAMPLE_POINTS= 21;
    public static final int TYPE_LVP_DEMO = 22; // 用于lvp论文插图
    public static final int TYPE_INDEX_MAP = 23;
    public static final int TYPE_CURV= 24;
    public static final int TYPE_IS_STILL = 25; // 是不是静止点， List<Boolean>
    public static final int TYPE_LOCAL_K_S= 26; // 局部邻域大小, List<Integer>
    public static final int TYPE_VECTOR_3D_LIST= 27; // 三维向量集合，法向量、方向向量...
    public static final int TYPE_BRIDGE_POINTS= 28;
    public static final int TYPE_SPHERE = 29;
    public static final int TYPE_INT_MAP_TO_INT = 30;
    public static final int TYPE_EIGEN_VALUES = 31;
    public static final int TYPE_EIGEN_VECTORS = 32;
    public static final int TYPE_POINT_CLOUD_SKINNED = 34; // 点云的染色,按 branch 进行染色...
    public static final int TYPE_POINT = 35; // 单个Point3d...
    public static final int TYPE_TEST_1 = -1;
    public static final int TYPE_TEST_2 = -2;

    public static final int FORMAT_POINT_CLOUD_LIUJI = 100;
    public static final int FORMAT_POINT_CLOUD_PLY = 101;
    public static final int FORMAT_POINT_CLOUD_ROSA_OFF = 102;
    public static final int FORMAT_POINT_CLOUD_OBJ = 103;
    public static final int FORMAT_SKELETON_ZJL = 113;
    public static final int FORMAT_SKELETON_L1_MEDIAN = 114;
    public static final int FORMAT_SKELETON_PLY = 115;

}
