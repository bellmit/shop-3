package com.zhang.shop.config;

import org.apache.catalina.connector.Connector;
import org.apache.coyote.ProtocolHandler;
import org.apache.coyote.http11.Http11NioProtocol;
import org.springframework.boot.web.embedded.tomcat.TomcatConnectorCustomizer;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.embedded.undertow.ConfigurableUndertowWebServerFactory;
import org.springframework.boot.web.server.ConfigurableWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;

// 可以修改spring没有暴露给我们的参数
// 当Spring容器内没有TomcatEmbeddedServletContainerFactory这个bean时，会加载到spring
@Component
public class WebServerConfiguration implements WebServerFactoryCustomizer<ConfigurableWebServerFactory> {
    @Override
    public void customize(ConfigurableWebServerFactory factory) {
        // 使用对应工程类提供给我们的接口定制话我们的tomcat connector
        ((TomcatServletWebServerFactory)factory).addConnectorCustomizers(new TomcatConnectorCustomizer() {
            @Override
            public void customize(Connector connector) {
                Http11NioProtocol protocolHandler = (Http11NioProtocol) connector.getProtocolHandler();
                // 定制化keepaliveTimeOut 设置30s内没有请求自动断开
                protocolHandler.setKeepAliveTimeout(30000);
                // 当客户端发送超过10000个请求则自动断开连接
                protocolHandler.setMaxKeepAliveRequests(10000);

            }
        });
    }
}
