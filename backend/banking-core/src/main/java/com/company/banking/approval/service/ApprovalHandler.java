package com.company.banking.approval.service;

import com.company.banking.approval.model.ApprovalType;
import java.util.UUID;

/**
 * Runs an approved action. Implemented by the module that owns the action, so the approval module never depends
 * on it. Called inside the checker's transaction with the request row locked: if the action fails (for example the
 * money is no longer there) the exception rolls everything back and the request stays pending.
 */
public interface ApprovalHandler {

    ApprovalType type();

    /**
     * @return the id of what the action created (transaction, journal), recorded on the request
     */
    UUID execute(ApprovedAction action);

    /**
     * @param requestedBy the maker; the action must be attributed to them, with {@code approvedBy} as the checker
     * @param payloadJson the payload exactly as submitted
     */
    record ApprovedAction(UUID requestId, ApprovalType type, UUID requestedBy, UUID approvedBy, UUID resourceId,
                          String payloadJson) {
    }
}
