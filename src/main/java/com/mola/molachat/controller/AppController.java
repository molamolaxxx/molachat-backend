package com.mola.molachat.controller;

import com.mola.molachat.common.ServerResponse;
import com.mola.molachat.common.Version;
import com.mola.molachat.enumeration.ServiceErrorEnum;
import com.mola.molachat.exception.service.GroupServiceException;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2020-03-11 01:45
 **/
@RequestMapping("/app")
@RestController
public class AppController {

    @Resource
    private Version appVersion;

    /**
     * 获取整个app的版本
     * @return
     */
    @GetMapping("/version")
    private ServerResponse version() {
        return ServerResponse.createBySuccess(appVersion.get());
    }

    /**
     * 修改版本
     * @return
     */
    @PostMapping("/version/{version}")
    private ServerResponse changeVersion(@PathVariable String version) {
        appVersion.set(version);
        return ServerResponse.createBySuccess();
    }

    /**
     * app端验证服务器地址
     */
    @GetMapping("/host")
    private ServerResponse host() {
        return ServerResponse.createBySuccess();
    }

    @GetMapping("/exception")
    private ServerResponse exception() {
        throw new GroupServiceException(ServiceErrorEnum.VISITOR_CREATE_GROUP_ERROR);
    }
}
