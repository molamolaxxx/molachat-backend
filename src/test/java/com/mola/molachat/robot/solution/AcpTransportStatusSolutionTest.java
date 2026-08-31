package com.mola.molachat.robot.solution;

import com.mola.rpc.core.remoting.netty.pool.ChannelWrapper;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AcpTransportStatusSolutionTest {

    private final AcpTransportStatusSolution solution = new AcpTransportStatusSolution();

    @Test
    public void missingChannelIsDisconnected() {
        assertFalse(solution.hasConnectedChannel(null, 100_000L));
        assertFalse(solution.hasConnectedChannel(Collections.emptyMap(), 100_000L));
    }

    @Test
    public void activeChannelWithFreshHeartbeatIsConnected() {
        ChannelWrapper channel = channel(true, 99_000L);

        assertTrue(solution.hasConnectedChannel(
                Collections.singletonMap("proxy", channel), 100_000L));
    }

    @Test
    public void inactiveOrStaleChannelIsDisconnected() {
        ChannelWrapper inactive = channel(false, 99_000L);
        ChannelWrapper stale = channel(true, 40_000L);

        assertFalse(solution.hasConnectedChannel(
                Collections.singletonMap("inactive", inactive), 100_000L));
        assertFalse(solution.hasConnectedChannel(
                Collections.singletonMap("stale", stale), 100_000L));
    }

    private ChannelWrapper channel(boolean ok, long lastAliveTime) {
        ChannelWrapper channel = mock(ChannelWrapper.class);
        when(channel.isOk()).thenReturn(ok);
        when(channel.getLastAliveTime()).thenReturn(lastAliveTime);
        return channel;
    }
}
