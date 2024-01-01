package com.mola.molachat.common.exceptionhandler;

import com.mola.molachat.server.websocket.WSResponse;
import com.mola.molachat.common.config.SelfConfig;
import com.mola.molachat.server.ChatServer;
import com.mola.molachat.server.service.ServerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * @author : molamola
 * @Project: molachat
 * @Description: 文件上传异常处理
 * @date : 2019-09-26 09:57
 **/
@ControllerAdvice
@Slf4j
public class UploadExceptionHandler {

    @Resource
    private ServerService serverService;

    @Resource
    private SelfConfig config;

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseBody
    private void exception(HttpServletRequest request) throws Exception {
        log.error("上传文件过大");
        //通知server弹出异常
        String chatterId = (String) request.getSession().getAttribute("id");
        if (null == chatterId){
            log.error("session不存在");
            return;
        }
        ChatServer server = serverService.selectByChatterId(chatterId);
        server.getSession().sendToClient(WSResponse
                .exception("exception", "文件过大，请上传小于"+config.getMaxFileSize()+"M的文件"));
    }
}
