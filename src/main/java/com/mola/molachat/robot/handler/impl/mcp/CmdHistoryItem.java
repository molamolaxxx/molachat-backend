package com.mola.molachat.robot.handler.impl.mcp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}
