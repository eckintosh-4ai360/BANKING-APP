package com.company.banking.ledger.repository;

import static com.company.banking.ledger.repository.SqlParams.date;
import static com.company.banking.ledger.repository.SqlParams.decimal;
import static com.company.banking.ledger.repository.SqlParams.text;
import static com.company.banking.ledger.repository.SqlParams.uuid;
import static com.company.banking.ledger.repository.SqlParams.uuidArray;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Journals and their lines: insert and read only. The database rejects any update or delete.
 */
@Repository
@RequiredArgsConstructor
public class JournalRepository {

    public record JournalRow(UUID id, UUID tenantId, String journalNumber, LocalDate businessDate,
                             LocalDate valueDate, Instant postedAt, String sourceType, String sourceReference,
                             UUID financialTransactionId, UUID reversesJournalId, UUID branchId, UUID postedBy,
                             UUID approvedBy, String description) {
    }

    public record LineRow(UUID id, UUID tenantId, UUID journalEntryId, int lineNo, UUID chartOfAccountId,
                          UUID ledgerAccountId, UUID branchId, String currency, String direction, BigDecimal amount,
                          LocalDate businessDate, String narration) {
    }

    public record LineView(int lineNo, UUID chartOfAccountId, String glCode, String glName, UUID ledgerAccountId,
                           String ledgerAccountName, UUID branchId, String currency, String direction,
                           BigDecimal amount, String narration) {
    }

    private static final String JOURNAL_COLUMNS = "id, tenant_id, journal_number, business_date, value_date,"
            + " posted_at, source_type, source_reference, financial_transaction_id, reverses_journal_id, branch_id,"
            + " posted_by, approved_by, description";

    private static final RowMapper<JournalRow> JOURNAL_MAPPER = (rs, rowNum) -> new JournalRow(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getString("journal_number"),
            rs.getObject("business_date", LocalDate.class),
            rs.getObject("value_date", LocalDate.class),
            rs.getTimestamp("posted_at").toInstant(),
            rs.getString("source_type"),
            rs.getString("source_reference"),
            rs.getObject("financial_transaction_id", UUID.class),
            rs.getObject("reverses_journal_id", UUID.class),
            rs.getObject("branch_id", UUID.class),
            rs.getObject("posted_by", UUID.class),
            rs.getObject("approved_by", UUID.class),
            rs.getString("description"));

    private final JdbcClient jdbc;

    public void insertJournal(JournalRow row) {
        jdbc.sql("INSERT INTO core.journal_entry (id, tenant_id, journal_number, business_date, value_date,"
                        + " source_type, source_reference, financial_transaction_id, reverses_journal_id, branch_id,"
                        + " posted_by, approved_by, description)"
                        + " VALUES (:id, :tenantId, :number, :businessDate, :valueDate, :sourceType, :sourceReference,"
                        + " :transactionId, :reverses, :branchId, :postedBy, :approvedBy, :description)")
                .param("id", uuid(row.id()))
                .param("tenantId", uuid(row.tenantId()))
                .param("number", row.journalNumber())
                .param("businessDate", date(row.businessDate()))
                .param("valueDate", date(row.valueDate()))
                .param("sourceType", row.sourceType())
                .param("sourceReference", text(row.sourceReference()))
                .param("transactionId", uuid(row.financialTransactionId()))
                .param("reverses", uuid(row.reversesJournalId()))
                .param("branchId", uuid(row.branchId()))
                .param("postedBy", uuid(row.postedBy()))
                .param("approvedBy", uuid(row.approvedBy()))
                .param("description", row.description())
                .update();
    }

    public void insertLine(LineRow line) {
        jdbc.sql("INSERT INTO core.ledger_entry (id, tenant_id, journal_entry_id, line_no, chart_of_account_id,"
                        + " ledger_account_id, branch_id, currency, direction, amount, business_date, narration)"
                        + " VALUES (:id, :tenantId, :journalId, :lineNo, :glId, :ledgerAccountId, :branchId, :currency,"
                        + " :direction, :amount, :businessDate, :narration)")
                .param("id", uuid(line.id()))
                .param("tenantId", uuid(line.tenantId()))
                .param("journalId", uuid(line.journalEntryId()))
                .param("lineNo", line.lineNo())
                .param("glId", uuid(line.chartOfAccountId()))
                .param("ledgerAccountId", uuid(line.ledgerAccountId()))
                .param("branchId", uuid(line.branchId()))
                .param("currency", line.currency())
                .param("direction", line.direction())
                .param("amount", decimal(line.amount()))
                .param("businessDate", date(line.businessDate()))
                .param("narration", text(line.narration()))
                .update();
    }

