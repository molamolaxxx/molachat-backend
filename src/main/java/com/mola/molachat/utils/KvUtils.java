package com.mola.molachat.utils;

import com.mola.molachat.data.KeyValueFactoryInterface;
import com.mola.molachat.entity.KeyValue;
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
public class KvUtils {

    @Resource
    private KeyValueFactoryInterface keyValueFactory;

    public Integer getIntegerOrDefault(String key, Integer defaultValue) {
        KeyValue keyValue = keyValueFactory.selectOne(key);
        if (Objects.isNull(keyValue)) {
            return defaultValue;
        }
        return Integer.parseInt(key);
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
