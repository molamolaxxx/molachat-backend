package com.mola.molachat.server.config;

import com.mola.molachat.common.config.AppConfig;
import com.mola.molachat.server.spring.SpringWebSocketChatServer;
import com.mola.molachat.server.spring.SpringWebSocketInterceptor;
import com.mola.molachat.server.tomcat.TomcatChatServer;
import com.mola.molachat.common.utils.BeanUtilsPlug;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.HashSet;
import java.util.Set;

/**
 * @Author: molamola
 * @Date: 19-8-6 上午11:09
 * @Version 1.0
 */
@Configuration
@EnableWebSocket
@Slf4j
public class ServerConfig implements WebSocketConfigurer, InitializingBean {

    @Resource
    private AppConfig appConfig;

    @Resource
    private ApplicationContext applicationContext;

    private static Set<String> typeSet = new HashSet<>();
    static {
        typeSet.add("spring");
        typeSet.add("tomcat");
        typeSet.add("netty");
    }

    @PostConstruct
    public void init() {
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        if ("spring".equals(appConfig.getServerType())) {
            log.info("[molachat] 使用了spring提供的websocket引擎");
            registry.addHandler(new SpringWebSocketChatServer(),"/server/{chatterId}")
                    .setAllowedOrigins("*")
                    .addInterceptors(new SpringWebSocketInterceptor());
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if ("tomcat".equals(appConfig.getServerType())) {
            log.info("[molachat] 使用了tomcat提供的websocket引擎");
            BeanUtilsPlug.registerBean("tomcatChatServer", TomcatChatServer.class, () -> new TomcatChatServer(), applicationContext);
            BeanUtilsPlug.registerBean("serverEndpointExporter", ServerEndpointExporter.class, () -> new ServerEndpointExporter(), applicationContext);
            // 动态添加beanDefination后，如果需要执行钩子函数，需要重新调用beanFactory.preInstantiateSingletons方法
            // 因为第一次执行
            DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory)applicationContext.getAutowireCapableBeanFactory();
            beanFactory.preInstantiateSingletons();
        }
    }
}
