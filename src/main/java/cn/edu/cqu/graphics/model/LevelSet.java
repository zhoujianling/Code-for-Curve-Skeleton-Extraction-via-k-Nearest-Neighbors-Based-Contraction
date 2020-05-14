package cn.edu.cqu.graphics.model;

import cn.edu.cqu.graphics.model.tree.TreeSkeletonNode;

import java.io.Serializable;
import java.util.Vector;

public class LevelSet implements Serializable {
    private  Vector<TreeSkeletonNode> levelSets = new Vector<>();
    private Vector<Vector<TreeSkeletonNode>> result = new Vector<>();

    public Vector<TreeSkeletonNode> getLevelSets() {
        return levelSets;
    }

    public void setLevelSets(Vector<TreeSkeletonNode> levelSets) {
        this.levelSets = levelSets;
    }

    public Vector<Vector<TreeSkeletonNode>> getResult() {
        return result;
    }

    public void setResult(Vector<Vector<TreeSkeletonNode>> result) {
        this.result = result;
    }
}
