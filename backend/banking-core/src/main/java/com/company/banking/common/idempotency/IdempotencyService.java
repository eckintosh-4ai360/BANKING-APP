package com.company.banking.common.idempotency;

import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.CommonErrorCode;
import com.company.banking.common.id.UuidV7;
import com.company.banking.common.tenant.TenantContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Makes a state-changing request safe to retry (spec rule 11; decision D5).
 *
 * <p>The record is claimed, the action runs and the response is stored in the caller's transaction, so they commit
 * or roll back together:
 * <ul>
 *   <li>same key, same request, completed: the stored response is returned and the action does not run again;</li>
 *   <li>same key, different request: refused ({@code IDEMPOTENCY_KEY_REUSED});</li>
 *   <li>a concurrent duplicate waits on the key's unique index until the first commits, then gets its response;</li>
 *   <li>a request that failed (rolled back) left no record, so retrying it runs it again.</li>
 * </ul>
 */
@Service
public class IdempotencyService {

    public static final Pattern KEY_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{8,100}$");

    /**
     * @param scope        who and what the key belongs to, e.g. {@code STAFF:<id>:DEPOSIT}; keys are only unique
     *                     within a scope, so two clients can never collide
     * @param requestHash  {@link #hash(Object)} of the canonical request
     * @param resourceType kind of resource the request creates (for investigation), may be null
     */
    public record Request(String scope, String key, String requestHash, String resourceType) {
    }

    /**
     * @param replayed true when the response is the stored result of an earlier identical request
     */
    public record Result<T>(T response, boolean replayed) {
    }

    private final IdempotencyRepository repository;
    private final JsonMapper jsonMapper;
    private final JsonMapper canonicalMapper;
    private final Clock clock;
    private final Duration retention;

    public IdempotencyService(IdempotencyRepository repository, JsonMapper jsonMapper, Clock clock,
                              @Value("${banking.idempotency.retention:P1D}") Duration retention) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
        this.canonicalMapper = jsonMapper.rebuild()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .build();
        this.clock = clock;
        this.retention = retention;
    }

    public static void requireValidKey(String key) {
        if (key == null || key.isBlank()) {
            throw new BankingException(CommonErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }
        if (!KEY_PATTERN.matcher(key).matches()) {
            throw new BankingException(CommonErrorCode.INVALID_IDEMPOTENCY_KEY);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public <T> Result<T> execute(Request request, Class<T> responseType, Function<T, UUID> resourceId,
                                 Supplier<T> action) {
        requireValidKey(request.key());
        UUID tenantId = TenantContext.requireTenantId();
        Instant now = clock.instant();
        repository.deleteExpired(tenantId, request.scope(), request.key(), now);
        UUID recordId = UuidV7.next();
        if (repository.tryInsert(recordId, tenantId, request.scope(), request.key(), request.requestHash(),
                request.resourceType(), now, now.plus(retention))) {
            T response = action.get();
            repository.complete(recordId, jsonMapper.writeValueAsString(response),
                    resourceId == null ? null : resourceId.apply(response), clock.instant());
            return new Result<>(response, false);
        }
        IdempotencyRepository.StoredRecord existing = repository.lock(tenantId, request.scope(), request.key())
                .orElseThrow(() -> new BankingException(CommonErrorCode.IDEMPOTENT_REQUEST_IN_PROGRESS));
        if (!existing.requestHash().equals(request.requestHash())) {
            throw new BankingException(CommonErrorCode.IDEMPOTENCY_KEY_REUSED);
        }
        if (!"COMPLETED".equals(existing.status()) || existing.responseBody() == null) {
            throw new BankingException(CommonErrorCode.IDEMPOTENT_REQUEST_IN_PROGRESS);
        }
        return new Result<>(jsonMapper.readValue(existing.responseBody(), responseType), true);
    }

    /**
     * SHA-256 of the request's canonical JSON (properties and map keys sorted), as lower-case hex.
     */
    public String hash(Object canonicalRequest) {
        byte[] json = canonicalMapper.writeValueAsString(canonicalRequest).getBytes(StandardCharsets.UTF_8);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }

    /**
     * Removes expired records of the current tenant (housekeeping).
     */
    @Transactional
    public int purgeExpired(int limit) {
        return repository.purgeExpired(TenantContext.requireTenantId(), clock.instant(), limit);
    }
}
