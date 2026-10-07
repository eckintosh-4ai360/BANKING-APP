package com.company.banking.common.security;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BranchScopeTest {

    private final UUID accra = UUID.randomUUID();
    private final UUID tema = UUID.randomUUID();

    @Test
    void allBranchesPermitsEveryBranch() {
        assertThat(BranchScope.all().permits(accra)).isTrue();
        assertThat(BranchScope.all().permits(tema)).isTrue();
    }

    @Test
    void explicitScopePermitsOnlyListedBranches() {
        BranchScope scope = BranchScope.of(Set.of(accra));
        assertThat(scope.permits(accra)).isTrue();
        assertThat(scope.permits(tema)).isFalse();
        assertThat(scope.permits(null)).isFalse();
    }

    @Test
    void emptyScopePermitsNothing() {
        assertThat(BranchScope.none().permits(accra)).isFalse();
    }
}
