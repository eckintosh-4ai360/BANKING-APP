package com.company.banking.channel.controller;

import com.company.banking.channel.dto.NotificationDtos;
import com.company.banking.channel.service.CustomerNotificationService;
import com.company.banking.common.api.ApiResponse;
import com.company.banking.common.api.PageRequests;
import com.company.banking.common.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in customer's notifications: inbox, push registration and text alerts.
 */
@RestController
@RequestMapping("/api/v1/customer/notifications")
@RequiredArgsConstructor
@Tag(name = "Customer notifications")
public class CustomerNotificationController {

    private final CustomerNotificationService notificationService;

    @GetMapping
    @Operation(summary = "My notifications, newest first")
    public ApiResponse<PageResponse<NotificationDtos.Notification>> inbox(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(notificationService.inbox(PageRequests.of(page, size, Sort.unsorted())));
    }

    @GetMapping("/unread")
    @Operation(summary = "How many notifications I have not read")
    public ApiResponse<NotificationDtos.Unread> unread() {
        return ApiResponse.ok(notificationService.unread());
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Mark a notification read")
    public ApiResponse<NotificationDtos.Notification> markRead(@PathVariable UUID id) {
        return ApiResponse.ok(notificationService.markRead(id));
    }

    @PostMapping("/read")
    @Operation(summary = "Mark every notification read")
    public ApiResponse<Void> markAllRead() {
        notificationService.markAllRead();
        return ApiResponse.ok("All read", null);
    }

    @PutMapping("/push")
    @Operation(summary = "Receive push notifications on this device")
    public ApiResponse<Void> registerPush(@Valid @RequestBody NotificationDtos.PushRegistration request) {
        notificationService.registerPush(request);
        return ApiResponse.ok("Push notifications on", null);
    }

    @DeleteMapping("/push")
    @Operation(summary = "Stop push notifications on this device")
    public ApiResponse<Void> unregisterPush() {
        notificationService.unregisterPush();
        return ApiResponse.ok("Push notifications off", null);
    }

    @GetMapping("/preferences")
    @Operation(summary = "Whether I get text alerts for money in and out")
    public ApiResponse<NotificationDtos.Preferences> preferences() {
        return ApiResponse.ok(notificationService.preferences());
    }

    @PutMapping("/preferences")
    @Operation(summary = "Turn text alerts for money in and out on or off (security notices are always texted)")
    public ApiResponse<NotificationDtos.Preferences> updatePreferences(
            @Valid @RequestBody NotificationDtos.Preferences request) {
        return ApiResponse.ok(notificationService.updatePreferences(request));
    }
}
