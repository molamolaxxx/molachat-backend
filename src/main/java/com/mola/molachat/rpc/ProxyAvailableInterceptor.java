package com.mola.molachat.rpc;

import com.alibaba.nacos.common.utils.Objects;
import com.mola.molachat.rpc.client.ReverseProxyService;
import com.mola.molachat.service.http.HttpService;
import com.mola.rpc.common.entity.RpcMetaData;
import com.mola.rpc.common.interceptor.ReverseProxyRegisterInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2023-06-03 14:45
 **/
@Component
@Slf4j
public class ProxyAvailableInterceptor extends ReverseProxyRegisterInterceptor {

    @Override
    public boolean intercept(RpcMetaData proxyProviderMetaData) {
        if (!proxyProviderMetaData.getInterfaceClazz().equals(ReverseProxyService.class)) {
            return false;
        }
        try {
            String res = HttpService.PROXY.get("https://google.com", "",
                    null, 5000);
            if (Objects.isNull(res)) {
                log.info("ProxyAvailableInterceptor res 为空");
                return true;
            }
        } catch (Exception e) {
            log.info("ProxyAvailableInterceptor 异常", e);
            return true;
        }
        log.info("ProxyAvailableInterceptor 代理正常");
        return false;
    }
}
