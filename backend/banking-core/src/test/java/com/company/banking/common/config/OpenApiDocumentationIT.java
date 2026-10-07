package com.company.banking.common.config;

import com.company.banking.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiDocumentationIT extends IntegrationTest {

    @Test
    void institutionApiIsDocumentedWithAuthenticationAndErrorContract() {
        JsonNode spec = api.get("/v3/api-docs/institution", null).expect(200).body();

        assertThat(spec.at("/components/securitySchemes/bearerAuth/scheme").asString()).isEqualTo("bearer");
        assertThat(spec.at("/components/schemas/ApiErrorResponse").isMissingNode()).isFalse();
        assertThat(spec.at("/paths/~1api~1v1~1branches/post").isMissingNode()).isFalse();
        assertThat(spec.at("/paths/~1api~1v1~1branches/post/responses/409").isMissingNode()).isFalse();
        assertThat(spec.at("/paths/~1api~1v1~1platform~1tenants").isMissingNode()).isTrue();
    }

    @Test
    void platformApiIsDocumentedSeparately() {
        JsonNode spec = api.get("/v3/api-docs/platform", null).expect(200).body();
        assertThat(spec.at("/paths/~1api~1v1~1platform~1tenants/post").isMissingNode()).isFalse();
        assertThat(spec.at("/paths/~1api~1v1~1branches").isMissingNode()).isTrue();
    }
}
