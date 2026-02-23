package com.mola.molachat.robot.handler.impl.mcp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 命令二次确认请求，推送给前端弹窗展示
 *
 * @author : molamola
 * @date : 2026-02-24
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CmdConfirmRequest implements Serializable {

    /**
     * 确认请求唯一ID，用于前端回传确认结果
     */
    private String confirmId;

    /**
     * 命令名称（如 executeBash）
     */
    private String cmdName;

    /**
     * 命令参数
     */
    private String cmdParam;

    /**
     * 匹配到的危险命令关键字
     */
    private String matchedCommand;

    /**
     * 危险命令描述
     */
    private String description;

    /**
     * 会话ID
     */
    private String sessionId;
}
