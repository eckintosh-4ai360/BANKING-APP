package com.company.banking.iam.entity;

import com.company.banking.common.security.ActorType;

public enum PrincipalType {
    STAFF("staff", ActorType.STAFF),
    PLATFORM("platform", ActorType.PLATFORM_ADMIN);

    private final String audience;
    private final ActorType actorType;

    PrincipalType(String audience, ActorType actorType) {
        this.audience = audience;
        this.actorType = actorType;
    }

    /**
     * JWT {@code aud} value. Each API area accepts exactly one audience.
     */
    public String audience() {
        return audience;
    }

    public ActorType actorType() {
        return actorType;
    }

    public static PrincipalType fromAudience(String audience) {
        for (PrincipalType type : values()) {
            if (type.audience.equals(audience)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown audience " + audience);
    }
}
