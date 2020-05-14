package cn.edu.cqu.graphics.platform;

import cn.edu.cqu.graphics.config.AlgorithmConfig;
import cn.edu.cqu.graphics.config.PlatformConfig;
import cn.edu.cqu.graphics.protocol.InputData;
import cn.edu.cqu.graphics.protocol.Pipeline;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * 为中间数据生成磁盘cache，cache目录保存在用户目录下，对使用者透明
 * pipeline的名字是第0级目录，
 * 对原始点云数据文件生成md5，用md5做索引，md5是第二级目录
 * 根据skelConfig 再生成一个 hashcode，用 hashcode 做第三层目录。
 * 第三层目录下面，直接讲序列化出来的数据写入到磁盘文件（二进制），不存json
 *
 * 对于程序而言，默认检测有没有cache，有cache直接读cache，（要打log）
 * 没有cache的话，正常生成数据，比如邻接图、图结点索引、骨架结点索引等
 *
 * 这些数据一旦被计算出来，从线程池中取一个线程去缓存数据
 *
 */
@Component
public class PlatformGlobalManager {

    private static final String APP_ROOT_DIR = "ModelProcessEnvironment";
    private static final String PLATFORM_DIR = "Platform";
    private static final String ALGORITHM_DIR = "Algorithm";
    private static final String SNAPSHOT_DIR = "Snapshot";
    private static final String PLATFORM_CONFIG_NAME = "UserPreference.json";
    private static final String OPTIMAL_CONFIG_NAME = "OptimalConfig.json";

    @Autowired
    Logger logger;

    @Autowired
    ExperimentPlatform platform;

    private Kryo kryo = new Kryo();
    private Gson gson = new Gson();
    private JsonParser parser = new JsonParser();
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm", Locale.getDefault());

    public static File detectDirectory(String path, String meaning) throws FileNotFoundException {
        File file = new File(path);
        if (!file.exists()) {
            boolean mkdirSuccess = file.mkdir();
            if (!mkdirSuccess) {
                throw new FileNotFoundException("Cannot create the directory of " + meaning);
            }
        }
        return file;
    }

    private void detectPipelineOptimalConfigDirectory(String pipelineName, InputData originalData, AlgorithmConfig config) throws FileNotFoundException {
        detectDirectory(appDir(), "root of app");
        detectDirectory(algorithmDir(), "algorithm");
        detectDirectory(pipelineDir(pipelineName), "pipeline");
        detectDirectory(pipelineDataDir(pipelineName, originalData), "data(point cloud md5)");

    }

    private void backupPreviousConfig(String dir, String fileName) {
        String currentTime = dateFormat.format(new Date(System.currentTimeMillis()));
        String filePath = dir + File.separator + fileName;
        String newFile = dir + File.separator + currentTime + ".json";
        File previousFile = new File(filePath);
        if (previousFile.exists()) {
            boolean renameResult = previousFile.renameTo(new File(newFile));
            if (!renameResult) {
                logger.warning("Fail to save config。");
            } else {
                logger.info("Fail to save config due a file conficts，old version is renamed to" + newFile + "。");
            }
        }
    }


    private void detectDataCacheDirectory(InputData originalData, AlgorithmConfig config, String pipelineName) throws FileNotFoundException {
        detectDirectory(appDir(), "root of app");
        detectDirectory(algorithmDir(), "algorithm");
        detectDirectory(pipelineDir(pipelineName), "pipeline");
        detectDirectory(pipelineDataDir(pipelineName, originalData), "data(point cloud md5)");
        String configDir = pipelineDataDir(pipelineName, originalData)
                + File.separator
                + md5Now(config.hashCode())
                ;
        detectDirectory(configDir, "config");

    }

    public static void detectPlatformConfigDirectory() throws FileNotFoundException {
        detectDirectory(appDir(), "root of app");
        detectDirectory(platformDir(), "platform");
    }

    private static String appDir() {
        return System.getProperty("user.home")
                + File.separator
                + APP_ROOT_DIR;
    }

    private static String algorithmDir() {
        return appDir()
                + File.separator
                + ALGORITHM_DIR;
    }

    public static String snapshotDir() {
        return appDir()
                + File.separator
                + SNAPSHOT_DIR;
    }

