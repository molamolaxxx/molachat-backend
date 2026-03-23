package com.mola.molachat.robot.handler.impl.mcp;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 危险命令检查器，判断指令是否需要用户二次确认
 *
 * @author : molamola
 * @date : 2026-02-24
 **/
public class DangerousCmdChecker {

    private static final Map<String, String> DANGEROUS_COMMANDS = new LinkedHashMap<>();

    static {
        // 文件操作
        DANGEROUS_COMMANDS.put("mv ", "移动或重命名文件/目录");
        DANGEROUS_COMMANDS.put("cp ", "复制文件/目录");
        DANGEROUS_COMMANDS.put("mkdir ", "创建目录");
        DANGEROUS_COMMANDS.put("touch ", "创建空文件或更新文件时间戳");
        DANGEROUS_COMMANDS.put("rm ", "删除文件");
        DANGEROUS_COMMANDS.put("rmdir ", "删除空目录");
        DANGEROUS_COMMANDS.put("tee ", "将输出写入文件");
        DANGEROUS_COMMANDS.put("sed -i", "直接修改文件内容");
        DANGEROUS_COMMANDS.put("chmod ", "修改文件权限");
        DANGEROUS_COMMANDS.put("chown ", "修改文件所有者");
        DANGEROUS_COMMANDS.put("ln ", "创建链接（软链接/硬链接）");
        DANGEROUS_COMMANDS.put("tar ", "打包/解包归档文件");
        DANGEROUS_COMMANDS.put("unzip ", "解压 ZIP 文件");
        DANGEROUS_COMMANDS.put("zip ", "压缩文件为 ZIP 格式");
        DANGEROUS_COMMANDS.put(">>", "追加内容到文件");
        DANGEROUS_COMMANDS.put("> ", "重定向输出到文件（覆盖）");

        // 安装/卸载
        DANGEROUS_COMMANDS.put("install ", "安装软件或组件");
        DANGEROUS_COMMANDS.put("uninstall ", "卸载软件或组件");
        DANGEROUS_COMMANDS.put("apt ", "Debian/Ubuntu 包管理器操作");
        DANGEROUS_COMMANDS.put("yum ", "CentOS/RHEL 包管理器操作");
        DANGEROUS_COMMANDS.put("dnf ", "Fedora 包管理器操作");
        DANGEROUS_COMMANDS.put("brew ", "Homebrew 包管理器操作");
        DANGEROUS_COMMANDS.put("pip install", "安装 Python 包");
        DANGEROUS_COMMANDS.put("npm install", "安装 Node.js 包");
        DANGEROUS_COMMANDS.put("yarn add", "安装 Node.js 包（Yarn）");
        DANGEROUS_COMMANDS.put("make install", "编译安装软件");
        DANGEROUS_COMMANDS.put("mvn", "执行 Maven 构建命令");
        DANGEROUS_COMMANDS.put("choco ", "Chocolatey 包管理器操作（Windows）");
        DANGEROUS_COMMANDS.put("scoop ", "Scoop 包管理器操作（Windows）");
        DANGEROUS_COMMANDS.put("winget ", "Windows 包管理器操作");
        DANGEROUS_COMMANDS.put("msiexec", "运行 Windows 安装程序");
        DANGEROUS_COMMANDS.put("start-process", "启动新进程（Windows）");

        // 进程/系统操作
        DANGEROUS_COMMANDS.put("kill ", "终止指定进程");
        DANGEROUS_COMMANDS.put("kill -", "向进程发送信号");
        DANGEROUS_COMMANDS.put("killall ", "按名称终止所有匹配进程");
        DANGEROUS_COMMANDS.put("pkill ", "按模式匹配终止进程");
        DANGEROUS_COMMANDS.put("xkill", "点击窗口终止对应进程");
        DANGEROUS_COMMANDS.put("shutdown", "关闭计算机");
        DANGEROUS_COMMANDS.put("reboot", "重启计算机");
        DANGEROUS_COMMANDS.put("halt", "停止系统");
        DANGEROUS_COMMANDS.put("poweroff", "关闭电源");
        DANGEROUS_COMMANDS.put("init 0", "关机（init 级别 0）");
        DANGEROUS_COMMANDS.put("init 6", "重启（init 级别 6）");
        DANGEROUS_COMMANDS.put("nohup ", "后台运行命令（不挂断）");
        DANGEROUS_COMMANDS.put("systemctl stop", "停止系统服务");
        DANGEROUS_COMMANDS.put("systemctl restart", "重启系统服务");
        DANGEROUS_COMMANDS.put("systemctl disable", "禁用系统服务");
        DANGEROUS_COMMANDS.put("systemctl suspend", "使系统进入休眠状态");
        DANGEROUS_COMMANDS.put("service ", "管理系统服务");

        // 网络
        DANGEROUS_COMMANDS.put("ifconfig ", "配置网络接口");
        DANGEROUS_COMMANDS.put("ip link set", "设置网络链路属性");
        DANGEROUS_COMMANDS.put("iptables ", "配置防火墙规则");

        // 磁盘/挂载
        DANGEROUS_COMMANDS.put("mount ", "挂载文件系统");
        DANGEROUS_COMMANDS.put("umount ", "卸载文件系统");
        DANGEROUS_COMMANDS.put("eject ", "弹出可移动设备");

        // 定时任务
        DANGEROUS_COMMANDS.put("crontab ", "编辑定时任务");

        // 用户管理
        DANGEROUS_COMMANDS.put("useradd ", "添加系统用户");
        DANGEROUS_COMMANDS.put("userdel ", "删除系统用户");
        DANGEROUS_COMMANDS.put("usermod ", "修改用户属性");
        DANGEROUS_COMMANDS.put("groupadd ", "添加用户组");
        DANGEROUS_COMMANDS.put("groupdel ", "删除用户组");
        DANGEROUS_COMMANDS.put("passwd ", "修改用户密码");

        // PowerShell 特有
        DANGEROUS_COMMANDS.put("move-item", "移动或重命名文件/目录（PowerShell）");
        DANGEROUS_COMMANDS.put("copy-item", "复制文件/目录（PowerShell）");
        DANGEROUS_COMMANDS.put("new-item", "创建新文件或目录（PowerShell）");
        DANGEROUS_COMMANDS.put("set-content", "写入文件内容（PowerShell）");
        DANGEROUS_COMMANDS.put("add-content", "追加文件内容（PowerShell）");
        DANGEROUS_COMMANDS.put("out-file", "将输出写入文件（PowerShell）");
        DANGEROUS_COMMANDS.put("rename-item", "重命名文件/目录（PowerShell）");
        DANGEROUS_COMMANDS.put("set-itemproperty", "设置项属性（PowerShell）");
        DANGEROUS_COMMANDS.put("stop-process", "终止进程（PowerShell）");
        DANGEROUS_COMMANDS.put("taskkill", "终止进程（Windows）");
        DANGEROUS_COMMANDS.put("stop-computer", "关闭计算机（PowerShell）");
        DANGEROUS_COMMANDS.put("restart-computer", "重启计算机（PowerShell）");
        DANGEROUS_COMMANDS.put("logoff", "注销当前用户");
        DANGEROUS_COMMANDS.put("stop-service", "停止服务（PowerShell）");
        DANGEROUS_COMMANDS.put("restart-service", "重启服务（PowerShell）");
        DANGEROUS_COMMANDS.put("set-service", "配置服务属性（PowerShell）");
        DANGEROUS_COMMANDS.put("disable-netadapter", "禁用网络适配器（PowerShell）");
        DANGEROUS_COMMANDS.put("enable-netadapter", "启用网络适配器（PowerShell）");
        DANGEROUS_COMMANDS.put("new-netfirewallrule", "创建防火墙规则（PowerShell）");
        DANGEROUS_COMMANDS.put("remove-netfirewallrule", "删除防火墙规则（PowerShell）");
        DANGEROUS_COMMANDS.put("netsh ", "网络配置工具（Windows）");
        DANGEROUS_COMMANDS.put("mount-diskimage", "挂载磁盘映像（PowerShell）");
        DANGEROUS_COMMANDS.put("dismount-diskimage", "卸载磁盘映像（PowerShell）");
        DANGEROUS_COMMANDS.put("schtasks ", "管理计划任务（Windows）");
        DANGEROUS_COMMANDS.put("register-scheduledtask", "注册计划任务（PowerShell）");
        DANGEROUS_COMMANDS.put("unregister-scheduledtask", "取消注册计划任务（PowerShell）");
        DANGEROUS_COMMANDS.put("new-localuser", "创建本地用户（PowerShell）");
        DANGEROUS_COMMANDS.put("remove-localuser", "删除本地用户（PowerShell）");
        DANGEROUS_COMMANDS.put("set-localuser", "修改本地用户（PowerShell）");
        DANGEROUS_COMMANDS.put("net user ", "管理用户账户（Windows）");
        DANGEROUS_COMMANDS.put("net stop ", "停止服务（Windows）");
        DANGEROUS_COMMANDS.put("net start ", "启动服务（Windows）");
        DANGEROUS_COMMANDS.put("bcdedit", "编辑启动配置数据（Windows）");
        DANGEROUS_COMMANDS.put("reagentc", "配置 Windows 恢复环境");
    }

    /**
     * 检查命令是否需要二次确认
     * @param cmdName 命令名称（如 executeBash, executePowerShell）
     * @param cmdParam 命令参数（JSON字符串，包含实际执行的脚本内容）
     * @return 匹配到的危险命令描述，null表示不需要确认
     */
    public static CheckResult check(String cmdName, String cmdParam) {
        // 只对 executeBash 和 executePowerShell 命令进行检查
        if (!"executeBash".equals(cmdName) && !"executePowerShell".equals(cmdName)) {
            return null;
        }
        if (cmdParam == null || cmdParam.isEmpty()) {
            return null;
        }
        String lowerParam = cmdParam.toLowerCase();
        for (Map.Entry<String, String> entry : DANGEROUS_COMMANDS.entrySet()) {
            if (lowerParam.contains(entry.getKey().toLowerCase())) {
                return new CheckResult(entry.getKey(), entry.getValue());
            }
        }
        return null;
    }

    @Data
    public static class CheckResult {
        private final String matchedCommand;
        private final String description;

        public CheckResult(String matchedCommand, String description) {
            this.matchedCommand = matchedCommand;
            this.description = description;
        }
    }
}
