package com.company.banking.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.banking.notification.service.StubSmsGateway;
import com.company.banking.support.Fixtures.TenantHandle;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/**
 * Drives the customer app's API in tests: sets up mobile banking for an existing customer (reading the texted code
 * from the stub SMS gateway) and signs in from a device.
 */
public final class CustomerApp {

    public static final String PASSWORD = "Kente-Weaver-2027";
    public static final String PIN = "2580";
    private static final Pattern CODE = Pattern.compile("is (\\d{6})");

    private final Api api;
    private final StubSmsGateway sms;

    public CustomerApp(Api api, StubSmsGateway sms) {
        this.api = api;
        this.sms = sms;
    }

    /**
     * A customer of the institution signed in on a device.
     */
    public record Session(String accessToken, String device, String phone) {
    }

    /**
     * Sets up mobile banking for the customer on {@code device} (the institution must have the customer app on).
     */
    public Session activate(TenantHandle tenant, String customerNumber, String phone, String device) {
        String token = api.fromDevice("POST", "/api/v1/customer/auth/activation", null, device, Map.of(
                        "institutionCode", tenant.code(), "customerNumber", customerNumber, "phoneNumber", phone))
                .expect(202).data().get("challengeToken").asString();
        Map<String, Object> completion = new LinkedHashMap<>();
        completion.put("challengeToken", token);
        completion.put("code", lastCode(phone));
        completion.put("password", PASSWORD);
        completion.put("pin", PIN);
        completion.put("deviceName", "Test phone");
        JsonNode tokens = api.fromDevice("POST", "/api/v1/customer/auth/activation/complete", null, device,
                completion).expect(200).data();
        return new Session(tokens.get("accessToken").asString(), device, phone);
    }

    public Api.Response get(Session session, String path) {
        return api.fromDevice("GET", path, session.accessToken(), session.device(), null);
    }

    public Api.Response send(Session session, String method, String path, Object body) {
        return api.fromDevice(method, path, session.accessToken(), session.device(), body);
    }

    /**
     * A money movement: like {@link #send} with an {@code Idempotency-Key}.
     */
    public Api.Response pay(Session session, String path, String idempotencyKey, Object body) {
        return api.fromDeviceIdempotent(path, session.accessToken(), session.device(), idempotencyKey, body);
    }

    public String lastMessage(String phone) {
        return sms.lastTo(phone).orElseThrow().body();
    }

    public String lastCode(String phone) {
        Matcher matcher = CODE.matcher(lastMessage(phone));
        assertThat(matcher.find()).as("the last text carries a code: %s", lastMessage(phone)).isTrue();
        return matcher.group(1);
    }
}
