package com.mola.molachat.robot.handler.impl.cmd;

import com.mola.molachat.robot.event.CommandInputEvent;
import com.mola.molachat.robot.handler.impl.BaseCmdRobotHandler;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2024-09-08 21:33
 **/
@Component
public class DateFormatHandler extends BaseCmdRobotHandler {

    @Override
    public String getCommand() {
        return "date";
    }

    @Override
    public String getDesc() {
        return "转换时间戳到日期";
    }

    @Override
    protected String executeCommand(CommandInputEvent baseEvent) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
        return dateFormat.format(new Date(Long.parseLong(baseEvent.getCommandInput())));
    }

    @Override
    public Integer order() {
        return 0;
    }
}
