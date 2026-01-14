package edu.iuh.fit.se.apigateway.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class SwaggerConfig {

    @Bean
    @Primary
    public OpenAPI apiGatewayOpenAPI() {
        Contact contact = new Contact();
        contact.setName("TTVV Social Network Team");
        contact.setEmail("support@ttvv.social");

        License license = new License()
                .name("MIT License")
                .url("https://choosealicense.com/licenses/mit/");

        Info info = new Info()
                .title("TTVV Social Network API Gateway")
                .version("1.0.0")
                .contact(contact)
                .description("Unified API Gateway for TTVV Social Network - Access all services through one endpoint")
                .license(license);

        return new OpenAPI().info(info);
    }
}
