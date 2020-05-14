package cn.edu.cqu.graphics.config;

import cn.edu.cqu.graphics.model.Color;
import com.google.gson.Gson;
import org.springframework.core.io.ClassPathResource;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class UiTreeConfig {
    private float surfaceTransparency;
    private boolean showCircle;
    private boolean showPointCloud;
    private boolean useTexture;
    private boolean showSkeleton;
    private Color circleColor;
    private float circleWidth;
    private float rootRadius;

    private UiTreeConfig() {}

    public static UiTreeConfig fromResource(String resName) throws FileNotFoundException {
        ClassPathResource resource = new ClassPathResource("config/" + resName);
        UiTreeConfig config = null;
        try {
            Gson gson = new Gson();
            config = gson.fromJson(new InputStreamReader(resource.getInputStream()), UiTreeConfig.class);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return config;
    }

    public static UiTreeConfig fromJson(String fileName) throws FileNotFoundException {
        FileReader reader = new FileReader(fileName);
        Gson gson = new Gson();
        return gson.fromJson(reader, UiTreeConfig.class);
    }

    public float getSurfaceTransparency() {
        return surfaceTransparency;
    }

    public void setSurfaceTransparency(float surfaceTransparency) {
        this.surfaceTransparency = surfaceTransparency;
    }

    public boolean isShowCircle() {
        return showCircle;
    }

    public void setShowCircle(boolean showCircle) {
        this.showCircle = showCircle;
    }

    public boolean isUseTexture() {
        return useTexture;
    }

    public void setUseTexture(boolean useTexture) {
        this.useTexture = useTexture;
    }

    public boolean isShowSkeleton() {
        return showSkeleton;
    }

    public void setShowSkeleton(boolean showSkeleton) {
        this.showSkeleton = showSkeleton;
    }

    public boolean isShowPointCloud() {
        return showPointCloud;
    }

    public void setShowPointCloud(boolean showPointCloud) {
        this.showPointCloud = showPointCloud;
    }

    public Color getCircleColor() {
        return circleColor;
    }

    public void setCircleColor(Color circleColor) {
        this.circleColor = circleColor;
    }

    public float getCircleWidth() {
        return circleWidth;
    }

    public void setCircleWidth(float circleWidth) {
        this.circleWidth = circleWidth;
    }

    public float getRootRadius() {
        return rootRadius;
    }

    public void setRootRadius(float f) {
        this.rootRadius = f;
    }
}



