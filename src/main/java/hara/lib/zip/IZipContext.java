package hara.lib.zip;

import hara.lang.protocol.*;
import hara.lang.data.List;

public interface IZipContext {
    Object createContainer();
    Object createElement(Object data);
    boolean isContainer(Object element);
    boolean isEmptyContainer(Object element);
    boolean isElement(Object element);
    List listElements(Object container);
    Object updateElements(Object container, List newElements);
    Object addElement(Object container, Object element);
    Object wrapData(Object data);
    Object unwrapData(Object data);
}
