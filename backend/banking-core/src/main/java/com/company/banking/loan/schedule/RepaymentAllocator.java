package com.company.banking.loan.schedule;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Splits a repayment over a loan's installments. Pure.
 *
 * <p><b>Partial repayment:</b> installments due by today are settled oldest first, each one's components in the
 * product's allocation order (e.g. penalty, fee, interest, principal). Whatever is left prepays principal of the
 * installments not yet due, the next one first. Interest of an installment is never collected before it falls due:
 * it is not earned yet.
 *
 * <p><b>Settlement:</b> everything owed today. Penalties, the interest of installments already due, the interest
 * earned so far in the running period and all principal; the interest not yet earned is waived.
 */
public final class RepaymentAllocator {

    public enum Component { PENALTY, FEE, INTEREST, PRINCIPAL }

    /**
     * What is still owed on an installment.
     */
    public record Owed(int number, LocalDate dueDate, BigDecimal principal, BigDecimal interest, BigDecimal penalty) {
    }

    /**
     * What a repayment pays of an installment, and (on settlement) the interest waived on it.
     */
    public record Allocation(int number, BigDecimal principal, BigDecimal interest, BigDecimal penalty,
                             BigDecimal interestWaived) {

        public boolean isEmpty() {
            return principal.signum() == 0 && interest.signum() == 0 && penalty.signum() == 0
                    && interestWaived.signum() == 0;
        }
    }

    /**
     * @param unallocated what the amount exceeds everything that could be paid (zero when it all found a place)
     */
    public record Result(List<Allocation> allocations, BigDecimal principal, BigDecimal interest, BigDecimal penalty,
                         BigDecimal interestWaived, BigDecimal unallocated) {

        public BigDecimal total() {
            return principal.add(interest).add(penalty);
        }
    }

    private RepaymentAllocator() {
    }

    /**
     * Parses an allocation order such as {@code PENALTY,FEE,INTEREST,PRINCIPAL}: each component exactly once.
     */
    public static List<Component> parseOrder(String order) {
        List<Component> components = Arrays.stream(order.split(",")).map(String::trim).map(Component::valueOf)
                .toList();
        if (components.size() != Component.values().length || components.stream().distinct().count()
                != Component.values().length) {
            throw new IllegalArgumentException("The allocation order must name each component once: " + order);
        }
        return components;
    }

    public static Result allocate(List<Owed> owed, LocalDate today, BigDecimal amount, List<Component> order) {
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("A repayment must be positive");
        }
        BigDecimal zero = amount.subtract(amount);
        BigDecimal remaining = amount;
        List<Allocation> allocations = new ArrayList<>();
        for (Owed installment : owed) {
            BigDecimal principal = zero;
            BigDecimal interest = zero;
            BigDecimal penalty = zero;
            if (!installment.dueDate().isAfter(today)) {
                for (Component component : order) {
                    BigDecimal outstanding = switch (component) {
                        case PENALTY -> installment.penalty();
                        case INTEREST -> installment.interest();
                        case PRINCIPAL -> installment.principal();
                        case FEE -> zero;
                    };
                    BigDecimal paid = remaining.min(outstanding);
                    remaining = remaining.subtract(paid);
                    switch (component) {
                        case PENALTY -> penalty = paid;
                        case INTEREST -> interest = paid;
                        case PRINCIPAL -> principal = paid;
                        case FEE -> { }
                    }
                }
            }
            allocations.add(new Allocation(installment.number(), principal, interest, penalty, zero));
        }
        for (int index = 0; index < owed.size() && remaining.signum() > 0; index++) {
            Owed installment = owed.get(index);
            if (installment.dueDate().isAfter(today)) {
                BigDecimal paid = remaining.min(installment.principal());
                remaining = remaining.subtract(paid);
                Allocation current = allocations.get(index);
                allocations.set(index, new Allocation(current.number(), paid, current.interest(), current.penalty(),
                        zero));
            }
        }
        return result(allocations, remaining, zero);
    }

    /**
     * @param interestEarned  all interest earned by today ({@link InterestEarned#through}); less the interest already
     *                        settled it gives what the running period's installment owes so far
     * @param interestSettled per installment, the interest already paid or waived on it (same order as {@code owed})
     */
    public static Result settle(List<Owed> owed, List<BigDecimal> interestSettled, LocalDate today,
                                BigDecimal interestEarned) {
        BigDecimal zero = interestEarned.subtract(interestEarned);
        BigDecimal earnedNotDue = interestEarned;
        for (int index = 0; index < owed.size(); index++) {
            Owed installment = owed.get(index);
            if (!installment.dueDate().isAfter(today)) {
                earnedNotDue = earnedNotDue.subtract(installment.interest()).subtract(interestSettled.get(index));
            }
        }
        List<Allocation> allocations = new ArrayList<>();
        for (int index = 0; index < owed.size(); index++) {
            Owed installment = owed.get(index);
            BigDecimal interest = installment.interest();
            BigDecimal waived = zero;
            if (installment.dueDate().isAfter(today)) {
                BigDecimal earned = earnedNotDue.subtract(interestSettled.get(index)).max(zero).min(interest);
                earnedNotDue = earnedNotDue.subtract(earned).subtract(interestSettled.get(index)).max(zero);
                waived = interest.subtract(earned);
                interest = earned;
            }
            allocations.add(new Allocation(installment.number(), installment.principal(), interest,
                    installment.penalty(), waived));
        }
        return result(allocations, zero, zero);
    }

    private static Result result(List<Allocation> allocations, BigDecimal unallocated, BigDecimal zero) {
        BigDecimal principal = zero;
        BigDecimal interest = zero;
        BigDecimal penalty = zero;
        BigDecimal waived = zero;
        for (Allocation allocation : allocations) {
            principal = principal.add(allocation.principal());
            interest = interest.add(allocation.interest());
            penalty = penalty.add(allocation.penalty());
            waived = waived.add(allocation.interestWaived());
        }
        return new Result(List.copyOf(allocations), principal, interest, penalty, waived, unallocated);
    }
}
