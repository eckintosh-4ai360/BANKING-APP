package com.company.banking.common.config;

import com.company.banking.common.api.ApiErrorResponse;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";
    private static final String ERROR_SCHEMA = "ApiErrorResponse";

    private static final Map<String, String> STANDARD_ERRORS = new LinkedHashMap<>();

    static {
        STANDARD_ERRORS.put("400", "VALIDATION_FAILED or MALFORMED_REQUEST");
        STANDARD_ERRORS.put("401", "UNAUTHENTICATED, SESSION_REVOKED");
        STANDARD_ERRORS.put("403", "ACCESS_DENIED, PASSWORD_CHANGE_REQUIRED");
        STANDARD_ERRORS.put("404", "RESOURCE_NOT_FOUND (also for other tenants' resources)");
        STANDARD_ERRORS.put("409", "DUPLICATE_RESOURCE, CONCURRENT_MODIFICATION");
        STANDARD_ERRORS.put("422", "Business rule violation with a specific code");
        STANDARD_ERRORS.put("429", "RATE_LIMITED");
        STANDARD_ERRORS.put("500", "INTERNAL_ERROR");
    }

    @Bean
    public OpenAPI bankingOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Banking Core API")
                        .version("v1")
                        .description("""
                                Multi-tenant digital banking and microfinance core.

                                * Authenticate with `POST /api/v1/auth/staff/login` (or the platform login) and send \
                                the access token as `Authorization: Bearer <token>`.
                                * The tenant is always taken from the token, never from request data.
                                * Money amounts are decimal strings. Every error has a stable machine-readable `code`.
                                """))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    @Bean
    public GroupedOpenApi institutionApi(OpenApiCustomizer standardErrorResponses) {
        return GroupedOpenApi.builder()
                .group("institution")
                .displayName("Institution API (staff)")
                .pathsToMatch("/api/v1/**")
                .pathsToExclude("/api/v1/platform/**")
                .addOpenApiCustomizer(standardErrorResponses)
                .build();
    }

    @Bean
    public GroupedOpenApi platformApi(OpenApiCustomizer standardErrorResponses) {
        return GroupedOpenApi.builder()
                .group("platform")
                .displayName("Platform API (super admin)")
                .pathsToMatch("/api/v1/platform/**", "/api/v1/auth/**")
                .addOpenApiCustomizer(standardErrorResponses)
                .build();
    }

    /**
     * Documents the shared error envelope on every operation instead of repeating annotations on each endpoint.
     */
    @Bean
    public OpenApiCustomizer standardErrorResponses() {
        return openApi -> {
            ResolvedSchema resolved = ModelConverters.getInstance()
                    .resolveAsResolvedSchema(new AnnotatedType(ApiErrorResponse.class));
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }
            openApi.getComponents().addSchemas(ERROR_SCHEMA, resolved.schema);
            if (resolved.referencedSchemas != null) {
                resolved.referencedSchemas.forEach(openApi.getComponents()::addSchemas);
            }
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().values().forEach(pathItem ->
                    pathItem.readOperations().forEach(OpenApiConfig::addStandardErrors));
        };
    }

    private static void addStandardErrors(Operation operation) {
        ApiResponses responses = operation.getResponses();
        STANDARD_ERRORS.forEach((status, description) -> {
            if (!responses.containsKey(status)) {
                responses.addApiResponse(status, new ApiResponse()
                        .description(description)
                        .content(new Content().addMediaType("application/json", new MediaType()
                                .schema(new Schema<>().$ref("#/components/schemas/" + ERROR_SCHEMA)))));
            }
        });
    }
}
