package dev.heysulo.databridge.core.common;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.List;

public class ProtocolSettingsTest {

    @Test
    public void startsWithDefaults() {
        ProtocolSettings settings = new ProtocolSettings();

        Assert.assertEquals(settings.getTrustedPackages(), MessageDecoder.DEFAULT_TRUSTED_PACKAGES);
        Assert.assertEquals(settings.getMaxFrameLength(), MessageDecoder.DEFAULT_MAX_FRAME_LENGTH);
        Assert.assertEquals(settings.getHeartbeatInterval(), ProtocolSettings.DEFAULT_HEARTBEAT_INTERVAL);
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void trustedPackagesCannotBeChangedThroughTheGetter() {
        new ProtocolSettings().getTrustedPackages().clear();
    }

    @Test
    public void clearingRemovesDefaultsButKeepsLaterRules() {
        ProtocolSettings settings = new ProtocolSettings();

        settings.clearTrustedPackages();
        settings.addTrustedPackage("com.example.**");

        Assert.assertEquals(settings.getTrustedPackages(), List.of("com.example.**"));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void maxFrameLengthMustBePositive() {
        new ProtocolSettings().setMaxFrameLength(0);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void heartbeatIntervalMustNotBeNegative() {
        new ProtocolSettings().setHeartbeatInterval(Duration.ofSeconds(-1));
    }

    @Test
    public void zeroHeartbeatIntervalIsAllowed() {
        ProtocolSettings settings = new ProtocolSettings();

        settings.setHeartbeatInterval(Duration.ZERO);

        Assert.assertTrue(settings.getHeartbeatInterval().isZero());
    }
}
