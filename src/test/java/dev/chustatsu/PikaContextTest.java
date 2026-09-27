package dev.chustatsu;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public final class PikaContextTest {
    @Test
    public void recognizesOnlyPikaHosts() {
        assertTrue(PikaContext.isPikaAddress("play.pika-network.net"));
        assertTrue(PikaContext.isPikaAddress("PIKA-NETWORK.NET:25565"));
        assertTrue(PikaContext.isPikaAddress("play.pika.host."));
        assertFalse(PikaContext.isPikaAddress("pika-network.net.evil.example"));
        assertFalse(PikaContext.isPikaAddress("other-network.net"));
        assertFalse(PikaContext.isPikaAddress(null));
    }
}
