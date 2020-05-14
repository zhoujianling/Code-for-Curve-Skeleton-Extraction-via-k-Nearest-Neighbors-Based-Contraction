package cn.edu.cqu.graphics.config;

import cn.edu.cqu.graphics.model.Color;
import com.google.gson.Gson;
import org.springframework.core.io.ClassPathResource;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class FissionConfig {
    private Long showFissionIndex = 0L;
    private double geodesicRatio;
    private Color l1Color;
    private Color l1VarColor;
    private String type;

    public static FissionConfig fromResource(String resName) throws FileNotFoundException {
        ClassPathResource resource = new ClassPathResource("config/" + resName);
        FissionConfig config = null;
        try {
            Gson gson = new Gson();
            config = gson.fromJson(new InputStreamReader(resource.getInputStream()), FissionConfig.class);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return config;
    }

    public static FissionConfig fromJson(String fileName) throws FileNotFoundException {
        FileReader reader = new FileReader(fileName);
        Gson gson = new Gson();
        return gson.fromJson(reader, FissionConfig.class);
    }

    public Long getShowFissionIndex() {
        return showFissionIndex;
    }

    public void setShowFissionIndex(Long showFissionIndex) {
        this.showFissionIndex = showFissionIndex;
    }

    public Color getL1Color() {
        return l1Color;
    }

    public void setL1Color(Color l1Color) {
        this.l1Color = l1Color;
    }

    public Color getL1VarColor() {
        return l1VarColor;
    }

    public void setL1VarColor(Color l1VarColor) {
        this.l1VarColor = l1VarColor;
    }

    public String getType() {
        return type;
    }

    public void setType(String s) {this.type = s;}

    public double getGeodesicRatio() {
        return geodesicRatio;
    }

    public void setGeodesicRatio(double geodesicRatio) {
        this.geodesicRatio = geodesicRatio;
    }
}
