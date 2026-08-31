package com.mola.molachat.session.controller;

import com.mola.molachat.common.handler.TokenCheckHandler;
import com.mola.molachat.common.model.ServerResponse;
import com.mola.molachat.session.solution.SessionPreviewSolution;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/session/message")
public class SessionMessageController {

    @Resource
    private TokenCheckHandler tokenCheckHandler;

    @Resource
    private SessionPreviewSolution sessionPreviewSolution;

    @GetMapping("/content")
    public ServerResponse<String> content(
            @RequestParam("sessionId") String sessionId,
            @RequestParam("messageId") String messageId,
            @RequestParam("chatterId") String chatterId,
            @RequestParam("token") String token,
            HttpServletRequest request,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        if (!tokenCheckHandler.checkToken(chatterId, token, request)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return ServerResponse.createByErrorMessage("token验证错误");
        }
        String content = sessionPreviewSolution.findMessageContent(sessionId, messageId, chatterId);
        if (content == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return ServerResponse.createByErrorMessage("消息不存在或无权访问");
        }
        return ServerResponse.createBySuccess(content);
    }
}
