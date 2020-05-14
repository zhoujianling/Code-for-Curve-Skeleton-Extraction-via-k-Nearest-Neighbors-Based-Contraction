package cn.edu.cqu.graphics.protocol;

public enum SkeletonFormat {

    SKEL_ZJL(0, "skelz", "LVP代码导出的骨架"),
    SKEL_L1_MEDIAN(1, "skel", "L1-Median的导出骨架"),
    SKEL_ROSA(2, "cg", "ROSA的导出骨架");

    private int val = -1;

    private String suffix = ".undefined";

    private String comment = "未定义";

    SkeletonFormat(int val, String suffix, String comment) {
        this.val = val;
        this.suffix = suffix;
        this.comment = comment;
    }

    public int getVal() {
        return val;
    }

    public String getSuffix() {
        return suffix;
    }

    public String getComment() {
        return comment;
    }

}
