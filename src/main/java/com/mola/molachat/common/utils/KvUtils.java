package com.mola.molachat.common.utils;

import com.mola.molachat.robot.data.KeyValueFactoryInterface;
import com.mola.molachat.robot.model.KeyValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Objects;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2023-11-19 21:54
 **/
@Component
@Slf4j
public class KvUtils {

    @Resource
    private KeyValueFactoryInterface keyValueFactory;

    public Integer getIntegerOrDefault(String key, Integer defaultValue) {
        KeyValue keyValue = keyValueFactory.selectOne(key);
        if (Objects.isNull(keyValue)) {
            return defaultValue;
        }
        log.info("key:{}, value:{}", key, Integer.parseInt(keyValue.getValue()));
        return Integer.parseInt(keyValue.getValue());
    }

    public String getString(String key) {
        KeyValue keyValue = keyValueFactory.selectOne(key);
        if (Objects.isNull(keyValue)) {
            return null;
        }
        return keyValue.getValue();
    }

    public void set(String key, String value) {
        KeyValue keyValue = keyValueFactory.selectOne(key);
        if (Objects.isNull(keyValue)) {
            return;
        }
        keyValue.setValue(value);
        keyValueFactory.save(keyValue);
    }
}
