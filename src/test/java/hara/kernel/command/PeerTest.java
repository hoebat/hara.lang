package hara.kernel.command;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.List;
import hara.kernel.Foundation;

public class PeerTest {

    @Test
    public void testPeerHelp() {
        Foundation f = new Foundation();
        List result = (List) f.call("PEER");
        assertTrue(result.contains("ADD"));
        assertTrue(result.contains("LIST"));
    }

    @Test
    public void testPeerAddAndList() {
        Foundation f = new Foundation();

        // ADD
        f.call("PEER", "ADD", "test-node", "127.0.0.1", "9090");

        // LIST
        List peers = (List) f.call("PEER", "LIST");
        assertFalse(peers.isEmpty());

        // CHECK content (mapToList structure is List of Lists)
        // [[name, peerObj], ...]
        boolean found = false;
        for (Object o : peers) {
            List entry = (List) o;
            if ("test-node".equals(entry.get(0))) {
                found = true;
                break;
            }
        }
        assertTrue(found);
    }
}
