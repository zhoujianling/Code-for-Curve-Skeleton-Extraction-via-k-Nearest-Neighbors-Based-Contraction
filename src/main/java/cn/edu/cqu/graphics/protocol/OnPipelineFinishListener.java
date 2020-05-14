package cn.edu.cqu.graphics.protocol;

public interface OnPipelineFinishListener {
    void onFinished();
    void onError(Exception exception);
}
