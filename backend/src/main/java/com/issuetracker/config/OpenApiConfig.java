package com.issuetracker.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI at /swagger-ui.html with an "Authorize" button for pasting a JWT. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI issueTrackerOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Issue Tracker API").version("1.0")
                        .description("JIRA-style issue tracker: projects, boards, sprints, issues, comments"))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
