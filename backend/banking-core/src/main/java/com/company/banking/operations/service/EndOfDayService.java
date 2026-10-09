package com.company.banking.operations.service;

import com.company.banking.audit.service.AuditEvent;
import com.company.banking.audit.service.AuditService;
import com.company.banking.common.api.PageResponse;
import com.company.banking.common.eod.EndOfDayCheck;
import com.company.banking.common.eod.EndOfDayContext;
import com.company.banking.common.eod.EndOfDayProbe;
import com.company.banking.common.eod.EndOfDayStep;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.ResourceNotFoundException;
import com.company.banking.common.id.UuidV7;
import com.company.banking.common.persistence.ClusterLock;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.ledger.service.BusinessDateService;
import com.company.banking.operations.dto.EodRunResponse;
import com.company.banking.operations.entity.EodRun;
import com.company.banking.operations.entity.EodStepRecord;
import com.company.banking.operations.exception.OperationsErrorCode;
import com.company.banking.operations.repository.EodRunRepository;
import com.company.banking.operations.repository.EodStepRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * End-of-day: closes the current business date.
 *
 * <ol>
 *   <li>Checks contributed by modules must pass (e.g. every teller session closed).</li>
 *   <li>In one transaction the run and its steps are recorded and the business date rolls to the next working day,
 *   so branches carry on while the closed date is processed.</li>
 *   <li>The steps run in order for the closed date, each committing its own batches. A failure (or a crash) stops
 *   the run; resuming re-runs the unfinished steps, which skip what they already did.</li>
 * </ol>
 * Only one worker per institution executes a run at a time (cluster lock); steps run as the system actor.
 */
@Slf4j
@Service
public class EndOfDayService {

    private static final String RESOURCE = "EOD_RUN";

    private final EodRunRepository runs;
    private final EodStepRepository stepRecords;
    private final ObjectProvider<EndOfDayStep> steps;
    private final ObjectProvider<EndOfDayCheck> checks;
    private final ObjectProvider<EndOfDayProbe> probes;
    private final BusinessDateService businessDates;
    private final BusinessCalendarService calendar;
    private final ClusterLock clusterLock;
    private final TransactionTemplate transactions;
    private final AuditService auditService;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final boolean async;

    @SuppressWarnings("java:S107")
    public EndOfDayService(EodRunRepository runs, EodStepRepository stepRecords, ObjectProvider<EndOfDayStep> steps,
                           ObjectProvider<EndOfDayCheck> checks, ObjectProvider<EndOfDayProbe> probes,
                           BusinessDateService businessDates, BusinessCalendarService calendar,
                           ClusterLock clusterLock, PlatformTransactionManager transactionManager,
                           AuditService auditService, JsonMapper jsonMapper, Clock clock,
                           @Value("${banking.eod.async:true}") boolean async) {
        this.runs = runs;
        this.stepRecords = stepRecords;
        this.steps = steps;
        this.checks = checks;
        this.probes = probes;
        this.businessDates = businessDates;
        this.calendar = calendar;
        this.clusterLock = clusterLock;
        this.transactions = new TransactionTemplate(transactionManager);
        this.auditService = auditService;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
        this.async = async;
    }

    /**
     * Closes the current business date. Returns once the date has rolled; the steps run in the background (or
     * inline when {@code banking.eod.async=false}, as in tests).
     */
    public EodRunResponse start() {
        UUID tenantId = TenantContext.requireTenantId();
        UUID actor = CurrentActor.currentActorId().orElse(null);
        UUID runId = UuidV7.next();
        try {
            // Everything that decides the run happens in its transaction: tenant rows are only visible there.
            transactions.executeWithoutResult(status -> {
                runs.findFirstByTenantIdAndStatusNot(tenantId, EodRun.Status.COMPLETED).ifPresent(unfinished -> {
                    throw new BankingException(OperationsErrorCode.EOD_ALREADY_RUNNING,
                            "End-of-day for " + unfinished.getBusinessDate() + " has not finished. Resume it first.");
                });
                LocalDate closing = businessDates.today();
                List<String> problems = checks.orderedStream().flatMap(check -> check.problems(closing).stream())
                        .toList();
                if (!problems.isEmpty()) {
                    throw new BankingException(OperationsErrorCode.EOD_CHECKS_FAILED,
                            "The business date cannot close yet: " + String.join("; ", problems));
                }
                LocalDate next = calendar.nextBusinessDate(closing);
                runs.saveAndFlush(new EodRun(runId, tenantId, closing, next, clock.instant(), actor));
                for (EndOfDayStep step : orderedSteps()) {
                    stepRecords.save(new EodStepRecord(runId, tenantId, step.code(), step.order()));
                }
                stepRecords.flush();
                businessDates.roll(closing, next);
                auditService.record(AuditEvent.builder("EOD_STARTED", RESOURCE)
                        .resourceId(runId)
                        .metadata("businessDate", closing.toString())
                        .metadata("nextBusinessDate", next.toString())
                        .build());
            });
        } catch (DataIntegrityViolationException concurrent) {
            throw new BankingException(OperationsErrorCode.EOD_ALREADY_RUNNING);
        }
        dispatch(runId);
        return get(runId);
    }

