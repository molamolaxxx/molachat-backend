package com.mola.molachat.controller;

import com.mola.molachat.common.ResponseCode;
import com.mola.molachat.common.ServerResponse;
import com.mola.molachat.handler.common.TokenCheckHandler;
import com.mola.molachat.service.GroupService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2021-05-28 01:26
 **/
@RestController
@RequestMapping("/group")
@Slf4j
public class GroupController {

    @Resource
    private GroupService groupService;

    @Resource
    private TokenCheckHandler tokenCheckHandler;

    @GetMapping("/owner")
    public ServerResponse listByOwner(@RequestParam("chatterId") String chatterId,
                                      @RequestParam("token") String token,
                                      HttpServletRequest request,
                                      HttpServletResponse response) {
        //jwt验证
        if (!tokenCheckHandler.checkToken(chatterId, token, request)){
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return ServerResponse.createByErrorCodeMessage(ResponseCode.ERROR.getCode(),
                    "token验证错误");
        }
        return ServerResponse.createBySuccess(groupService.listByOwner(chatterId));
    }

    @GetMapping("/member")
    public ServerResponse listByMember(@RequestParam("chatterId") String chatterId,
                                       @RequestParam("token") String token,
                                       HttpServletRequest request,
                                       HttpServletResponse response) {
        //jwt验证
        if (!tokenCheckHandler.checkToken(chatterId, token, request)){
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return ServerResponse.createByErrorCodeMessage(ResponseCode.ERROR.getCode(),
                    "token验证错误");
        }
        return ServerResponse.createBySuccess(groupService.listByMemberId(chatterId));
    }
}
