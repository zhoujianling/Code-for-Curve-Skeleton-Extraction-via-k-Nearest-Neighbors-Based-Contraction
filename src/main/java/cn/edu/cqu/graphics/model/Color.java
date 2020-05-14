package cn.edu.cqu.graphics.model;

public class Color {
    public int r;
    public int g;
    public int b;

    public Color(int r, int g, int b) {
        this.r = r % 256;
        this.g = g % 256;
        this.b = b % 256;
    }
}
