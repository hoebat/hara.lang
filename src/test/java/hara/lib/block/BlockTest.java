package hara.lib.block;

import hara.lang.data.Vector;
import org.junit.Test;
import static org.junit.Assert.*;

public class BlockTest {

  @Test
  public void testVoidBlockCreation() {
    Block.Void voidBlock = new Block.Void("linespace", ' ', 1, 0);

    assertEquals("void", voidBlock.type());
    assertEquals("linespace", voidBlock.tag());
    assertEquals(" ", voidBlock.string());
    assertEquals(1, voidBlock.length());
    assertEquals(1, voidBlock.width());
    assertEquals(0, voidBlock.height());
    assertTrue(voidBlock.verify());
  }

  @Test
  public void testCommentBlockCreation() {
    String commentText = "; this is a comment";
    Block.Comment commentBlock = new Block.Comment(commentText);

    assertEquals("comment", commentBlock.type());
    assertEquals("comment", commentBlock.tag());
    assertEquals(commentText, commentBlock.string());
    assertEquals(commentText.length(), commentBlock.length());
    assertEquals(commentText.length(), commentBlock.width());
    assertEquals(0, commentBlock.height());
    assertTrue(commentBlock.verify());
  }

  @Test
  public void testTokenBlockCreation() {
    Block.Token tokenBlock = new Block.Token("string", "\"hello\"", "hello", "\"hello\"", 7, 0);

    assertEquals("token", tokenBlock.type());
    assertEquals("string", tokenBlock.tag());
    assertEquals("\"hello\"", tokenBlock.string());
    assertEquals("hello", tokenBlock.value());
    assertEquals(7, tokenBlock.width());
    assertTrue(tokenBlock.verify());
  }

  @Test
  public void testModifierBlockCreation() {
    Block.Modifier modifierBlock = new Block.Modifier("uneval", "#_", (acc, input) -> acc);

    assertEquals("modifier", modifierBlock.type());
    assertEquals("uneval", modifierBlock.tag());
    assertEquals("#_", modifierBlock.string());
    assertNotNull(modifierBlock.command);
  }

  @Test
  public void testContainerImmutability() {
    Vector<Block.IBlock> children =
        Vector.Standard.from(null, new Block.Token("number", "1", 1, "1", 1, 0));
    Block.Container container =
        new Block.Container("vector", children, new Block.Container.Props("[", "]"));

    try {
      ((Vector.Mutable<Block.IBlock>) container.children())
          .pushLast(new Block.Token("number", "2", 2, "2", 1, 0));
      fail("Expected ClassCastException");
    } catch (ClassCastException e) {
      // Test passed
    }
  }

  @Test
  public void testContainerSingleLineWidth() {
    Vector<Block.IBlock> children =
        Vector.Standard.from(
            null,
            new Block.Token("number", "1", 1, "1", 1, 0),
            new Block.Void("linespace", ' ', 1, 0),
            new Block.Token("number", "2", 2, "2", 1, 0));
    Block.Container container =
        new Block.Container("vector", children, new Block.Container.Props("[", "]"));
    assertEquals(5, container.width()); // [1 2] -> 1 + 1 + 1 + 1 + 1
  }

  @Test
  public void testContainerMultiLineWidth() {
    Vector<Block.IBlock> children =
        Vector.Standard.from(
            null,
            new Block.Token("number", "1", 1, "1", 1, 0),
            new Block.Void("linebreak", '\n', 0, 1),
            new Block.Token("number", "2", 2, "2", 1, 0));
    Block.Container container =
        new Block.Container("vector", children, new Block.Container.Props("[", "]"));
    assertEquals(2, container.width()); // 2] -> 1 + 1
  }

  @Test
  public void testContainerHeight() {
    Vector<Block.IBlock> children =
        Vector.Standard.from(
            null,
            new Block.Token("number", "1", 1, "1", 1, 0),
            new Block.Void("linebreak", '\n', 0, 1),
            new Block.Token("number", "2", 2, "2", 1, 0),
            new Block.Void("linebreak", '\n', 0, 1));
    Block.Container container =
        new Block.Container("vector", children, new Block.Container.Props("[", "]"));
    assertEquals(2, container.height());
  }
}
