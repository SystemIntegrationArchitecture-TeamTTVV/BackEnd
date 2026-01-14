package edu.iuh.fit.se.messegeservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI messageServiceOpenAPI() {
        Server localServer = new Server();
        localServer.setUrl("http://localhost:8082");
        localServer.setDescription("Local Development Server");

        Server gatewayServer = new Server();
        gatewayServer.setUrl("http://localhost:8080/api/message");
        gatewayServer.setDescription("API Gateway");

        Contact contact = new Contact();
        contact.setName("TTVV Social Network Team");
        contact.setEmail("support@ttvv.social");

        License license = new License()
                .name("MIT License")
                .url("https://choosealicense.com/licenses/mit/");

        Info info = new Info()
                .title("Message Service API")
                .version("1.0.0")
                .contact(contact)
                .description("API documentation for Message Service - manages conversations, messages, and calls")
                .license(license);

        return new OpenAPI()
                .info(info)
                .servers(List.of(localServer, gatewayServer));
    }
}
