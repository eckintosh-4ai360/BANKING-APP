package com.company.banking.iam.security;

import com.company.banking.iam.entity.PrincipalType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenCodecTest {

    private final RefreshTokenCodec codec = new RefreshTokenCodec();

    @Test
    void staffTokensCarryTheTenant() {
        UUID tenantId = UUID.randomUUID();
        String token = codec.generate(PrincipalType.STAFF, tenantId);
        assertThat(token).startsWith("s." + tenantId + ".");
        assertThat(codec.parse(token)).hasValue(new RefreshTokenCodec.ParsedRefreshToken(PrincipalType.STAFF,
                tenantId));
    }

    @Test
    void platformTokensCarryNoTenant() {
        String token = codec.generate(PrincipalType.PLATFORM, null);
        assertThat(token).startsWith("p.");
        assertThat(codec.parse(token)).hasValue(new RefreshTokenCodec.ParsedRefreshToken(PrincipalType.PLATFORM,
                null));
    }

    @Test
    void customerTokensCarryTheTenantUnderTheirOwnPrefix() {
        UUID tenantId = UUID.randomUUID();
        String token = codec.generate(PrincipalType.CUSTOMER, tenantId);
        assertThat(token).startsWith("c." + tenantId + ".");
        assertThat(codec.parse(token)).hasValue(new RefreshTokenCodec.ParsedRefreshToken(PrincipalType.CUSTOMER,
                tenantId));
    }

    @Test
    void tokensAreRandomAndStoredOnlyAsHashes() {
        String first = codec.generate(PrincipalType.PLATFORM, null);
        String second = codec.generate(PrincipalType.PLATFORM, null);
        assertThat(first).isNotEqualTo(second);
        assertThat(codec.hash(first)).matches("^[0-9a-f]{64}$").isEqualTo(codec.hash(first))
                .isNotEqualTo(codec.hash(second));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "garbage", "p.", "p.short", "x.abc",
            "c.not-a-uuid.AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "s.not-a-uuid.AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            "s.00000000-0000-0000-0000-000000000000.too.many.parts"})
    void malformedTokensAreRejected(String token) {
        assertThat(codec.parse(token)).isEmpty();
    }
}
