package com.mola.molachat.rpc;

import com.google.common.collect.Lists;
import com.mola.molachat.config.AppConfig;
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
public class ConsumerConfig implements InitializingBean {

    @RpcConsumer(reverseMode = true)
    private ReverseProxyService reverseProxyService;

    @Resource
    private ReverseProxyServiceProvider reverseProxyServiceProvider;

    @Resource
    private AppConfig appConfig;

    @Override
    public void afterPropertiesSet() throws Exception {
        if (!appConfig.getIsRpcProxyClient()) {
            log.error("配置为代理消费，不启动provider");
            return;
        }
        RpcMetaData rpcMetaData = new RpcMetaData();
        rpcMetaData.setReverseMode(Boolean.TRUE);
        rpcMetaData.setReverseModeConsumerAddress(Lists.newArrayList("120.27.230.24:9003"));
        RpcInvoker.provider(
                ReverseProxyService.class,
                reverseProxyServiceProvider,
                rpcMetaData
        );
    }
}
