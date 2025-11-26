package hara.lib.block;

import java.util.List;

public interface IBlock {
    String type();
    String tag();
    String string();
    int length();
    int width();
    int height();
    int prefixed();
    int suffixed();
    boolean verify();

    interface IBlockModifier {
        Object modify(Object accumulator, Object input);
    }

    interface IBlockExpression {
        Object value();
        String valueString();
    }

    interface IBlockContainer {
        List<IBlock> children();
        IBlockContainer replaceChildren(List<IBlock> children);
    }
}
