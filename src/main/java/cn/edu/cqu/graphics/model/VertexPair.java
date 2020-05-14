package cn.edu.cqu.graphics.model;

/**
 * Created by zjl on 2017/6/24.
 *
 */
public class VertexPair {
    Long indexA;
    Long indexB;

    public VertexPair(Long a, Long b) {
        this.indexA = a;
        this.indexB = b;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        VertexPair that = (VertexPair) o;

        if (indexA != null ? !indexA.equals(that.indexA) : that.indexA != null) return false;
        return indexB != null ? indexB.equals(that.indexB) : that.indexB == null;

    }

    @Override
    public int hashCode() {
        int result = indexA != null ? indexA.hashCode() : 0;
        result = 31 * result + (indexB != null ? indexB.hashCode() : 0);
        return result;
    }

    public Long getIndexA() {
        return this.indexA;
    }

    public Long getIndexB() {
        return this.indexB;
    }
}
