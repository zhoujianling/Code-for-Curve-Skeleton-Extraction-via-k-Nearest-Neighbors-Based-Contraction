package cn.edu.cqu.graphics.util;


import javax.vecmath.Matrix4d;
import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class MathUtils {

	public static double mid(double x, double y, double z) {
	    if (x > y && y > z) return y;
		if (z > y && y > x) return y;
		if (y > z && z > x) return z;
		if (x > z && z > y) return z;
		if (y > x && x > z) return x;
		if (z > x && x > y) return x;
		return x;
	}


	public static ArrayList<Double> matrix2List(Matrix4d m) {
		ArrayList<Double> l = new ArrayList<>();
		l.add(m.m00); l.add(m.m01); l.add(m.m02); l.add(m.m03);
		l.add(m.m10); l.add(m.m11); l.add(m.m12); l.add(m.m13);
		l.add(m.m20); l.add(m.m21); l.add(m.m22); l.add(m.m23);
		l.add(m.m30); l.add(m.m31); l.add(m.m32); l.add(m.m33);
		return l;
	}

	public static Matrix4d list2Matrix(List<Double> list) {
		double[] vals = new double[16];
		Matrix4d m = new Matrix4d();
		for (int i = 0; i < 16; i ++) vals[i] = list.get(i);
		m.set(vals);
		return m;
	}

	public static void shuffleArray(int[] ar)
	{
		// If running on Java 6 or older, use `new Random()` on RHS here
		Random rnd = ThreadLocalRandom.current();
		for (int i = ar.length - 1; i > 0; i--)
		{
			int index = rnd.nextInt(i + 1);
			// Simple swap
			int a = ar[index];
			ar[index] = ar[i];
			ar[i] = a;
		}
	}

	public static double max(double ...args)
	{
		Arrays.sort(args);
		return args[args.length-1];
	}
	public static double min(double ...args) {
		Arrays.sort(args);
		return args[0];
	}

	public static Point3d add(Point3d a, Point3d b) {return new Point3d(a.x + b.x, a.y + b.y, a.z + b.z);}

	public static Point3d minus(Point3d a, Point3d b) {
		return new Point3d(a.x - b.x, a.y - b.y, a.z - b.z);
	}

	public static Vector3d minusd(Point3d a, Point3d b) {
		return new Vector3d(b.x - a.x, b.y - a.y, b.z - a.z);
	}

	public static Point3d divideBy(Point3d a, float b) {a.x /= b; a.y /= b; a.z /= b; return a;}

	public static double[] vector(Point3d center) {
		double[] vec = new double[3];
		vec[0] = center.getX();
		vec[1] = center.getY();
		vec[2] = center.getZ();
		return vec;
	}

	/**
	 * 向量叉积
	 * @param a
	 * @param b
	 * @return
	 */
	public static Point3d vectorCross(Point3d a, Point3d b) {
		return new Point3d(
				a.y * b.z - a.z * b.y,
				a.z * b.x - a.x * b.z,
				a.x * b.y - a.y * b.x
		);
	}

	public static Vector3d cross(Vector3d a, Vector3d b) {
		return new Vector3d(
				a.y * b.z - a.z * b.y,
				a.z * b.x - a.x * b.z,
				a.x * b.y - a.y * b.x
		);
	}

	/**
	 * 向量的模
	 * @param a
	 * @return
	 */
	public static double vectorNorm(Point3d a) {
		return Math.sqrt(a.x * a.x + a.y * a.y + a.z * a.z);
	}

	public static double distance(Point3d p, Point3d a, Point3d b) {
		return MathUtils.vectorNorm(
			MathUtils.vectorCross(
				MathUtils.minus(b, a),
					MathUtils.minus(a, p)
			)) / MathUtils.vectorNorm(MathUtils.minus(b, a))
		;
	}

	public static double distance(Point3d a, Point3d b) {
		return a.distance(b);
//		return normd(new Vector3d(a.x - b.x, a.y - b.y, a.z - b.z));
	}

	public static double normd(Vector3d vec) {
		return Math.sqrt(vec.x * vec.x + vec.y * vec.y + vec.z * vec.z);
	}


	/**
	 * @return 返回 n1 n2 夹角余弦
	 */
	public static double cos(Vector3d n1, Vector3d n2) {
		return (n1.x * n2.x + n1.y * n2.y + n1.z * n2.z) / normd(n1) / normd(n2);
	}

	/**
	 * @return 返回 n1 n2 夹角正弦
	 */
	public static double sin(Vector3d n1, Vector3d n2) {
		return normd(cross(n1, n2)) / normd(n1) / normd(n2);
	}


	public static void main(String []args) {
//		Point3d a = new Point3d(1, 1, 1);
//		Point3d b = new Point3d(0, 0, 0);
//		Point3d p = new Point3d(0, 0, 1);
//
//		double distance //点到线的距离公式
//				= MathUtils.vectorNorm(
//				MathUtils.vectorCross(
//						MathUtils.minus(b, a),
//						MathUtils.minus(a, p))
//					)
//				/ MathUtils.vectorNorm(MathUtils.minus(b, a));
//		System.out.println("dis: " + distance);

//		Set<Double> set = new HashSet<>();
//		set.add(0.0);
//		set.add(1.0);
//		set.add(3.0);
//		set.add(4.0);
//		set.add(5.0);
//		set.add(6.0);
//		set.add(7.0);
//		set.add(8.0);
//		set.add(9.0);
//		set.add(8.0);
//		set.add(10.0);
//		set.add(9.3);
//		set.add(8.9);
//		set.add(7.6);
//		set.add(7.6);
//		set.add(7.6);
//		double dis = ReverseClusterer.histogram(set);
//		System.out.println("mean dis" + dis);

		Point3d a = new Point3d(0.0, 0.0, 0.0);
		Point3d b = new Point3d(1.0, 0.0, 0.0);
		Point3d c = new Point3d(1.0, 1.0, 0.0);
		Point3d d = new Point3d(0.5, 0.0, 0.0);

		Point3d[] aa = new Point3d[400];
		for (int i = 0; i < 100; i++) {
			aa[i * 4 + 0] = a;
			aa[i * 4 + 1] = b;
			aa[i * 4 + 2] = c;
			aa[i * 4 + 3] = d;
		}

		Point3d[] array = new Point3d[] {a, b, c};
		Set<Point3d> set = new HashSet<>();
		set.add(a);
		set.add(b);
		set.add(c);

//		Point3d centroid = ritterCenter(set);
//		System.out.println(String.format("%.3f, %.3f, %.3f", centroid.x, centroid.y, centroid.z));


//		BoundingSphere sphere = new BoundingSphere(d, 0.1);
//		sphere.combine(array);
//		System.out.println("radius: " + sphere.getRadius());
//		Point3d centroid = new Point3d(0, 0, 0);
//		sphere.getPosition(centroid);
//		System.out.println(String.format("%.3f, %.3f, %.3f", centroid.x, centroid.y, centroid.z));
	}

}