    private static String pipelineDir(String pipelineName) {
        if (pipelineName == null || pipelineName.length() < 1) {
            throw new IllegalArgumentException("Illegal Name!");
        }
        return appDir()
                + File.separator
                + ALGORITHM_DIR
                + File.separator
                + pipelineName;
    }

    private static String pipelineDataDir(String pipelineName, InputData input) {
        return pipelineDir(pipelineName)
                + File.separator
                + input.getMd5()
                ;
    }

    private static String platformDir() {
        return appDir()
                + File.separator
                + PLATFORM_DIR;
    }

    private AlgorithmConfig extractConfig(File configFile, Pipeline pipeline) throws FileNotFoundException {
        AlgorithmConfig config = new AlgorithmConfig();

        JsonReader reader = new JsonReader(new FileReader(configFile));
        JsonElement ele = parser.parse(reader);
        JsonObject object = ele.getAsJsonObject();
        if (object == null || object.isJsonNull()) {
            return null;
        }
        for (String intKey : pipeline.getConfig().getIntegerParams().keySet()) {
            if (object.get(intKey) == null) return null;
            int val = object.get(intKey).getAsInt();
            config.putIntegerParam(intKey, val);
        }
        for (String strKey : pipeline.getConfig().getStringParams().keySet()) {
            if (object.get(strKey) == null) return null;
            String val = object.get(strKey).getAsString();
            config.putStringParam(strKey, val);
        }
        for (String doubleKey : pipeline.getConfig().getDoubleParams().keySet()) {
            if (object.get(doubleKey) == null) return null;
            double val = object.get(doubleKey).getAsDouble();
            config.putDoubleParam(doubleKey, val);
        }
        for (String boolKey : pipeline.getConfig().getBooleanParams().keySet()) {
            if (object.get(boolKey) == null) return null;
            boolean val = object.get(boolKey).getAsBoolean();
            config.putBooleanParam(boolKey, val);
        }
        return config;

    }

    /**
     *
     * @param pointCloud
     * @param pipeline
     * @return 返回一个 AlgorithmConfig 的副本，不去污染现有 Pipeline 里面的 config
     * 外界拿到4个 HashMap 之后，去模拟人的操作去修改 Spinner 和 checkbox 的值，最后修改
     * 实际的算法配置。
     */
    public AlgorithmConfig extractPreviousOptimalConfig(InputData pointCloud, Pipeline pipeline) throws FileNotFoundException {
        if (pointCloud == null) {
            logger.warning("输入数据为空。");
            return null;
        }
        String configDir = pipelineDataDir(pipeline.name(), pointCloud);

        String configPath = configDir
                + File.separator
                + OPTIMAL_CONFIG_NAME
                ;
        File configFile = new File(configPath);
        if (!configFile.exists()) {
            return null;
        }
        return extractConfig(configFile, pipeline);
    }