    public Optional<JournalRow> find(UUID tenantId, UUID journalId) {
        return jdbc.sql("SELECT " + JOURNAL_COLUMNS + " FROM core.journal_entry WHERE tenant_id = :tenantId AND id = :id")
                .param("tenantId", uuid(tenantId))
                .param("id", uuid(journalId))
                .query(JOURNAL_MAPPER)
                .optional();
    }

    public List<JournalRow> findByTransaction(UUID tenantId, UUID financialTransactionId) {
        return jdbc.sql("SELECT " + JOURNAL_COLUMNS + " FROM core.journal_entry"
                        + " WHERE tenant_id = :tenantId AND financial_transaction_id = :transactionId ORDER BY posted_at, id")
                .param("tenantId", uuid(tenantId))
                .param("transactionId", uuid(financialTransactionId))
                .query(JOURNAL_MAPPER)
                .list();
    }

    public Optional<UUID> findReversalOf(UUID tenantId, UUID journalId) {
        return jdbc.sql("SELECT id FROM core.journal_entry WHERE tenant_id = :tenantId AND reverses_journal_id = :id")
                .param("tenantId", uuid(tenantId))
                .param("id", uuid(journalId))
                .query(UUID.class)
                .optional();
    }

    public List<LineView> findLines(UUID tenantId, UUID journalId) {
        return jdbc.sql("SELECT e.line_no, e.chart_of_account_id, g.code AS gl_code, g.name AS gl_name,"
                        + " e.ledger_account_id, l.name AS ledger_account_name, e.branch_id, e.currency, e.direction,"
                        + " e.amount, e.narration"
                        + " FROM core.ledger_entry e"
                        + " JOIN core.chart_of_account g ON g.tenant_id = e.tenant_id AND g.id = e.chart_of_account_id"
                        + " LEFT JOIN core.ledger_account l ON l.tenant_id = e.tenant_id AND l.id = e.ledger_account_id"
                        + " WHERE e.tenant_id = :tenantId AND e.journal_entry_id = :journalId ORDER BY e.line_no")
                .param("tenantId", uuid(tenantId))
                .param("journalId", uuid(journalId))
                .query((rs, rowNum) -> new LineView(
                        rs.getInt("line_no"),
                        rs.getObject("chart_of_account_id", UUID.class),
                        rs.getString("gl_code"),
                        rs.getString("gl_name"),
                        rs.getObject("ledger_account_id", UUID.class),
                        rs.getString("ledger_account_name"),
                        rs.getObject("branch_id", UUID.class),
                        rs.getString("currency"),
                        rs.getString("direction"),
                        rs.getBigDecimal("amount"),
                        rs.getString("narration")))
                .list();
    }

    /**
     * Journals by business date range, newest first. {@code branchIds} null means every branch.
     */
    public List<JournalRow> search(UUID tenantId, LocalDate from, LocalDate to, String sourceType,
                                   String sourceReference, Collection<UUID> branchIds, int limit, long offset) {
        return jdbc.sql("SELECT " + JOURNAL_COLUMNS + " FROM core.journal_entry" + searchWhere()
                        + " ORDER BY business_date DESC, posted_at DESC, id DESC LIMIT :limit OFFSET :offset")
                .params(searchParams(tenantId, from, to, sourceType, sourceReference, branchIds))
                .param("limit", limit)
                .param("offset", offset)
                .query(JOURNAL_MAPPER)
                .list();
    }

    public long count(UUID tenantId, LocalDate from, LocalDate to, String sourceType, String sourceReference,
                      Collection<UUID> branchIds) {
        return jdbc.sql("SELECT count(*) FROM core.journal_entry" + searchWhere())
                .params(searchParams(tenantId, from, to, sourceType, sourceReference, branchIds))
                .query(Long.class)
                .single();
    }

    private static String searchWhere() {
        return " WHERE tenant_id = :tenantId"
                + " AND (CAST(:from AS date) IS NULL OR business_date >= :from)"
                + " AND (CAST(:to AS date) IS NULL OR business_date <= :to)"
                + " AND (CAST(:sourceType AS varchar) IS NULL OR source_type = :sourceType)"
                + " AND (CAST(:sourceReference AS varchar) IS NULL OR source_reference = :sourceReference)"
                + " AND (CAST(:branchIds AS uuid[]) IS NULL OR branch_id = ANY (CAST(:branchIds AS uuid[])))";
    }

    private static java.util.Map<String, Object> searchParams(UUID tenantId, LocalDate from, LocalDate to,
                                                              String sourceType, String sourceReference,
                                                              Collection<UUID> branchIds) {
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        params.put("tenantId", uuid(tenantId));
        params.put("from", date(from));
        params.put("to", date(to));
        params.put("sourceType", text(sourceType));
        params.put("sourceReference", text(sourceReference));
        params.put("branchIds", uuidArray(branchIds));
        return params;
    }
}
