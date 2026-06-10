package com.pengchangwei.stepserver.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("计步器后端接口文档")
                        .version("1.0.0")
                        .description("MyStepCounter 后端 RESTful API")
                        .contact(new Contact()
                                .name("Peng Changwei")
                                .email("pengchangwei@example.com")))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .name("Authorization")
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("登录后获取的JWT令牌，格式：Bearer xxx")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
