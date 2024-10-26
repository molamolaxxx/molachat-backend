package com.mola.molachat.robot.handler.impl.cmd;

import com.mola.molachat.robot.event.CommandInputEvent;
import com.mola.molachat.robot.handler.impl.BaseCmdRobotHandler;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2024-09-08 21:33
 **/
@Component
public class DateParseHandler extends BaseCmdRobotHandler {

    @Override
    public String getCommand() {
        return "time";
    }

    @Override
    public String getDesc() {
        return "转换日期到时间戳";
    }

    @Override
    protected String executeCommand(CommandInputEvent baseEvent) {
        String result = parseDate2Time(baseEvent.getCommandInput(), "yyyy-MM-dd HH:mm:ss");
        if (result == null) {
            result = parseDate2Time(baseEvent.getCommandInput(), "yyyy-MM-dd");
        }
        return result;
    }

    private String parseDate2Time(String input, String format) {
        try {
            // 解析日期字符串
            Date date = new SimpleDateFormat(format).parse(input);
            // 获取时间戳
            long timestamp = date.getTime();
            return String.valueOf(timestamp);
        } catch (ParseException e) {
            return null;
        }
    }


    @Override
    public Integer order() {
        return 0;
    }
}
