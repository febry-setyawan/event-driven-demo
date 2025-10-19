package com.example.ordergateway.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class SwaggerConfig {

    @Value("${ORDER_SERVICE_URL:http://order-service:8081}")
    private String orderServiceUrl;

    @Value("${PAYMENT_SERVICE_URL:http://payment-service:8082}")
    private String paymentServiceUrl;

    @Bean
    @Primary
    public List<GroupedOpenApi> apis() {
        List<GroupedOpenApi> groups = new ArrayList<>();
        
        groups.add(GroupedOpenApi.builder()
            .group("order-service")
            .pathsToMatch("/api/orders/**")
            .build());
        
        groups.add(GroupedOpenApi.builder()
            .group("payment-service")
            .pathsToMatch("/api/payments/**")
            .build());
        
        return groups;
    }
}
