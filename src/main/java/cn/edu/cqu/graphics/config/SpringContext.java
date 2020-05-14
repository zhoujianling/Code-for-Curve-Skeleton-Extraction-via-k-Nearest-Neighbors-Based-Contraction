package cn.edu.cqu.graphics.config;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class SpringContext extends AnnotationConfigApplicationContext {

    private static volatile SpringContext instance = null;

    private SpringContext() {
        super(SpringBaseConfig.class);

    }

    public static SpringContext instance() {
        if (instance == null) {
            synchronized (SpringContext.class) {
                if (instance == null) {
                    instance = new SpringContext();
                }
            }
        }
        return instance;
    }

}