    /**
     * Resumes a failed run (or one whose worker died): the unfinished steps run again.
     */
    public EodRunResponse resume(UUID runId) {
        UUID tenantId = TenantContext.requireTenantId();
        transactions.executeWithoutResult(status -> {
            EodRun run = runs.findByTenantIdAndId(tenantId, runId)
                    .orElseThrow(() -> new ResourceNotFoundException("End-of-day run"));
            if (run.getStatus() == EodRun.Status.COMPLETED) {
                throw new BankingException(OperationsErrorCode.EOD_NOTHING_TO_RESUME);
            }
            run.resume();
            runs.saveAndFlush(run);
            auditService.record(AuditEvent.builder("EOD_RESUMED", RESOURCE)
                    .resourceId(runId)
                    .metadata("attempt", run.getAttempts())
                    .build());
        });
        dispatch(runId);
        return get(runId);
    }

    public EodRunResponse get(UUID runId) {
        UUID tenantId = TenantContext.requireTenantId();
        return transactions.execute(status -> toResponse(runs.findByTenantIdAndId(tenantId, runId)
                .orElseThrow(() -> new ResourceNotFoundException("End-of-day run"))));
    }

    public PageResponse<EodRunResponse> list(PageRequest page) {
        UUID tenantId = TenantContext.requireTenantId();
        return transactions.execute(status -> PageResponse.from(
                runs.findByTenantIdOrderByBusinessDateDesc(tenantId, page), this::toResponse));
    }

    // ---------------------------------------------------------------------------------------------------------

    private void dispatch(UUID runId) {
        UUID tenantId = TenantContext.requireTenantId();
        Runnable work = () -> execute(tenantId, runId);
        if (async) {
            Thread.ofVirtual().name("eod-" + runId).start(TenantContext.propagating(work));
        } else {
            work.run();
        }
    }

    private void execute(UUID tenantId, UUID runId) {
        boolean ran = clusterLock.runExclusively("eod:" + tenantId,
                () -> CurrentActor.callAsSystem(tenantId, () -> {
                    executeSteps(tenantId, runId);
                    return null;
                }));
        if (!ran) {
            log.info("End-of-day run {} is already being executed by another worker", runId);
        }
    }

