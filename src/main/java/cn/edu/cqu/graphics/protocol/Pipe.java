package cn.edu.cqu.graphics.protocol;

import java.io.FileNotFoundException;

public interface Pipe {
    String getName();

    void run() throws FileNotFoundException;

    void before();
    void apply() throws FileNotFoundException;
    void after();

}
