package com.mola.molachat.robot.controller;

import com.mola.molachat.common.handler.TokenCheckHandler;
import com.mola.molachat.common.model.ServerResponse;
import com.mola.molachat.robot.dto.FilePreviewDTO;
import com.mola.molachat.robot.dto.FilePreviewRequest;
import com.mola.molachat.robot.solution.FilePreviewException;
import com.mola.molachat.robot.solution.FilePreviewSolution;
import org.apache.commons.lang.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/robot/file-preview")
public class FilePreviewController {

    @Resource
    private TokenCheckHandler tokenCheckHandler;

    @Resource
    private FilePreviewSolution filePreviewSolution;

    @PostMapping
    public ServerResponse<FilePreviewDTO> preview(@RequestBody FilePreviewRequest body,
                                                  HttpServletRequest request,
                                                  HttpServletResponse response) {
        if (body == null || StringUtils.isBlank(body.getChatterId())
                || !tokenCheckHandler.checkToken(body.getChatterId(), body.getToken(), request)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return ServerResponse.createByErrorMessage("UNAUTHORIZED");
        }
        try {
            return ServerResponse.createBySuccess(filePreviewSolution.preview(body));
        } catch (FilePreviewException e) {
            response.setStatus(e.getHttpStatus());
            return ServerResponse.createByErrorMessage(e.getCode());
        }
    }
}
