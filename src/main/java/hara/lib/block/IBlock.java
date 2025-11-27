package hara.lib.block;

import hara.lang.data.Vector;

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
        Vector<IBlock> children();
        IBlockContainer replaceChildren(Vector<IBlock> children);
    }
}
