package com.mola.molachat.robot.controller;

import com.mola.molachat.common.model.ServerResponse;
import com.mola.molachat.robot.handler.impl.mcp.CmdConfirmManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 命令二次确认接口
 *
 * @author : molamola
 * @date : 2026-02-24
 **/
@RestController
@RequestMapping("/cmd")
@Slf4j
public class CmdConfirmController {

    /**
     * 用户确认或拒绝命令执行
     * @param confirmId 确认请求ID
     * @param confirmed true=执行，false=拒绝
     */
    @PostMapping("/confirm")
    public ServerResponse<String> confirm(@RequestParam String confirmId,
                                          @RequestParam boolean confirmed) {
        log.info("收到命令确认结果，confirmId={}, confirmed={}", confirmId, confirmed);
        CmdConfirmManager.INSTANCE.confirm(confirmId, confirmed);
        return ServerResponse.createBySuccess(confirmed ? "已确认执行" : "已拒绝执行");
    }
}
