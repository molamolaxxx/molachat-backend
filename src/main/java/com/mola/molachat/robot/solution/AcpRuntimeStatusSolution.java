package com.mola.molachat.robot.solution;

import com.alibaba.fastjson.JSON;
import com.mola.cmd.proxy.client.consumer.CmdSender;
import com.mola.cmd.proxy.client.resp.CmdInvokeResponse;
import com.mola.cmd.proxy.client.resp.CmdResponseContent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 普通MAIN ACP运行状态查询。
 */
@Service
@Slf4j
public class AcpRuntimeStatusSolution {

    public String getStatus(String groupId) {
        try {
            Map<String, String> paramMap = new HashMap<>();
            paramMap.put("groupId", groupId);
            CmdInvokeResponse<CmdResponseContent> response = CmdSender.INSTANCE.send(
                    "acpGetStatus", groupId,
                    new String[]{JSON.toJSONString(paramMap)});
            if (response != null && response.getData() != null) {
                String result = response.getData().getResultMap().get("result");
                if (result != null) {
                    return result;
                }
            }
        } catch (Exception e) {
            log.debug("MAIN ACP状态查询失败, groupId={}, error={}", groupId, e.getMessage());
        }
        return "UNKNOWN";
    }

    public boolean isOnline(String groupId) {
        String status = getStatus(groupId);
        return "READY".equals(status) || "BUSY".equals(status);
    }
}
