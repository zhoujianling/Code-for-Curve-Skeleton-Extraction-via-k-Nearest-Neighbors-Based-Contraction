package cn.edu.cqu.graphics.config;

import cn.edu.cqu.graphics.platform.PlatformGlobalManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.io.FileNotFoundException;
import java.util.Locale;
import java.util.logging.Logger;

@Configuration
@ComponentScan(basePackages = {"cn.edu.cqu"})
public class SpringBaseConfig {


    @Bean
    public UiSkeletonConfig uiSkelConfig() throws FileNotFoundException {
        return UiSkeletonConfig.fromResource("ui_skeleton_config.json");
    }

    @Bean
    public UiTreeConfig uiTreeConfig() throws FileNotFoundException {
        return UiTreeConfig.fromResource("ui_tree_config.json");
    }

    @Bean
    public PlatformConfig platformConfig() throws FileNotFoundException {
        PlatformConfig config = PlatformGlobalManager.extractPlatformConfig();
        if (config == null) config = new PlatformConfig();
        return config;
    }

    @Bean
    public FissionConfig fissonConfig() throws FileNotFoundException {
        return FissionConfig.fromResource("ui_fission_config.json");
    }

    @Bean
    public Logger logger() {
//        Logger.getAnonymousLogger().addHandler();
        Locale.setDefault(Locale.CANADA);
        Logger logger = Logger.getAnonymousLogger();
        return logger;
    }



}