    private void executeSteps(UUID tenantId, UUID runId) {
        EodRun run = transactions.execute(status -> runs.findByTenantIdAndId(tenantId, runId).orElseThrow());
        if (run == null || run.getStatus() != EodRun.Status.RUNNING) {
            return;
        }
        Map<String, EndOfDayStep> available = orderedSteps().stream()
                .collect(Collectors.toMap(EndOfDayStep::code, Function.identity()));
        Context context = new Context(runId, run.getBusinessDate(), run.getNextBusinessDate());
        List<EodStepRecord> pending = transactions.execute(status -> stepRecords.findOfRun(tenantId, runId)).stream()
                .filter(record -> record.getStatus() != EodStepRecord.Status.DONE)
                .toList();
        for (EodStepRecord record : pending) {
            String code = record.getStepCode();
            updateStep(tenantId, runId, code, step -> step.start(clock.instant()));
            try {
                context.checkpoint(code, "before");
                EndOfDayStep step = available.get(code);
                if (step == null) {
                    throw new IllegalStateException("No end-of-day step " + code + " is available");
                }
                Map<String, Object> result = step.run(context);
                String json = jsonMapper.writeValueAsString(result == null ? Map.of() : result);
                updateStep(tenantId, runId, code, done -> done.done(json, clock.instant()));
            } catch (RuntimeException failure) {
                String message = safeMessage(failure);
                log.warn("End-of-day step {} of run {} failed: {}", code, runId, failure.getClass().getSimpleName());
                updateStep(tenantId, runId, code, failed -> failed.fail(message, clock.instant()));
                transactions.executeWithoutResult(status -> {
                    EodRun current = runs.findByTenantIdAndId(tenantId, runId).orElseThrow();
                    current.fail(code, message);
                    runs.saveAndFlush(current);
                    auditService.record(AuditEvent.builder("EOD_FAILED", RESOURCE)
                            .resourceId(runId)
                            .metadata("step", code)
                            .metadata("message", message)
                            .build());
                });
                return;
            }
        }
        transactions.executeWithoutResult(status -> {
            EodRun current = runs.findByTenantIdAndId(tenantId, runId).orElseThrow();
            current.complete(clock.instant());
            runs.saveAndFlush(current);
            auditService.record(AuditEvent.builder("EOD_COMPLETED", RESOURCE)
                    .resourceId(runId)
                    .metadata("businessDate", current.getBusinessDate().toString())
                    .build());
        });
    }

    private void updateStep(UUID tenantId, UUID runId, String code, Consumer<EodStepRecord> change) {
        transactions.executeWithoutResult(status -> {
            EodStepRecord record = stepRecords.findById(new EodStepRecord.Key(runId, code))
                    .filter(found -> found.getTenantId().equals(tenantId))
                    .orElseThrow();
            change.accept(record);
            stepRecords.saveAndFlush(record);
        });
    }

    private List<EndOfDayStep> orderedSteps() {
        List<EndOfDayStep> all = new ArrayList<>(steps.orderedStream().toList());
        all.sort(Comparator.comparingInt(EndOfDayStep::order).thenComparing(EndOfDayStep::code));
        return all;
    }

    /**
     * Business errors carry a message meant for people; anything else is reported by type only, so no data from an
     * unexpected exception ends up in the run record.
     */
    private static String safeMessage(RuntimeException failure) {
        String message = failure instanceof BankingException ? failure.getMessage()
                : "Unexpected " + failure.getClass().getSimpleName();
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private EodRunResponse toResponse(EodRun run) {
        List<EodRunResponse.Step> stepResponses = stepRecords.findOfRun(run.getTenantId(), run.getId()).stream()
                .map(step -> new EodRunResponse.Step(step.getStepCode(), step.getStepOrder(), step.getStatus().name(),
                        step.getResult() == null ? null : jsonMapper.readTree(step.getResult()), step.getAttempts(),
                        step.getStartedAt(), step.getFinishedAt(), step.getError()))
                .toList();
        return new EodRunResponse(run.getId(), run.getBusinessDate(), run.getNextBusinessDate(), run.getStatus().name(),
                run.getStartedAt(), run.getStartedBy(), run.getFinishedAt(), run.getFailedStep(),
                run.getFailureMessage(), run.getAttempts(), stepResponses);
    }

    private final class Context implements EndOfDayContext {

        private final UUID runId;
        private final LocalDate businessDate;
        private final LocalDate nextBusinessDate;

        private Context(UUID runId, LocalDate businessDate, LocalDate nextBusinessDate) {
            this.runId = runId;
            this.businessDate = businessDate;
            this.nextBusinessDate = nextBusinessDate;
        }

        @Override
        public UUID runId() {
            return runId;
        }

        @Override
        public LocalDate businessDate() {
            return businessDate;
        }

        @Override
        public LocalDate nextBusinessDate() {
            return nextBusinessDate;
        }

        @Override
        public <T> T inTransaction(Supplier<T> work) {
            return transactions.execute(status -> work.get());
        }

        @Override
        public void checkpoint(String step, String point) {
            probes.orderedStream().forEach(probe -> probe.reached(step, point));
        }
    }
}
