package com.mola.molachat.common.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @Author: molamola
 * @Date: 19-8-5 下午2:42
 * @Version 1.0
 * web配置项
 */
@Configuration
public class ChatMvcConfig implements WebMvcConfigurer{

    @Autowired
    private SelfConfig config;

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("index");
        // 摄像头测试页面
        registry.addViewController("/test").setViewName("camera");
    }

//    @Override
//    public void addResourceHandlers(ResourceHandlerRegistry registry) {
//        registry.addResourceHandler("/files/**")
//                .addResourceLocations("file:"+config.getUploadFilePath()+ File.separator);
//    }

//    @Bean
//    public EmbeddedWebServerFactoryCustomizerAutoConfiguration containerCustomizer(){
//        return new EmbeddedWebServerFactoryCustomizerAutoConfiguration();
//    }


}
