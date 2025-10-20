package com.mola.molachat.robot.handler.impl.mcp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2025-10-04 12:32
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CmdHistoryItem {

    private String cmdAndParam;

    private String result;

    private String target;

    private String headerMessage;

    private BigDecimal importantRate;

    /**
     * 整个命令隐藏
     */
    private boolean hiddenItem;

    /**
     * 整个结果隐藏
     */
    private boolean hiddenResult;

    public String fetchResult() {
        if (hiddenResult) {
            return "当前结果已隐藏";
        }
        return result;
    }
}
