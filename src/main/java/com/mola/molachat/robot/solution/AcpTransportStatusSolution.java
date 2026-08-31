package com.mola.molachat.robot.solution;

import com.mola.cmd.proxy.client.CmdProxyInvokeService;
import com.mola.rpc.core.remoting.netty.pool.ChannelWrapper;
import com.mola.rpc.core.system.ReverseInvokeHelper;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 普通ACP反向RPC通道状态查询。
 */
@Service
public class AcpTransportStatusSolution {

    private static final long CHANNEL_ALIVE_MILLIS = 60_000L;

    public boolean isConnected(String groupId) {
        String serviceKey = String.format("%s:%s:%s",
                CmdProxyInvokeService.class.getName(), groupId, "1.0.0");
        Map<String, ChannelWrapper> channels = ReverseInvokeHelper
                .instance()
                .fetchAvailableProxyService(serviceKey);
        return hasConnectedChannel(channels, System.currentTimeMillis());
    }

    boolean hasConnectedChannel(Map<String, ChannelWrapper> channels, long now) {
        if (channels == null || channels.isEmpty()) {
            return false;
        }
        return channels.values().stream().anyMatch(channel -> channel != null
                && channel.isOk()
                && now - channel.getLastAliveTime() < CHANNEL_ALIVE_MILLIS);
    }
}
