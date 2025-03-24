package com.mola.molachat.robot.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2025-03-23 18:00
 **/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CmdDescription {

    public static final CmdDescription NOT_SUPPORT = new CmdDescription();

    /**
     * 命令名称
     */
    private String cmdName;

    /**
     * 命令描述
     */
    private String cmdDesc;

    /**
     * 命令执行的js脚本
     */
    private String executeScript;

    public boolean support() {
        return this != NOT_SUPPORT;
    }

    public String renderLine() {
        return String.format("| %s | %s| <button class=\"blue-ring-button\" onClick=\"%s;closeMessageView()\">触发</button> |",
                cmdName, cmdDesc, executeScript);
    }
}
