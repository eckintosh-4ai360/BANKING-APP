package com.company.banking.teller.controller;

import com.company.banking.common.api.ApiResponse;
import com.company.banking.teller.dto.CloseSessionRequest;
import com.company.banking.teller.dto.OpenSessionRequest;
import com.company.banking.teller.dto.SessionResponse;
import com.company.banking.teller.dto.SupervisorDecisionRequest;
import com.company.banking.teller.service.TellerSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/teller/sessions")
@RequiredArgsConstructor
@Tag(name = "Cash")
public class TellerSessionController {

    private final TellerSessionService sessions;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('teller.operate')")
    @Operation(summary = "Open a teller session on a drawer")
    public ApiResponse<SessionResponse> open(@Valid @RequestBody OpenSessionRequest request) {
        return ApiResponse.ok("Session opened", sessions.open(request));
    }

    @GetMapping("/me")
    @PreAuthorize("hasAuthority('teller.operate')")
    @Operation(summary = "Your open session and the cash your drawer should hold")
    public ApiResponse<SessionResponse> mine() {
        return ApiResponse.ok(sessions.mine());
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('teller.supervise', 'cash.view')")
    @Operation(summary = "Teller sessions of a business date (today by default) in the caller's branches")
    public ApiResponse<List<SessionResponse>> ofDate(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(sessions.ofDate(date));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('teller.operate', 'teller.supervise', 'cash.view')")
    @Operation(summary = "A teller session (tellers see their own)")
    public ApiResponse<SessionResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(sessions.get(id));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('teller.operate')")
    @Operation(summary = "Count the drawer and close the session; a difference waits for a supervisor")
    public ApiResponse<SessionResponse> close(@PathVariable UUID id, @Valid @RequestBody CloseSessionRequest request) {
        return ApiResponse.ok("Drawer counted", sessions.close(id, request));
    }

    @PostMapping("/{id}/accept-difference")
    @PreAuthorize("hasAuthority('teller.supervise')")
    @Operation(summary = "Accept a counted difference and post it (not for your own session)")
    public ApiResponse<SessionResponse> acceptDifference(@PathVariable UUID id,
                                                         @Valid @RequestBody SupervisorDecisionRequest request) {
        return ApiResponse.ok("Difference accepted", sessions.acceptDifference(id, request));
    }
}
