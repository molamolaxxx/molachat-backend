package com.mola.molachat.robot.controller;

import com.mola.molachat.chatter.dto.ChatterDTO;
import com.mola.molachat.chatter.model.ChatterForm;
import com.mola.molachat.chatter.service.ChatterService;
import com.mola.molachat.common.exception.service.ChatterServiceException;
import com.mola.molachat.common.model.ResponseCode;
import com.mola.molachat.common.model.ServerResponse;
import com.mola.molachat.robot.solution.RobotSolution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

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
    private RobotSolution robotSolution;

    @PutMapping
    public ServerResponse<Void> update(@Valid ChatterForm form,
                                 BindingResult bindingResult,
                                 HttpServletResponse response) {
        if (bindingResult.hasErrors()){
            log.error("表单验证出错");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return ServerResponse.createByErrorCodeMessage(ResponseCode.ERROR.getCode(),
                    bindingResult.getFieldError().getDefaultMessage());
        }
        if (!robotSolution.isRobot(form.getId())) {
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
    public ServerResponse<Void> pushMessage(@PathVariable("appKey") String appKey,
                                      @RequestParam("toChatterId") String toChatterId,
                                      @RequestParam("content") String content) {
        try {
            // 发送服务
            robotSolution.pushMessage(appKey, toChatterId, content);
            return ServerResponse.createBySuccess();
        } catch (Exception e) {
            log.error("pushMessage error", e);
            return ServerResponse.createByErrorMessage(e.getMessage());
        }
    }
}
