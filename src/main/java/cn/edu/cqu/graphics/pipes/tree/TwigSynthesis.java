package cn.edu.cqu.graphics.pipes.tree;

import cn.edu.cqu.graphics.protocol.CachedPipe;
import org.springframework.stereotype.Component;

@Component
public class TwigSynthesis extends CachedPipe {



    public TwigSynthesis() {}

    private void synthesized() {

    }

    @Override
    public String getName() {
        return "细微树枝合成";
    }

    @Override
    public void apply() {
        synthesized();
    }
}
