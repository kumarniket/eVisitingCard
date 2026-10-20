package com.krniket.platform.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean
    OpenAPI eCardMitraApi() {
        return new OpenAPI().info(new Info()
                .title("e-CardMitra Visiting Card API")
                .version("v1")
                .description("REST API for vendor profiles, moderated shops, products, and public visiting cards.")
                .contact(new Contact().name("e-CardMitra Platform")))
                .schemaRequirement("FirebaseBearer", new SecurityScheme().type(SecurityScheme.Type.HTTP)
                        .scheme("bearer").bearerFormat("Firebase ID token"));
    }
}
