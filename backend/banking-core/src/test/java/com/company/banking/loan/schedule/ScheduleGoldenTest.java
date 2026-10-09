package com.company.banking.loan.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.banking.loan.schedule.ScheduleCalculator.Installment;
import com.company.banking.loan.schedule.ScheduleCalculator.Schedule;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every case in {@code loan-schedules/golden.csv} (computed by an independent Python implementation, see
 * {@code generate_golden.py}) is reproduced installment by installment, to the cent.
 */
class ScheduleGoldenTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void reproducesTheGoldenSchedule(String caseId, ScheduleCalculator.Terms terms, List<String[]> expected) {
        Schedule schedule = ScheduleCalculator.calculate(terms);

        assertThat(schedule.installments()).hasSize(expected.size());
        for (int i = 0; i < expected.size(); i++) {
            String[] row = expected.get(i);
            Installment installment = schedule.installments().get(i);
            assertThat(List.of(Integer.toString(installment.number()), installment.fromDate().toString(),
                    installment.dueDate().toString(), installment.principal().toPlainString(),
                    installment.interest().toPlainString(), installment.total().toPlainString(),
                    installment.outstandingAfter().toPlainString()))
                    .as("%s installment %d", caseId, i + 1)
                    .containsExactly(row[11], row[12], row[13], row[14], row[15], row[16], row[17]);
        }
        assertThat(schedule.installments().stream().map(Installment::principal).reduce(BigDecimal.ZERO,
                BigDecimal::add)).as("%s principal adds up", caseId).isEqualByComparingTo(terms.principal());
    }

    static Stream<Arguments> cases() throws IOException {
        Map<String, List<String[]>> rows = new LinkedHashMap<>();
        try (InputStream input = ScheduleGoldenTest.class.getResourceAsStream("/loan-schedules/golden.csv");
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            reader.readLine();
            String line;
            while ((line = reader.readLine()) != null) {
                String[] row = line.split(",", -1);
                rows.computeIfAbsent(row[0], key -> new ArrayList<>()).add(row);
            }
        }
        assertThat(rows).hasSizeGreaterThanOrEqualTo(50);
        return rows.entrySet().stream().map(entry -> {
            String[] row = entry.getValue().getFirst();
            ScheduleCalculator.Terms terms = new ScheduleCalculator.Terms(new BigDecimal(row[4]), new BigDecimal(row[5]),
                    InterestMethod.valueOf(row[1]), RepaymentFrequency.valueOf(row[2]), LoanDayCount.valueOf(row[3]),
                    Integer.parseInt(row[6]), LocalDate.parse(row[7]), row[8].isEmpty() ? null : LocalDate.parse(row[8]),
                    Integer.parseInt(row[9]), Integer.parseInt(row[10]), 2, RoundingMode.HALF_EVEN);
            return Arguments.of(entry.getKey() + " " + row[1] + " " + row[2] + " " + row[3], terms, entry.getValue());
        });
    }
}
