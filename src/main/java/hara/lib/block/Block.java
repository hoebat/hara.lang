package hara.lib.block;

import hara.lang.data.Vector;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class Block {

    public static final int TAB_WIDTH = 4;

    // Helper from std.block.check
    private static boolean isComment(String s) {
        return s != null && s.startsWith(";");
    }

    // Helper from std.block.check
    private static String voidTag(Character c) {
        if (c == null) return "eof";
        if (c == '\n' || c == '\r' || c == '\f') return "linebreak";
        if (c == ' ' || c == '\t') return "linespace";
        return null;
    }

    public static class Void implements IBlock, Comparable<IBlock> {
        public final String tag;
        public final Character character;
        public final int width;
        public final int height;

        public Void(String tag, Character character, int width, int height) {
            this.tag = tag;
            this.character = character;
            this.width = width;
            this.height = height;
        }

        @Override public String type() { return "void"; }
        @Override public String tag() { return this.tag; }
        @Override public String string() { return String.valueOf(this.character); }
        @Override public int length() { return 1; }
        @Override public int width() { return this.width; }
        @Override public int height() { return this.height; }
        @Override public int prefixed() { return 0; }
        @Override public int suffixed() { return 0; }
        @Override public boolean verify() {
            return this.tag != null && this.tag.equals(voidTag(this.character));
        }

        @Override
        public int compareTo(IBlock other) {
            return Block.compare(this, other);
        }

        @Override
        public String toString() {
            if (character == ' ') return "␣";
            if (character == '\n') return "\\n";
            if (character == '\t') return "\\t";
            return String.valueOf(character);
        }
    }

    public static class Comment implements IBlock, Comparable<IBlock> {
        public final String string;
        public final int width;

        public Comment(String string) {
            this.string = string;
            this.width = string.length();
        }

        @Override public String type() { return "comment"; }
        @Override public String tag() { return "comment"; }
        @Override public String string() { return this.string; }
        @Override public int length() { return this.width; }
        @Override public int width() { return this.width; }
        @Override public int height() { return 0; }
        @Override public int prefixed() { return 0; }
        @Override public int suffixed() { return 0; }
        @Override public boolean verify() { return isComment(this.string); }

        @Override
        public int compareTo(IBlock other) {
            return Block.compare(this, other);
        }

        @Override
        public String toString() {
            return this.string;
        }
    }

    public static int compare(IBlock a, IBlock b) {
        int tagCompare = a.tag().compareTo(b.tag());
        if (tagCompare != 0) {
            return tagCompare;
        }
        return a.string().compareTo(b.string());
    }

    private static String tokenTag(Object value) {
        if (value instanceof String) return "string";
        if (value instanceof Number) return "number";
        if (value instanceof Boolean) return "boolean";
        if (value instanceof Character) return "character";
        return "object";
    }

    public static class Token implements IBlock, IBlock.IBlockExpression, Comparable<IBlock> {
        public final String tag;
        public final String string;
        public final Object value;
        public final String valueString;
        public final int width;
        public final int height;

        public Token(String tag, String string, Object value, String valueString, int width, int height) {
            this.tag = tag;
            this.string = string;
            this.value = value;
            this.valueString = valueString;
            this.width = width;
            this.height = height;
        }

        @Override public String type() { return "token"; }
        @Override public String tag() { return this.tag; }
        @Override public String string() { return this.string; }
        @Override public int length() { return this.string.length(); }
        @Override public int width() { return this.width; }
        @Override public int height() { return this.height; }
        @Override public int prefixed() { return 0; }
        @Override public int suffixed() { return 0; }
        @Override public boolean verify() {
            return this.tag != null && this.tag.equals(tokenTag(this.value));
        }

        @Override public Object value() { return this.value; }
        @Override public String valueString() { return this.valueString; }

        @Override
        public int compareTo(IBlock other) {
            return Block.compare(this, other);
        }

        @Override
        public String toString() {
            return Objects.toString(this.value);
        }
    }

    public static class Container implements IBlock, IBlock.IBlockExpression, IBlock.IBlockContainer, Comparable<IBlock> {
        public final String tag;
        public final Vector<IBlock> children;
        public final Props props;

        public static class Props {
            public final String start;
            public final String end;
            public Props(String start, String end) {
                this.start = start;
                this.end = end;
            }
        }

        public Container(String tag, Vector<IBlock> children, Props props) {
            this.tag = tag;
            this.children = children;
            this.props = props;
        }

        @Override public String type() { return "collection"; }
        @Override public String tag() { return this.tag; }
        @Override public int prefixed() { return this.props.start.length(); }
        @Override public int suffixed() { return this.props.end.length(); }
        @Override public Vector<IBlock> children() { return this.children; }

        @Override
        public IBlock.IBlockContainer replaceChildren(Vector<IBlock> newChildren) {
            return new Container(this.tag, newChildren, this.props);
        }

        @Override public boolean verify() { return true; }
        @Override public Object value() { return null; }
        @Override public String valueString() { return containerValueString(this); }
        @Override public String string() { return containerString(this); }
        @Override public int length() { return string().length(); }
        @Override public int width() { return containerWidth(this); }
        @Override public int height() { return containerHeight(this); }

        @Override
        public int compareTo(IBlock other) {
            return Block.compare(this, other);
        }

        @Override
        public String toString() {
            return string();
        }
    }

    private static int containerWidth(Container block) {
        int lastLineWidth = 0;
        boolean onLastLine = true;
        Vector<IBlock> children = block.children();
        for (int i = (int) (children.count() - 1); i >= 0; i--) {
            IBlock child = children.nth(i);
            if (child.height() > 0) {
                onLastLine = false;
                lastLineWidth += child.width();
                break;
            }
            lastLineWidth += child.width();
        }

        if (onLastLine) {
            return block.prefixed() + lastLineWidth + block.suffixed();
        } else {
            return lastLineWidth + block.suffixed();
        }
    }

    private static int containerHeight(Container block) {
        return (int) StreamSupport.stream(block.children.spliterator(), false).mapToInt(IBlock::height).sum();
    }

    private static String containerString(Container block) {
        String childrenStr = StreamSupport.stream(block.children.spliterator(), false)
                                          .map(IBlock::string)
                                          .collect(Collectors.joining());
        switch (block.tag) {
            case "root":
                return childrenStr;
            default:
                return block.props.start + childrenStr + block.props.end;
        }
    }

    private static String containerValueString(Container block) {
        String childrenStr = StreamSupport.stream(block.children.spliterator(), false)
            .map(child -> {
                if (child instanceof IBlock.IBlockExpression) {
                    return ((IBlock.IBlockExpression) child).valueString();
                } else if (child instanceof IBlock.IBlockModifier) {
                    return child.string();
                }
                return null;
            })
            .filter(Objects::nonNull)
            .collect(Collectors.joining(" "));
        return block.props.start + childrenStr + block.props.end;
    }

    @FunctionalInterface
    public interface ModifierCommand {
        Object apply(Object accumulator, Object input);
    }

    public static class Modifier implements IBlock, IBlock.IBlockModifier, Comparable<IBlock> {
        public final String tag;
        public final String string;
        public final ModifierCommand command;

        public Modifier(String tag, String string, ModifierCommand command) {
            this.tag = tag;
            this.string = string;
            this.command = command;
        }

        @Override public String type() { return "modifier"; }
        @Override public String tag() { return this.tag; }
        @Override public String string() { return this.string; }
        @Override public int length() { return this.string.length(); }
        @Override public int width() { return this.string.length(); }
        @Override public int height() { return 0; }
        @Override public int prefixed() { return 0; }
        @Override public int suffixed() { return 0; }
        @Override public boolean verify() { return true; }

        @Override
        public Object modify(Object accumulator, Object input) {
            return command.apply(accumulator, input);
        }

        @Override
        public int compareTo(IBlock other) {
            return Block.compare(this, other);
        }

        @Override
        public String toString() {
            return this.string;
        }
    }
}
