package com.company.banking.channel.controller;

import com.company.banking.channel.dto.ChannelSettingsDtos;
import com.company.banking.channel.service.ChannelSettingsService;
import com.company.banking.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The institution's limits for transfers from the customer app (staff).
 */
@RestController
@RequestMapping("/api/v1/channel-settings")
@RequiredArgsConstructor
@Tag(name = "Customer app settings")
public class ChannelSettingsController {

    private final ChannelSettingsService settingsService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('settings.view', 'settings.manage')")
    @Operation(summary = "Limits for transfers from the customer app")
    public ApiResponse<ChannelSettingsDtos.Settings> get() {
        return ApiResponse.ok(settingsService.get());
    }

    @PutMapping
    @PreAuthorize("hasAuthority('settings.manage')")
    @Operation(summary = "Change the limits for transfers from the customer app")
    public ApiResponse<ChannelSettingsDtos.Settings> update(@Valid @RequestBody ChannelSettingsDtos.Update request) {
        return ApiResponse.ok("Settings saved", settingsService.update(request));
    }
}