    public static PlatformConfig extractPlatformConfig() {
        String platformConfigFile = platformDir()
                + File.separator
                + PLATFORM_CONFIG_NAME ;

        File configFile = new File(platformConfigFile);
        if (! configFile.exists()) {
            return null;
        }
        try {
            Gson gson = new Gson();
            PlatformConfig config = gson.fromJson(new FileReader(configFile), PlatformConfig.class);
            return config;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void cachePlatformConfig(PlatformConfig config, Logger logger) {
        try {
            String platformConfigFile = platformDir()
                    + File.separator
                    + PLATFORM_CONFIG_NAME ;

            detectPlatformConfigDirectory();
            FileWriter writer = new FileWriter(platformConfigFile);
            new Gson().toJson(config, writer);
            writer.flush();
            writer.close();
            logger.info("Save platform config successfully。");
        } catch (IOException e) {
            e.printStackTrace();
            logger.warning(e.getMessage());
        }
    }

    public void saveSnapshot(BufferedImage image, String keyword) throws IOException {
        if (keyword == null) keyword = "";
        detectDirectory(appDir(), "app dir");
        File file = detectDirectory(snapshotDir(), "save images");
        SimpleDateFormat format = new SimpleDateFormat("YYYY-MM-dd-HH-mm-ss");
        String time = format.format(new Date());
        String imageName = String.format("%s-%s-Snapshot.png", time, keyword);
        File imageFile = new File(file, imageName);
        ImageIO.write(image, "png", imageFile);
    }

    public void cacheOptimalConfig(InputData pointCloud, Pipeline pipeline) throws IOException {
        if (pointCloud == null) {
            logger.warning("输入数据为空。");
            return;
        }
        detectPipelineOptimalConfigDirectory(pipeline.name(), pointCloud, pipeline.getConfig());
        String configDir = pipelineDataDir(pipeline.name(), pointCloud);

        String configPath = configDir
                + File.separator
                + OPTIMAL_CONFIG_NAME
                ;

        AlgorithmConfig config = pipeline.getConfig();
        JsonObject object = new JsonObject();
        for (String key : config.getIntegerParams().keySet()) {
            object.addProperty(key, config.getIntegerParams().get(key));
        }
        for (String key : config.getBooleanParams().keySet()) {
            object.addProperty(key, config.getBooleanParams().get(key));
        }
        for (String key : config.getDoubleParams().keySet()) {
            object.addProperty(key, config.getDoubleParams().get(key));
        }
        for (String key : config.getStringParams().keySet()) {
            object.addProperty(key, config.getStringParams().get(key));
        }
        backupPreviousConfig(configDir, OPTIMAL_CONFIG_NAME);
        FileWriter writer = new FileWriter(configPath);
        gson.toJson(object, writer);
        writer.close();
        logger.info("Current user preference has been saved.");
    }

    public Object readCache(
                          InputData pointCloud,
                          Pipeline pipeline,
                          String signature,
                          Class dataType)
            throws IOException, ClassNotFoundException {
        String path = pipelineDataDir(pipeline.name(), pointCloud)
                + File.separator
                + md5Now(pipeline.getConfig().hashCode())
                + File.separator
                + signature
                + ".cache"
                ;
        FileInputStream stream = new FileInputStream(path);

//        Serializable energy = (Serializable) gson.readValurFromJson(new FileReader(path), dataType);

        Input input = new Input(stream);
        Serializable obj = (Serializable) kryo.readClassAndObject(input);
        input.close();

//        ObjectInputStream objectStream = new ObjectInputStream(stream);
//        Serializable energy = (Serializable) objectStream.readObject();

        if (dataType.isInstance(obj)) {
            logger.info("Read cache successfully.");
            return obj;
        } else {
            throw new FileNotFoundException("类型不对, " + dataType.getName());
        }
    }

    public void makeCache(Pipeline pipeline, InputData pointCloud,
                          Object data, String signature) throws IOException {
        detectDataCacheDirectory(pointCloud, pipeline.getConfig(), pipeline.name());
        String path = pipelineDataDir(pipeline.name(), pointCloud)
                + File.separator
                + md5Now(pipeline.getConfig().hashCode())
                + File.separator
                + signature
                + ".cache"
                ;
        logger.info("Cache path: " + path);
        FileOutputStream stream = new FileOutputStream(path);

//        FileWriter writer = new FileWriter(path);
//        gson.toJson(data, writer);
//        writer.close();

//        BufferedWriter writer = new BufferedWriter(new FileWriter(path));
//        writer.write(json);

        Output output = new Output(stream);
        kryo.writeClassAndObject(output, data);
        output.close();

//        ObjectOutputStream objectStream = new ObjectOutputStream(stream);
//        objectStream.writeObject(data);
//        objectStream.close();
        logger.info("Write cache successfully.");
    }

    public static void clearAlgorithmCache() throws IOException {
        String algorithmDir = algorithmDir();
        File file = new File(algorithmDir);
        deleteFileRecursively(file);
    }

    private static void deleteFileRecursively(File file) throws IOException {
        if (! file.exists()) return;
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files == null) files = new File[] {};
            for (File subFile : files) {
                try {
                    deleteFileRecursively(subFile);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (! file.delete()) throw new IOException("Cannot delete: " + file.getAbsolutePath());
        }
        if (! file.delete()) throw new IOException("Cannot delete: " + file.getAbsolutePath());
    }

    private String md5Now(long data) {
        return DigestUtils.md5DigestAsHex(String.valueOf(data).getBytes()).substring(0, 7);
    }

    private String md5Now(String data) {
        return DigestUtils.md5DigestAsHex(data.getBytes()).substring(0, 7);
    }

}
