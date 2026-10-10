package com.company.banking.loan;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.banking.notification.service.StubSmsGateway;
import com.company.banking.support.CustomerApp;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * A borrower sees their loan in the app, is reminded before an installment falls due and repays it from its
 * repayment account with their PIN.
 */
class CustomerLoanAppIT extends LoanIntegrationTest {

    @Autowired
    private StubSmsGateway sms;

    @BeforeEach
    void setUpProduct() {
        productId = publishedLoanProduct("APP01", loanTerms());
        fixtures.enableFeature(tenant, "CUSTOMER_MOBILE_APP");
    }

    @Test
    void theBorrowerSeesAndRepaysTheirLoanInTheApp() {
        String loanId = disbursedLoan("1200.00", 6);
        JsonNode customer = api.get("/api/v1/customers/" + customerId, manager.token()).expect(200).data();
        CustomerApp app = new CustomerApp(api, sms);
        CustomerApp.Session session = app.activate(tenant, customer.get("customerNumber").asString(),
                customer.get("primaryPhone").asString(), "phone-" + UUID.randomUUID());

        JsonNode loans = app.get(session, "/api/v1/customer/loans").expect(200).data();
        assertThat(loans).hasSize(1);
        assertThat(loans.at("/0/principalOutstanding").asString()).isEqualTo("1200.00");
        JsonNode detail = app.get(session, "/api/v1/customer/loans/" + loanId).expect(200).data();
        assertThat(detail.get("schedule")).hasSize(6);
        app.get(session, "/api/v1/customer/loans/" + UUID.randomUUID()).expectError(404, "RESOURCE_NOT_FOUND");

        // The evening before the first installment falls due, the borrower is reminded once.
        jumpTo(START, LocalDate.of(2027, 3, 31));
        endOfDay("2027-03-31");
        String loanNumber = detail.at("/loan/loanNumber").asString();
        JsonNode reminder = app.get(session, "/api/v1/customer/notifications").expect(200).data().at("/items/0");
        assertThat(reminder.get("title").asString()).isEqualTo("Repayment due");
        assertThat(reminder.get("body").asString())
                .isEqualTo("GHS 224.00 is due on Thu 1 Apr for loan " + loanNumber + ".");
        assertThat(reminder.get("referenceId").asString()).isEqualTo(loanId);
        assertThat(app.lastMessage(session.phone())).contains("GHS 224.00 is due on Thu 1 Apr");

        String path = "/api/v1/customer/loans/" + loanId + "/repayments";
        app.pay(session, path, "app-repay-0", Map.of("amount", "224.00", "pin", "9753"))
                .expectError(422, "WRONG_PIN");
        JsonNode receipt = app.pay(session, path, "app-repay-1", Map.of("amount", "224.00", "pin", CustomerApp.PIN))
                .expect(201).data();
        assertThat(receipt.at("/repayment/principal").asString()).isEqualTo("200.00");
        assertThat(receipt.at("/repayment/source").asString()).isEqualTo("ACCOUNT");
        assertThat(app.pay(session, path, "app-repay-1", Map.of("amount", "224.00", "pin", CustomerApp.PIN))
                .expect(201).data().at("/repayment/id").asString()).isEqualTo(receipt.at("/repayment/id").asString());
        assertThat(balance()).isEqualTo("947.00");

        JsonNode transaction = api.get("/api/v1/transactions/" + receipt.at("/repayment/transactionId").asString(),
                manager.token()).expect(200).data();
        assertThat(transaction.get("channel").asString()).isEqualTo("MOBILE");
    }
}
