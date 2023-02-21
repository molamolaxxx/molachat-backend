package com.mola.molachat.controller;

import com.mola.molachat.common.ResponseCode;
import com.mola.molachat.common.ServerResponse;
import com.mola.molachat.data.OtherDataInterface;
import com.mola.molachat.entity.dto.ChatterDTO;
import com.mola.molachat.exception.service.ChatterServiceException;
import com.mola.molachat.form.ChatterForm;
import com.mola.molachat.service.ChatterService;
import com.mola.molachat.service.RobotService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.util.List;

/**
 * @author : molamola
 * @Project: molachat
 * @Description: 机器人api
 * @date : 2021-03-10 18:04
 **/
@RestController
@RequestMapping("/robot")
@Slf4j
public class RobotController {

    @Resource
    private ChatterService chatterService;

    @Resource
    private RobotService robotService;

    @Resource
    private OtherDataInterface otherDataInterface;

    @PutMapping
    public ServerResponse update(@Valid ChatterForm form,
                                 BindingResult bindingResult,
                                 HttpServletResponse response) {
        if (bindingResult.hasErrors()){
            log.error("表单验证出错");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return ServerResponse.createByErrorCodeMessage(ResponseCode.ERROR.getCode(),
                    bindingResult.getFieldError().getDefaultMessage());
        }
        if (!robotService.isRobot(form.getId())) {
            return ServerResponse.createByErrorMessage("修改对象不是机器人");
        }
        ChatterDTO chatterDTO = new ChatterDTO();
        BeanUtils.copyProperties(form, chatterDTO);

        try {
            chatterService.updateRobot(chatterDTO);
        } catch (ChatterServiceException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return ServerResponse.createByErrorCodeMessage(e.getCode(), e.getMessage());
        }
        return ServerResponse.createBySuccess();
    }

    @GetMapping("/push/{appKey}")
    public ServerResponse pushMessage(@PathVariable("appKey") String appKey,
                                      @RequestParam("toChatterId") String toChatterId,
                                      @RequestParam("content") String content) {
        try {
            // 发送服务
            robotService.pushMessage(appKey, toChatterId, content);
            return ServerResponse.createBySuccess();
        } catch (Exception e) {
            log.error("pushMessage error", e);
            return ServerResponse.createByErrorMessage(e.getMessage());
        }
    }

    @PostMapping("/gpt/insertSubApiKeys")
    public ServerResponse insertSubApiKeys(@RequestBody List<String> subApiKeys) {
        try {
            otherDataInterface.operateGpt3ChildTokens((tokens) -> {
                for (String subApiKey : subApiKeys) {
                    tokens.add(subApiKey);
                }
            });
            return ServerResponse.createBySuccess(otherDataInterface.getGpt3ChildTokens());
        } catch (Exception e) {
            log.error("insertSubApiKeys error", e);
            return ServerResponse.createByErrorMessage(e.getMessage());
        }
    }
}
