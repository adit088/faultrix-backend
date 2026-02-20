package com.adit.mockDemo.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "ChaosLab API",
                version = "2.0.0",
                description = "Enterprise Chaos Engineering Platform - Multi-tenant API",
                contact = @Contact(
                        name = "ChaosLab Support",
                        email = "support@chaoslab.io"
                ),
                license = @License(name = "Proprietary")
        ),
        servers = {
                @Server(url = "http://localhost:8080", description = "Local"),
                @Server(url = "https://api.chaoslab.io", description = "Production")
        }
)
@SecurityScheme(
        name = "ApiKey",
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.HEADER,
        paramName = "X-API-Key",
        description = "API Key authentication. Include your organisation's API key in the X-API-Key header. " +
                "Keys are issued at organisation creation and are shown only once."
)
public class OpenApiConfig {
}