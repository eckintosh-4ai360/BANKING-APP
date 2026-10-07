package com.company.banking.common.persistence;

import com.company.banking.common.tenant.TenantContext;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * Starts every database transaction by publishing the current tenant to PostgreSQL:
 * {@code set_config('app.tenant_id', <tenant>, true)}. The setting is transaction-local, so pooled connections
 * never carry a tenant into the next transaction. Row-level security policies read it through
 * {@code core.current_tenant_id()}; when no tenant is bound the setting is empty and tenant rows are invisible.
 */
public class TenantAwareJpaTransactionManager extends JpaTransactionManager {

    private static final String SET_TENANT_SQL = "SELECT set_config('app.tenant_id', :tenantId, true)";

    public TenantAwareJpaTransactionManager(EntityManagerFactory entityManagerFactory) {
        super(entityManagerFactory);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);
        EntityManagerHolder holder =
                (EntityManagerHolder) TransactionSynchronizationManager.getResource(obtainEntityManagerFactory());
        if (holder == null) {
            throw new IllegalStateException("No EntityManager bound after transaction begin");
        }
        String tenantId = TenantContext.currentTenantId().map(UUID::toString).orElse("");
        holder.getEntityManager()
                .createNativeQuery(SET_TENANT_SQL)
                .setParameter("tenantId", tenantId)
                .getSingleResult();
    }
}
