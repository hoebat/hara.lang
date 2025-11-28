package hara.kernel.maven;

import static org.junit.Assert.*;

import org.junit.Test;

import hara.lang.base.I;
import hara.kernel.Foundation;

public class MavenTest {

    @Test
    public void testLoad() {
        Foundation f = new Foundation();
        f.call("SESSION", "NEW", "test");
        I.Runtime rt = (I.Runtime) f.RTS.get("test");
        assertNotNull(rt);

        f.call("MAVEN", "LOAD", "test", "org.apache.commons:commons-lang3:3.12.0");

        Class<?> stringUtilsClass = rt.classFor("org.apache.commons.lang3.StringUtils");
        assertNotNull(stringUtilsClass);
    }
}
