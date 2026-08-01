package com.mola.molachat.team.solution;

import com.mola.cmd.proxy.client.consumer.CmdSender;
import com.mola.cmd.proxy.client.resp.CmdInvokeResponse;
import com.mola.cmd.proxy.client.resp.CmdResponseContent;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Consumer;

@Component
public class TeamCommandTransport {

    public Map<String, String> send(String command, String transportGroup, String payload) {
        return send(command, transportGroup, new String[]{payload});
    }

    public Map<String, String> sendWithoutArgs(String command, String transportGroup) {
        return send(command, transportGroup, new String[0]);
    }

    private Map<String, String> send(String command, String transportGroup, String[] args) {
        CmdInvokeResponse<CmdResponseContent> response =
                CmdSender.INSTANCE.send(command, transportGroup, args);
        if (response == null || !response.isSuccess() || response.getData() == null) {
            return null;
        }
        return response.getData().getResultMap();
    }

    public void registerEventCallback(String eventCommand, String transportGroup,
                                      Consumer<Map<String, String>> callback) {
        CmdSender.INSTANCE.registerCallback(eventCommand, transportGroup, response -> {
            callback.accept(response.getResultMap());
            return kotlin.Unit.INSTANCE;
        });
    }
}
