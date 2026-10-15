package tz.ppra.ppraAMS.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        bearerFormat = "JWT",
        scheme = "bearer"
)
public class OpenApiConfig {

    @Bean
    public OpenAPI tpmsOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("USER SERVICE API")
                        .description("Special for user authentication API")
                        .version("v1.0"))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
