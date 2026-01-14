package edu.iuh.fit.se.apigateway.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class OpenApiConfig {

    @Autowired
    private RouteDefinitionLocator locator;

    @Bean
    public List<GroupedOpenApi> apis() {
        List<GroupedOpenApi> groups = new ArrayList<>();
        
        // CommonService API Group
        groups.add(GroupedOpenApi.builder()
                .group("common-service")
                .pathsToMatch("/api/common/**")
                .build());
        
        // MessageService API Group
        groups.add(GroupedOpenApi.builder()
                .group("message-service")
                .pathsToMatch("/api/message/**")
                .build());
        
        return groups;
    }
}
