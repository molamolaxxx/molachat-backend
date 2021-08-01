package com.mola.molachat.common;

import com.mola.molachat.config.SelfConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AfterRunningHandler implements CommandLineRunner {

    @Autowired
    private SelfConfig config;

    @Override
    public void run(String... args) throws Exception {
        //打印相关配置
        System.out.println("**********************************  molachat 相关配置输出 **********************************");
        System.out.println("检查连接的超时时间 : "+config.getCONNECT_TIMEOUT());
        System.out.println("断开链接时间 : "+config.getCLOSE_TIMEOUT());
        System.out.println("最大客户端数量 : "+config.getMAX_CLIENT_NUM());
        System.out.println("每个会话最大保存信息数 : "+config.getMAX_SESSION_MESSAGE_NUM());
        System.out.println("上传文件保存地址，供下载管理 : "+config.getUploadFilePath());
        System.out.println("最大上传文件大小（单位:m） : "+config.getMaxFileSize());
        System.out.println("最大请求文件大小(单位:m) : "+config.getMaxRequestSize());
        System.out.println("**********************************  molachat 相关配置输出 **********************************");
    }
}
