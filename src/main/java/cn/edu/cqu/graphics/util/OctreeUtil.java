package cn.edu.cqu.graphics.util;

import java.util.Vector;

/**
 * Created by zjl on 2017/7/7.
 * 3bit   最左边（高位），表示x方向的，中间的一位表示 y 方向
 */
public class OctreeUtil {


    public static int[] index2LeftTop(Long index, int OCTREE_LEVEL) {
        int []coord = new int[3];//x y z
        coord[0] = 0; coord[1] = 0; coord[2] = 0;
        for (int i = 1; i < OCTREE_LEVEL; i++) {
            Long temp = index & 7L;
            coord[0] += ((temp >> 2) & 1L) * (0x1 << (OCTREE_LEVEL - i));
            coord[1] += ((temp >> 1) & 1L) * (0x1 << (OCTREE_LEVEL - i));
            coord[2] += ((temp >> 0) & 1L) * (0x1 << (OCTREE_LEVEL - i));
            index >>= 3;
        }
        coord[0] += ((index >> 2) & 1L);
        coord[1] += ((index >> 1) & 1L);
        coord[2] += ((index >> 0) & 1L);
        return coord;
    }

    public static Long leftTop2Index(int []coord, int OCTREE_LEVEL) {
        assert coord.length >= 3;
        Long index = 0L;
        index |= ((coord[0] & 1) << 2);
        index |= ((coord[1] & 1) << 1);
        index |= ((coord[2] & 1) << 0);
        for (int i = 1; i < OCTREE_LEVEL; i++) {
            index <<= 3;
            int xt = coord[0] >> (i);
            int yt = coord[1] >> (i);
            int zt = coord[2] >> (i);
            xt &= 1; yt &= 1; zt &= 1;
            xt <<= 2; yt <<= 1; zt <<= 0;
            index |= xt; index |= yt; index |= zt;
        }
        return index;
    }

    public static Vector<Long> obtainAdjacents6(Long index, int OCTREE_LEVEL) {
        Vector<Long> result = new Vector<>();
        int []coord =  index2LeftTop(index, OCTREE_LEVEL);
        int []coord1 = new int[] {coord[0] - 1, coord[1], coord[2]};
        int []coord2 = new int[] {coord[0] + 1, coord[1], coord[2]};
        int []coord3 = new int[] {coord[0], coord[1] - 1, coord[2]};
        int []coord4 = new int[] {coord[0], coord[1] + 1, coord[2]};
        int []coord5 = new int[] {coord[0], coord[1], coord[2] - 1};
        int []coord6 = new int[] {coord[0], coord[1], coord[2] + 1};
        result.add(leftTop2Index(coord1, OCTREE_LEVEL));
        result.add(leftTop2Index(coord2, OCTREE_LEVEL));
        result.add(leftTop2Index(coord3, OCTREE_LEVEL));
        result.add(leftTop2Index(coord4, OCTREE_LEVEL));
        result.add(leftTop2Index(coord5, OCTREE_LEVEL));
        result.add(leftTop2Index(coord6, OCTREE_LEVEL));
        return result;
    }

    public static Vector<Long> obtainAdjacents26(Long index, int OCTREE_LEVEL) {
        Vector<Long> result = new Vector<>();
        int []coord = index2LeftTop(index, OCTREE_LEVEL);
        for (int i : new int[] {-1, 0, 1}) {
            for (int j : new int[] {-1, 0, 1}) {
                for (int k : new int[] {-1, 0, 1}) {
                    if (i == 0 && j == 0 && k == 0) continue;
                    if (coord[0] == 0 && i < 0) continue;
                    if (coord[1] == 0 && j < 0) continue;
                    if (coord[2] == 0 && k < 0) continue;
                    if (coord[0] == Math.pow(2, OCTREE_LEVEL) - 1 && i > 0) continue;
                    if (coord[1] == Math.pow(2, OCTREE_LEVEL) - 1 && j > 0) continue;
                    if (coord[2] == Math.pow(2, OCTREE_LEVEL) - 1 && k > 0) continue;

                    int []newCoord = new int [] {coord[0] + i, coord[1] + j, coord[2] + k};
                    if (validCoord(newCoord))
                        result.add(leftTop2Index(newCoord, OCTREE_LEVEL));
                }
            }
        }

        return result;
    }

    public static boolean validCoord(int []coord) {
        if (coord.length < 3) return false;
        return coord[0] >= 0 && coord[1] >= 1 && coord[2] >= 2;//???
    }
}
