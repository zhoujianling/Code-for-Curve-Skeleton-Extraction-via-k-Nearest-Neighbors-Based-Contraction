package cn.edu.cqu.graphics.config;


import cn.edu.cqu.graphics.model.Color;
import com.google.gson.Gson;
import org.springframework.core.io.ClassPathResource;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class UiSkeletonConfig {
    private Color skeletonNodeColor;
    private float skeletonNodeSize;
    private float pointCloudSize;
    private float skeletonCurveWidth;
    private Color skeletonCurveColor;
    private String pointCloudColorMode;
    private Color pointCloudColor;
    private Color guessPointColor;
    private float guessPointSize;

    private boolean showGeodesicGraph;
    private boolean showNeighborhoodGraph;
    private boolean showLevelSetGraph;
    private boolean showPointCloud;
    private boolean showSkeletonCurve;
    private boolean showGuessPoint;

    private UiSkeletonConfig() {
    }

    public static UiSkeletonConfig fromResource(String resName) throws FileNotFoundException {
        ClassPathResource resource = new ClassPathResource("config/" + resName);
//        ClassPathResource resource = new ClassPathResource("icon.png");
        UiSkeletonConfig config = null;
        try {
            Gson gson = new Gson();
            config = gson.fromJson(new InputStreamReader(resource.getInputStream()), UiSkeletonConfig.class);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return config;
    }

    public static UiSkeletonConfig fromJson(String fileName) throws FileNotFoundException {
        FileReader reader = new FileReader(fileName);
        Gson gson = new Gson();
        return gson.fromJson(reader, UiSkeletonConfig.class);
    }

    public Color getSkeletonNodeColor() {
        return skeletonNodeColor;
    }

    public void setSkeletonNodeColor(Color c) {
        this.skeletonNodeColor = c;
    }

    public float getSkeletonNodeSize() {
        return skeletonNodeSize;
    }

    public void setSkeletonNodeSize(float skeletonNodeSize) {
        this.skeletonNodeSize = skeletonNodeSize;
    }

    public float getSkeletonCurveWidth() {
        return skeletonCurveWidth;
    }

    public void setSkeletonCurveWidth(float skeletonCurveWidth) {
        this.skeletonCurveWidth = skeletonCurveWidth;
    }

    public String getPointCloudColorMode() {
        return pointCloudColorMode;
    }

    public void setPointCloudColorMode(String pointCloudColorMode) {
        this.pointCloudColorMode = pointCloudColorMode;
    }

    public Color getPointCloudColor() {
        return pointCloudColor;
    }

    public void setPointCloudColor(Color pointCloudColor) {
        this.pointCloudColor = pointCloudColor;
    }

    public Color getSkeletonCurveColor() {
        return skeletonCurveColor;
    }

    public void setSkeletonCurveColor(Color skeletonCurveColor) {
        this.skeletonCurveColor = skeletonCurveColor;
    }

    public boolean isShowGeodesicGraph() {
        return showGeodesicGraph;
    }

    public void setShowGeodesicGraph(boolean showGeodesicGraph) {
        this.showGeodesicGraph = showGeodesicGraph;
    }

    public boolean isShowNeighborhoodGraph() {
        return showNeighborhoodGraph;
    }

    public void setShowNeighborhoodGraph(boolean showNeighborhoodGraph) {
        this.showNeighborhoodGraph = showNeighborhoodGraph;
    }

    public boolean isShowPointCloud() {
        return showPointCloud;
    }

    public void setShowPointCloud(boolean showPointCloud) {
        this.showPointCloud = showPointCloud;
    }

    public boolean isShowSkeletonCurve() {
        return showSkeletonCurve;
    }

    public void setShowSkeletonCurve(boolean showSkeletonCurve) {
        this.showSkeletonCurve = showSkeletonCurve;
    }

    public boolean isShowGuessPoint() {
        return showGuessPoint;
    }

    public void setShowGuessPoint(boolean showGuessPoint) {
        this.showGuessPoint = showGuessPoint;
    }

    public float getPointCloudSize() {
        return pointCloudSize;
    }

    public void setPointCloudSize(float pointCloudSize) {
        this.pointCloudSize = pointCloudSize;
    }

    public Color getGuessPointColor() {
        return guessPointColor;
    }

    public void setGuessPointColor(Color guessPointColor) {
        this.guessPointColor = guessPointColor;
    }

    public float getGuessPointSize() {
        return guessPointSize;
    }

    public void setGuessPointSize(float guessPointSize) {
        this.guessPointSize = guessPointSize;
    }

    public boolean isShowLevelSetGraph() {
        return showLevelSetGraph;
    }

    public void setShowLevelSetGraph(boolean showLevelSetGraph) {
        this.showLevelSetGraph = showLevelSetGraph;
    }
}
