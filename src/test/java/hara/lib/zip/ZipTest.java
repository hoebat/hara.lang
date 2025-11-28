package hara.lib.zip;

import hara.lang.data.Vector;
import org.junit.Test;
import static org.junit.Assert.*;

public class ZipTest {

  @Test
  public void testZipperCreation() {
    Vector root = Vector.Standard.from(null, 1, 2, 3);
    Zipper zipper = Zip.zipper(root);
    assertEquals(root, zipper.currentNode());
  }

  @Test
  public void testNavigation() {
    Vector root = Vector.Standard.from(null, 1, 2, 3);
    Zipper zipper = Zip.zipper(root);
    zipper = Zip.stepInside(zipper);
    assertEquals(1, zipper.currentNode());
    zipper = Zip.stepRight(zipper);
    assertEquals(2, zipper.currentNode());
    zipper = Zip.stepRight(zipper);
    assertEquals(3, zipper.currentNode());
    zipper = Zip.stepLeft(zipper);
    assertEquals(2, zipper.currentNode());
  }

  @Test
  public void testManipulation() {
    Vector root = Vector.Standard.from(null, 1, 2, 3);
    Zipper zipper = Zip.zipper(root);
    zipper = Zip.stepInside(zipper);
    zipper = Zip.stepRight(zipper);
    assertEquals(2, zipper.currentNode());

    zipper = Zip.insertLeft(zipper, 0);
    zipper = Zip.stepLeft(zipper);
    assertEquals(0, zipper.currentNode());

    zipper = Zip.insertAndFocus(zipper, 9);
    assertEquals(9, zipper.currentNode());

    zipper = Zip.stepRight(zipper);
    assertEquals(0, zipper.currentNode());

    zipper = Zip.replace(zipper, 8);
    assertEquals(8, zipper.currentNode());

    zipper = Zip.deleteLeft(zipper);
    zipper = Zip.stepLeft(zipper);
    assertEquals(1, zipper.currentNode());

    zipper = Zip.remove(zipper);
    assertEquals(8, zipper.currentNode());
  }

  @Test
  public void testFindNext() {
    Vector root = Vector.Standard.from(null, 1, Vector.Standard.from(null, 2, 3), 4);
    Zipper zipper = Zip.zipper(root);
    Zipper found = Zip.findNext(zipper, z -> z.currentNode().equals(3));
    assertEquals(3, found.currentNode());
  }

  @Test
  public void testPrewalk() {
    Vector root = Vector.Standard.from(null, 1, Vector.Standard.from(null, 2, 3), 4);
    Zipper zipper = Zip.zipper(root);
    Zipper walked =
        Zip.prewalk(
            zipper,
            n -> {
              if (n instanceof Integer && (Integer) n == 2) {
                return 99;
              }
              return n;
            });
    assertTrue(
        hara.lang.base.Eq.eq(
            Vector.Standard.from(null, 1, Vector.Standard.from(null, 99, 3), 4),
            walked.currentNode()));
  }

  @Test
  public void testPostwalk() {
    Vector root = Vector.Standard.from(null, 1, Vector.Standard.from(null, 2, 3), 4);
    Zipper zipper = Zip.zipper(root);
    Zipper walked =
        Zip.postwalk(
            zipper,
            n -> {
              if (n instanceof Integer && (Integer) n == 2) {
                return 99;
              }
              return n;
            });
    assertTrue(
        hara.lang.base.Eq.eq(
            Vector.Standard.from(null, 1, Vector.Standard.from(null, 99, 3), 4),
            walked.currentNode()));
  }

  @Test
  public void testUpdateElements() {
    hara.lang.data.List root = hara.lang.data.List.Standard.from(null, 1, 2, 3);
    Zipper zipper = Zip.zipper(root);
    zipper = Zip.stepInside(zipper);
    zipper = Zip.insertLeft(zipper, 0);
    zipper = Zip.stepOutside(zipper);
    assertTrue(
        hara.lang.base.Eq.eq(
            hara.lang.data.List.Standard.from(null, 0, 1, 2, 3), zipper.currentNode()));
  }

  @Test
  public void testAtRightMost() {
    hara.lang.data.List list = hara.lang.data.List.Standard.from(null, 1, 2, 3);
    Zipper zipper = Zip.zipper(list);
    zipper = Zip.stepInside(zipper);
    assertFalse(zipper.atRightMost());
    zipper = Zip.stepRight(zipper);
    assertFalse(zipper.atRightMost());
    zipper = Zip.stepRight(zipper);
    assertTrue(zipper.atRightMost());
  }

  @Test
  public void testUpdateElementsWithList() {
    hara.lang.data.List list = hara.lang.data.List.Standard.from(null, 1, 2, 3, 4, 5);
    Zipper zipper = Zip.zipper(list);
    zipper = Zip.stepInside(zipper);
    zipper = Zip.stepRight(zipper); // Now at '2'
    Zipper updatedZipper =
        Zip.updateElements(zipper, hara.lang.data.List.Standard.from(null, 9, 8, 7));
    hara.lang.data.List expected = hara.lang.data.List.Standard.from(null, 1, 9, 8, 7, 3, 4, 5);
    assertTrue(hara.lang.base.Eq.eq(expected, Zip.result(updatedZipper)));
  }

  @Test
  public void testStepRightAtEnd() {
    hara.lang.data.List list = hara.lang.data.List.Standard.from(null, 1, 2, 3);
    Zipper zipper = Zip.zipper(list);
    zipper = Zip.stepInside(zipper);
    zipper = Zip.stepRight(zipper);
    zipper = Zip.stepRight(zipper);
    assertEquals(3, zipper.currentNode());
    zipper = Zip.stepRight(zipper);
    assertEquals(3, zipper.currentNode()); // Should not move past the end
  }

  @Test
  public void testStepNextAtEnd() {
    Vector root = Vector.Standard.from(null, 1, Vector.Standard.from(null, 2, 3), 4);
    Zipper zipper = Zip.zipper(root);
    zipper = Zip.stepNext(zipper);
    assertEquals(1, zipper.currentNode());
    zipper = Zip.stepNext(zipper);
    assertTrue(hara.lang.base.Eq.eq(Vector.Standard.from(null, 2, 3), zipper.currentNode()));
    zipper = Zip.stepNext(zipper);
    assertEquals(2, zipper.currentNode());
    zipper = Zip.stepNext(zipper);
    assertEquals(3, zipper.currentNode());
    zipper = Zip.stepNext(zipper);
    assertEquals(4, zipper.currentNode());
    assertNull(Zip.stepNext(zipper)); // Should be at the end
  }
}
