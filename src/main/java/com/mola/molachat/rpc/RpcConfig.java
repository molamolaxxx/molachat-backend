package com.mola.molachat.rpc;

import com.google.common.collect.Lists;
import com.mola.molachat.config.AppConfig;
import com.mola.molachat.rpc.client.ReverseProxyCallbackService;
import com.mola.molachat.rpc.client.ImageGenerateService;
import com.mola.molachat.rpc.client.ReverseProxyService;
import com.mola.rpc.common.annotation.RpcConsumer;
import com.mola.rpc.common.entity.RpcMetaData;
import com.mola.rpc.core.proto.RpcInvoker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2023-03-09 02:20
 **/
@Slf4j
@Configuration
public class RpcConfig implements InitializingBean {

    @RpcConsumer(reverseMode = true, timeout = 150000)
    private ReverseProxyService reverseProxyService;

    @RpcConsumer(reverseMode = true)
    private ImageGenerateService imageGenerateService;

    @RpcConsumer(appointedAddress = "120.27.230.24:9003")
    private ReverseProxyCallbackService reverseProxyCallbackService;

    @Resource
    private ReverseProxyServiceProvider reverseProxyServiceProvider;

    @Resource
    private ImageGenerateServiceProvider imageGenerateServiceProvider;

    @Resource
    private ReverseProxyCallbackServiceProvider reverseProxyCallbackServiceProvider;

    @Resource
    private AppConfig appConfig;

    @Override
    public void afterPropertiesSet() {
        if (appConfig.getStartProxyProvider()) {
            log.error("启动代理provider");
            try {
                RpcMetaData rpcMetaData = new RpcMetaData();
                rpcMetaData.setReverseMode(Boolean.TRUE);
                rpcMetaData.setReverseModeConsumerAddress(Lists.newArrayList("120.27.230.24:9003"));
                RpcInvoker.provider(
                        ReverseProxyService.class,
                        reverseProxyServiceProvider,
                        rpcMetaData
                );
            } catch (Exception e) {
                log.error("provider反向代理失败, msg = "+ e.getMessage());
            }
            try {
                RpcMetaData rpcMetaData = new RpcMetaData();
                rpcMetaData.setReverseMode(Boolean.TRUE);
                rpcMetaData.setReverseModeConsumerAddress(Lists.newArrayList("120.27.230.24:9003"));
                RpcInvoker.provider(
                        ImageGenerateService.class,
                        imageGenerateServiceProvider,
                        rpcMetaData
                );
            } catch (Exception e) {
                log.error("provider反向代理失败, msg = "+ e.getMessage());
            }
        }
        if (appConfig.getUseProxyConsumer()) {
            log.error("配置为使用代理，启动回调");
            try {
                RpcMetaData rpcMetaData = new RpcMetaData();
                RpcInvoker.provider(
                        ReverseProxyCallbackService.class,
                        reverseProxyCallbackServiceProvider,
                        rpcMetaData
                );
            } catch (Exception e) {
                log.error("回调服务启动失败, msg = "+ e.getMessage());
            }
        }
    }
}
