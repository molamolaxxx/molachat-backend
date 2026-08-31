package com.mola.molachat.robot.solution;

import com.alibaba.fastjson.JSON;
import com.mola.cmd.proxy.client.consumer.CmdSender;
import com.mola.cmd.proxy.client.resp.CmdInvokeResponse;
import com.mola.cmd.proxy.client.resp.CmdResponseContent;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 普通 MAIN ACP prompt 提交与取消入口。
 */
@Service
@Slf4j
public class AcpPromptSolution {

    private static final String BUSY_POLICY_INTERRUPT = "INTERRUPT";

    public InvokeResult sendInterrupting(String groupId, String message,
                                         List<Map<String, String>> files) {
        Map<String, Object> params = new HashMap<>();
        params.put("groupId", groupId);
        params.put("message", message);
        params.put("busyPolicy", BUSY_POLICY_INTERRUPT);
        if (files != null && !files.isEmpty()) {
            params.put("files", files);
        }
        return invoke("acpSendMessage", groupId, params);
    }

    public InvokeResult cancelPrompt(String groupId) {
        Map<String, Object> params = new HashMap<>();
        params.put("groupId", groupId);
        return invoke("acpCancelPrompt", groupId, params);
    }

    private InvokeResult invoke(String command, String groupId, Map<String, Object> params) {
        try {
            CmdInvokeResponse<CmdResponseContent> response = CmdSender.INSTANCE.send(
                    command, groupId, new String[]{JSON.toJSONString(params)});
            if (response == null || response.getData() == null
                    || response.getData().getResultMap() == null) {
                return InvokeResult.rejected("EMPTY_RESPONSE", command + " 响应为空");
            }
            Map<String, String> resultMap = response.getData().getResultMap();
            String accepted = resultMap.get("accepted");
            String code = resultMap.get("code");
            String message = resultMap.get("result");
            // 兼容先部署的旧 cmd-proxy；新版本必须显式返回 accepted/code。
            boolean success = accepted == null || Boolean.parseBoolean(accepted);
            return new InvokeResult(success,
                    code == null ? (success ? "LEGACY_ACCEPTED" : "REJECTED") : code,
                    message);
        } catch (Exception e) {
            log.error("{} 执行失败, groupId={}", command, groupId, e);
            return InvokeResult.rejected("INVOKE_FAILED", e.getMessage());
        }
    }

    @Getter
    public static class InvokeResult {
        private final boolean accepted;
        private final String code;
        private final String message;

        public InvokeResult(boolean accepted, String code, String message) {
            this.accepted = accepted;
            this.code = code;
            this.message = message;
        }

        public static InvokeResult accepted(String code, String message) {
            return new InvokeResult(true, code, message);
        }

        public static InvokeResult rejected(String code, String message) {
            return new InvokeResult(false, code, message);
        }
    }
}
