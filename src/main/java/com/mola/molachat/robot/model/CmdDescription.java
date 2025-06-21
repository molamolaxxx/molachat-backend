package com.mola.molachat.robot.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

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

    public static final List<CmdDescription> NOT_SUPPORT = Collections.emptyList();

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

    public String renderLine() {
        return String.format("| %s | %s| <button class=\"blue-ring-button\" onClick=\"%s;closeMessageView()\">触发</button> |",
                cmdName, cmdDesc, executeScript);
    }

    public static class CmdDescriptionListBuilder extends CmdDescriptionBuilder {

        public List<CmdDescription> buildSingleton() {
            return Collections.singletonList(super.build());
        }

        @Override
        public CmdDescriptionListBuilder cmdName(String cmdName) {
            return (CmdDescriptionListBuilder)super.cmdName(cmdName);
        }

        @Override
        public CmdDescriptionListBuilder cmdDesc(String cmdDesc) {
            return (CmdDescriptionListBuilder)super.cmdDesc(cmdDesc);
        }

        @Override
        public CmdDescriptionListBuilder executeScript(String executeScript) {
            return (CmdDescriptionListBuilder)super.executeScript(executeScript);
        }
    }

    public static CmdDescriptionListBuilder builder() {
        return new CmdDescriptionListBuilder();
    }
}
