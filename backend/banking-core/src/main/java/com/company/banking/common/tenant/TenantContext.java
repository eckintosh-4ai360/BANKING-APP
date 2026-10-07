package com.company.banking.common.tenant;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The tenant on whose behalf the current thread is working.
 *
 * <p>For authenticated requests it is populated <b>only</b> from the verified access token, never from request
 * input. The transaction manager copies it into the PostgreSQL setting {@code app.tenant_id} at the start of
 * every transaction, where row-level security enforces it. An empty context means "platform context": tenant
 * rows are invisible.
 *
 * <p>Switching tenant inside an active transaction is refused, because the database setting of that transaction
 * would no longer match.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static Optional<UUID> currentTenantId() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static UUID requireTenantId() {
        UUID tenantId = CURRENT.get();
        if (tenantId == null) {
            throw new IllegalStateException("No tenant is bound to the current thread");
        }
        return tenantId;
    }

    /**
     * Binds the tenant for the remainder of the request. Callers must {@link #clear()} in a finally block.
     */
    public static void bind(UUID tenantId) {
        assertNoActiveTransaction();
        CURRENT.set(tenantId);
    }

    public static void clear() {
        CURRENT.remove();
    }

    /**
     * Runs {@code action} as {@code tenantId} (or in platform context when {@code null}) and restores the
     * previous context afterwards. Reserved for provisioning and pre-authentication flows; see ArchitectureTest.
     */
    public static <T> T callAs(UUID tenantId, Supplier<T> action) {
        assertNoActiveTransaction();
        UUID previous = CURRENT.get();
        set(tenantId);
        try {
            return action.get();
        } finally {
            set(previous);
        }
    }

    public static void runAs(UUID tenantId, Runnable action) {
        callAs(tenantId, () -> {
            action.run();
            return null;
        });
    }

    private static void set(UUID tenantId) {
        if (tenantId == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(tenantId);
        }
    }

    private static void assertNoActiveTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("The tenant context cannot change inside an active transaction");
        }
    }
}
