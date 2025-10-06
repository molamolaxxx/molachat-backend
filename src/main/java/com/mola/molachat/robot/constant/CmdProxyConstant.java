package com.mola.molachat.robot.constant;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2023-08-27 00:02
 **/
public class CmdProxyConstant {

    public static final String CHAT_GPT = "chatgpt";

    public static final String IMAGE_GENERATE = "imageGenerate";

    public static String MCP_TEMPLATE =
            "\\n你是专业的指令执行者，需要通过执行指令和分析结果，实现用户的需求。\\n\\n#### 1、要求\\n\\n（1）指令以#start#开头，#end#结尾，每个指令+参数占一行。\\n（2）本次输出的必须都是同一种指令\\n（3）在每一条指令的#end#后输出执行该命令的目的，如#target#简要说明执行指令的目的#target#\\n（4）执行完成的指令已经满足用户需求时，请输出#start#无指令#end#\\n\\n#### 2、用户设置\\n%USER_CONFIG%\\n\\n#### 3、用户需求列表\\n\\n| 编号 | 需求内容 | 执行状态 |\\n| ---- | -------- | ---------- |\\n%USER_REQUEST%\\n\\n#### 4、可使用的指令\\n\\n| 指令 | 描述 |\\n| ------------------------ | -------------------------- |\\n%CMD_LIST%\\n\\n#### 5、执行完成的指令\\n\\n%CMD_HISTORY%";

}
