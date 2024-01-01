package com.mola.molachat.common.config;

import org.apache.catalina.Context;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2020-05-29 10:01
 * 自定义TomcatServletWebServerFactory
 **/
public class MyTomcatServletWebServerFactory extends TomcatServletWebServerFactory {

    @Override
    protected void postProcessContext(Context context) {
        SecurityConstraint securityConstraint=new SecurityConstraint();
        securityConstraint.setUserConstraint("CONFIDENTIAL");
        SecurityCollection collection=new SecurityCollection();
        collection.addPattern("/*");
        securityConstraint.addCollection(collection);
        context.addConstraint(securityConstraint);
    }
}
