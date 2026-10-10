package com.company.banking.channel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Requests and responses of the customer channel. Requests that carry a password, PIN or code never print them.
 */
public final class ChannelDtos {

    private static final String CODE = "^[0-9]{6}$";
    private static final String PIN = "^[0-9]{4,6}$";

    private ChannelDtos() {
    }

    // ------------------------------------------------------------------------------------------- sign-in

    /**
     * Sign-in with the phone number and password. The installation is identified by the {@code X-Device-Id} header;
     * a device the customer has not trusted yet gets a code by text instead of tokens.
     */
    public record Login(
            @NotBlank @Size(max = 32) String institutionCode,
            @NotBlank @Size(max = 30) String phoneNumber,
            @NotBlank @Size(max = 128) String password,
            @Size(max = 100) String deviceName,
            @Size(max = 20) String platform) {

        @Override
        public String toString() {
            return "Login[institutionCode=" + institutionCode + ", phoneNumber=***, password=***]";
        }
    }

    /**
     * Trusts the installation with the code texted at sign-in.
     */
    public record DeviceVerification(
            @NotBlank @Size(max = 80) String challengeToken,
            @NotBlank @Pattern(regexp = CODE) String code,
            @Size(max = 100) String deviceName,
            @Size(max = 20) String platform) {

        @Override
        public String toString() {
            return "DeviceVerification[***]";
        }
    }

    /**
     * An existing customer (onboarded at a branch) sets up mobile banking: the number on their customer card and the
     * phone number on file.
     */
    public record Activation(
            @NotBlank @Size(max = 32) String institutionCode,
            @NotBlank @Size(max = 30) String customerNumber,
            @NotBlank @Size(max = 30) String phoneNumber) {

        @Override
        public String toString() {
            return "Activation[institutionCode=" + institutionCode + ", customerNumber=***, phoneNumber=***]";
        }
    }

    /**
     * Sign-up in the app for someone who is not a customer yet: a code is texted to the phone number.
     */
    public record SignUp(
            @NotBlank @Size(max = 32) String institutionCode,
            @NotBlank @Size(max = 30) String phoneNumber) {

        @Override
        public String toString() {
            return "SignUp[institutionCode=" + institutionCode + ", phoneNumber=***]";
        }
    }

    /**
     * Registers with the texted code: who they are, their password and transaction PIN.
     */
    public record SignUpCompletion(
            @NotBlank @Size(max = 80) String challengeToken,
            @NotBlank @Pattern(regexp = CODE) String code,
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotNull @Past LocalDate dateOfBirth,
            @NotBlank @Size(max = 128) String password,
            @NotBlank @Pattern(regexp = PIN) String pin,
            @Size(max = 100) String deviceName,
            @Size(max = 20) String platform) {

        @Override
        public String toString() {
            return "SignUpCompletion[***]";
        }
    }

    public record ActivationCompletion(
            @NotBlank @Size(max = 80) String challengeToken,
            @NotBlank @Pattern(regexp = CODE) String code,
            @NotBlank @Size(max = 128) String password,
            @NotBlank @Pattern(regexp = PIN) String pin,
            @Size(max = 100) String deviceName,
            @Size(max = 20) String platform) {

        @Override
        public String toString() {
            return "ActivationCompletion[***]";
        }
    }

    /**
     * Where a code went. The same answer is given whether or not the request matched a customer.
     *
     * @param sentTo the phone number with most digits hidden
     */
    public record CodeSent(String challengeToken, String sentTo, Instant expiresAt) {
    }

    public record PasswordReset(
            @NotBlank @Size(max = 32) String institutionCode,
            @NotBlank @Size(max = 30) String phoneNumber) {

        @Override
        public String toString() {
            return "PasswordReset[institutionCode=" + institutionCode + ", phoneNumber=***]";
        }
    }

    /**
     * Resetting the password needs the texted code and the transaction PIN, so a stolen SIM alone is not enough.
     */
    public record PasswordResetCompletion(
            @NotBlank @Size(max = 80) String challengeToken,
            @NotBlank @Pattern(regexp = CODE) String code,
            @NotBlank @Pattern(regexp = PIN) String pin,
            @NotBlank @Size(max = 128) String newPassword) {

        @Override
        public String toString() {
            return "PasswordResetCompletion[***]";
        }
    }

    public record PasswordChange(
            @NotBlank @Size(max = 128) String currentPassword,
            @NotBlank @Size(max = 128) String newPassword) {

        @Override
        public String toString() {
            return "PasswordChange[***]";
        }
    }

    // ---------------------------------------------------------------------------------------------- PIN

    public record PinChange(@NotBlank @Pattern(regexp = PIN) String currentPin,
                            @NotBlank @Pattern(regexp = PIN) String newPin) {

        @Override
        public String toString() {
            return "PinChange[***]";
        }
    }

    /**
     * Resetting a forgotten or locked PIN needs the texted code and the password.
     */
    public record PinResetCompletion(
            @NotBlank @Size(max = 80) String challengeToken,
            @NotBlank @Pattern(regexp = CODE) String code,
            @NotBlank @Size(max = 128) String password,
            @NotBlank @Pattern(regexp = PIN) String newPin) {

        @Override
        public String toString() {
            return "PinResetCompletion[***]";
        }
    }

    // --------------------------------------------------------------------------------------------- views

    public record Profile(UUID customerId, String customerNumber, String displayName, String phoneNumber,
                          String status, String kycStatus, String kycTierCode, Instant lastLoginAt,
                          boolean pinLocked) {
    }

    /**
     * @param current the installation making the request
     */
    public record Device(UUID id, String name, String platform, String status, Instant boundAt, Instant lastSeenAt,
                         Instant revokedAt, boolean current) {
    }

    /**
     * A sign-in session (login history).
     */
    public record Session(UUID id, UUID deviceId, String deviceName, String status, Instant createdAt,
                          Instant lastActiveAt, Instant revokedAt, String endedBecause, String ipAddress,
                          boolean current) {
    }
}
