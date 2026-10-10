package com.company.banking.notification.controller;

import com.company.banking.common.api.ApiResponse;
import com.company.banking.notification.service.StubSmsGateway;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Local development only ({@code banking.notification.sms.stub-inbox-enabled}): shows what the stub gateway would
 * have texted to a number, so the customer app can be tried without an SMS provider. Deployed environments refuse to
 * start with it enabled.
 */
@Hidden
@RestController
@RequestMapping("/api/v1/public/dev/sms")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "banking.notification.sms", name = "stub-inbox-enabled", havingValue = "true")
public class StubSmsInboxController {

    private final StubSmsGateway gateway;

    @GetMapping
    public ApiResponse<List<StubSmsGateway.SentMessage>> inbox(@RequestParam String phoneNumber) {
        return ApiResponse.ok(gateway.sentTo(phoneNumber.trim()));
    }
}
