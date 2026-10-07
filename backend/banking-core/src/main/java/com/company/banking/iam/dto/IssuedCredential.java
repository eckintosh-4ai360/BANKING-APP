package com.company.banking.iam.dto;

/**
 * A one-time temporary password, returned exactly once to the administrator who created or reset the credential.
 * It is never stored in plain text and the holder must change it at first login.
 */
public record IssuedCredential(String username, String temporaryPassword) {

    @Override
    public String toString() {
        return "IssuedCredential[username=" + username + ", temporaryPassword=***]";
    }
}
