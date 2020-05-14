package cn.edu.cqu.graphics.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;

public class AlgorithmConfig {

    private HashMap<String, String> stringParams = new HashMap<>();
    private HashMap<String, Boolean> booleanParams = new HashMap<>();
    private HashMap<String, Integer> integerParams = new HashMap<>();
    private HashMap<String, Double> doubleParams = new HashMap<>();


    public void readValueFromJson(String jsonPath) throws FileNotFoundException {
        JsonParser parser = new JsonParser();
        JsonObject object = parser.parse(new JsonReader(new FileReader(jsonPath))).getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            JsonElement element = entry.getValue();

        }
    }

    public void putStringParam(String key, String val) {
        stringParams.put(key, val);
    }

    public String getStringParam(String key, String defaultVal) {
        return stringParams.getOrDefault(key, defaultVal);
    }

    public void putIntegerParam(String key, Integer val) {
        integerParams.put(key, val);
    }

    public Integer getIntegerParam(String key, Integer defaultVal) {
        return integerParams.getOrDefault(key, defaultVal);
    }

    public Integer getIntegerParam(String key) {
        return integerParams.get(key);
    }

    public void putDoubleParam(String key, Double val) {
        doubleParams.put(key, val);
    }

    public Double getDoubleParam(String key, Double defaultVal) {
        return doubleParams.getOrDefault(key, defaultVal);
    }

    public Double getDoubleParam(String key) {
        return doubleParams.get(key);
    }

    public void putBooleanParam(String key, Boolean val) {
        booleanParams.put(key, val);
    }

    public Boolean getBooleanParam(String key, Boolean defaultVal) {
        return booleanParams.getOrDefault(key, defaultVal);
    }

    public Boolean getBooleanParam(String key) {
        return booleanParams.get(key);
    }


    public HashMap<String, String> getStringParams() {
        return stringParams;
    }

    public HashMap<String, Boolean> getBooleanParams() {
        return booleanParams;
    }

    public HashMap<String, Integer> getIntegerParams() {
        return integerParams;
    }

    public HashMap<String, Double> getDoubleParams() {
        return doubleParams;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AlgorithmConfig that = (AlgorithmConfig) o;

        if (!stringParams.equals(that.stringParams)) return false;
        if (!booleanParams.equals(that.booleanParams)) return false;
        if (!integerParams.equals(that.integerParams)) return false;
        return doubleParams.equals(that.doubleParams);
    }

    @Override
    public int hashCode() {
        int result = stringParams.hashCode();
        result = 31 * result + booleanParams.hashCode();
        result = 31 * result + integerParams.hashCode();
        result = 31 * result + doubleParams.hashCode();
        return result;
    }
}
