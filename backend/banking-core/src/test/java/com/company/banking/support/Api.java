package com.company.banking.support;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Thin JSON client over MockMvc for API tests.
 */
public final class Api {

    private final MockMvc mockMvc;
    private final JsonMapper jsonMapper;

    public Api(MockMvc mockMvc, JsonMapper jsonMapper) {
        this.mockMvc = mockMvc;
        this.jsonMapper = jsonMapper;
    }

    public Response get(String path, String token) {
        return perform(authorize(MockMvcRequestBuilders.get(path), token));
    }

    public Response post(String path, String token, Object body) {
        return perform(withBody(authorize(MockMvcRequestBuilders.post(path), token), body));
    }

    /**
     * A POST carrying an Idempotency-Key header (money movements).
     */
    public Response postIdempotent(String path, String token, String idempotencyKey, Object body) {
        MockHttpServletRequestBuilder builder = authorize(MockMvcRequestBuilders.post(path), token);
        if (idempotencyKey != null) {
            builder.header("Idempotency-Key", idempotencyKey);
        }
        return perform(withBody(builder, body));
    }

    /**
     * A request from an app installation: the {@code X-Device-Id} header identifies it (the customer app).
     */
    public Response fromDevice(String method, String path, String token, String deviceId, Object body) {
        MockHttpServletRequestBuilder builder = authorize(MockMvcRequestBuilders.request(
                org.springframework.http.HttpMethod.valueOf(method), path), token);
        if (deviceId != null) {
            builder.header("X-Device-Id", deviceId);
        }
        return perform(body == null && "GET".equals(method) ? builder : withBody(builder, body));
    }

    public Response put(String path, String token, Object body) {
        return perform(withBody(authorize(MockMvcRequestBuilders.put(path), token), body));
    }

    public Response delete(String path, String token) {
        return perform(authorize(MockMvcRequestBuilders.delete(path), token));
    }

    /**
     * Multipart upload of one file plus form parameters.
     */
    public Response upload(String path, String token, String fileName, byte[] content, String... params) {
        MockMultipartHttpServletRequestBuilder builder = MockMvcRequestBuilders.multipart(path)
                .file(new MockMultipartFile("file", fileName, MediaType.APPLICATION_OCTET_STREAM_VALUE, content));
        for (int i = 0; i + 1 < params.length; i += 2) {
            builder.param(params[i], params[i + 1]);
        }
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return perform(builder);
    }

    /**
     * Raw response bytes, for downloads.
     */
    public byte[] download(String path, String token) {
        try {
            MockHttpServletResponse response = mockMvc.perform(authorize(MockMvcRequestBuilders.get(path), token))
                    .andReturn().getResponse();
            assertThat(response.getStatus()).as("download status").isEqualTo(200);
            return response.getContentAsByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Download failed", ex);
        }
    }

    private static MockHttpServletRequestBuilder authorize(MockHttpServletRequestBuilder builder, String token) {
        return token == null ? builder : builder.header("Authorization", "Bearer " + token);
    }

    private MockHttpServletRequestBuilder withBody(MockHttpServletRequestBuilder builder, Object body) {
        builder.contentType(MediaType.APPLICATION_JSON);
        if (body != null) {
            builder.content(body instanceof String raw ? raw : jsonMapper.writeValueAsString(body));
        }
        return builder;
    }

    private Response perform(RequestBuilder builder) {
        try {
            MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
            String content = response.getContentAsString(StandardCharsets.UTF_8);
            JsonNode body = content.isBlank() ? jsonMapper.nullNode() : jsonMapper.readTree(content);
            Map<String, String> headers = new HashMap<>();
            response.getHeaderNames().forEach(name -> headers.put(name.toLowerCase(Locale.ROOT),
                    response.getHeader(name)));
            return new Response(response.getStatus(), body, headers);
        } catch (Exception ex) {
            throw new IllegalStateException("Request failed", ex);
        }
    }

    public record Response(int status, JsonNode body, Map<String, String> headers) {

        public String header(String name) {
            return headers.get(name.toLowerCase(Locale.ROOT));
        }

        public Response expect(int expectedStatus) {
            assertThat(status).as("HTTP status, body: %s", body).isEqualTo(expectedStatus);
            return this;
        }

        public Response expectError(int expectedStatus, String expectedCode) {
            expect(expectedStatus);
            assertThat(errorCode()).as("error code, body: %s", body).isEqualTo(expectedCode);
            return this;
        }

        public JsonNode data() {
            return body.get("data");
        }

        public String errorCode() {
            return body.path("code").asString();
        }
    }
}
