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
        assertFalse(solution.hasConnectedChannel(null));
        assertFalse(solution.hasConnectedChannel(Collections.emptyMap()));
    }

    @Test
    public void channelWrapperAvailabilityIsTheSingleSourceOfTruth() {
        ChannelWrapper channel = channel(true);

        assertTrue(solution.hasConnectedChannel(
                Collections.singletonMap("proxy", channel)));
    }

    @Test
    public void inactiveChannelIsDisconnected() {
        ChannelWrapper inactive = channel(false);

        assertFalse(solution.hasConnectedChannel(
                Collections.singletonMap("inactive", inactive)));
    }

    private ChannelWrapper channel(boolean ok) {
        ChannelWrapper channel = mock(ChannelWrapper.class);
        when(channel.isOk()).thenReturn(ok);
        return channel;
    }
}
