package com.mola.molachat.common;

import com.mola.molachat.config.AppConfig;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;

/**
 * @author : molamola
 * @Project: molachat
 * @Description: 版本
 * @date : 2020-03-11 01:42
 **/
@Component
@Data
@Slf4j
public class Version {

    @Resource
    private AppConfig appConfig;

    public String get() {
        return appConfig.getVersion();
    }

    public void set(String version) {
        if (StringUtils.isEmpty(version)) {
            log.warn("版本传入为空，跳过");
            return;
        }
        appConfig.setVersion(version);
    }
}
