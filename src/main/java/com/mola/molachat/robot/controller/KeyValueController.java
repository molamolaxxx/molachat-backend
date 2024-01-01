package com.mola.molachat.robot.controller;

import com.mola.molachat.robot.data.KeyValueFactoryInterface;
import com.mola.molachat.robot.model.KeyValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Objects;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2023-09-30 16:01
 **/
@RequestMapping("/kv")
@RestController
public class KeyValueController {

    @Resource
    private KeyValueFactoryInterface keyValueFactory;

    @GetMapping("/{key}")
    public String find(@PathVariable String key) {
        KeyValue keyValue = keyValueFactory.selectOne(key);
        return Objects.isNull(keyValue) ? "" : keyValue.getValue();
    }
}
