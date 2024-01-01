package com.mola.molachat.robot.handler.impl.cmd;

import com.mola.molachat.robot.data.KeyValueFactoryInterface;
import com.mola.molachat.robot.model.KeyValue;
import com.mola.molachat.chatter.dto.ChatterDTO;
import com.mola.molachat.robot.event.CommandInputEvent;
import com.mola.molachat.robot.handler.impl.BaseCmdRobotHandler;
import com.mola.molachat.chatter.service.ChatterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2023-12-03 11:50
 **/
@Component
@Slf4j
public class KvOwnerChangeHandler  extends BaseCmdRobotHandler {

    @Resource
    private KeyValueFactoryInterface keyValueFactory;

    @Resource
    private ChatterService chatterService;

    @Override
    public String getCommand() {
        return "kvOwnerChange";
    }

    @Override
    public String getDesc() {
        return "kvOwnerChange";
    }

    @Override
    protected String executeCommand(CommandInputEvent baseEvent) {
        List<ChatterDTO> chatterDTOList = chatterService.list();
        List<KeyValue> keyValueList = keyValueFactory.list();
        try {
            for (ChatterDTO chatterDTO : chatterDTOList) {
                List<KeyValue> keyValues = keyValueList.stream()
                        .filter(keyValue -> Objects.equals(keyValue.getOwner(), chatterDTO.getName()))
                        .collect(Collectors.toList());
                keyValues.forEach(
                        kv -> {
                            kv.setOwner(chatterDTO.getId());
                            keyValueFactory.save(kv);
                        }
                );
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return "操作成功";
    }


    @Override
    public Integer order() {
        return 0;
    }
}
