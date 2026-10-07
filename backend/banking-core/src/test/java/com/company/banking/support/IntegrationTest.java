package com.company.banking.support;

import com.company.banking.common.security.CurrentActor;
import com.company.banking.iam.service.PlatformUserService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/**
 * Base for API-level integration tests: full application context, real PostgreSQL, MockMvc through the complete
 * security filter chain. Tests create their own institutions with unique codes, so they don't depend on each other.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {

    public static final String PLATFORM_USERNAME = "platform.owner";
    public static final String PLATFORM_PASSWORD = "Platform#Test#2026";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JsonMapper jsonMapper;

    @Autowired
    private PlatformUserService platformUserService;

    protected Api api;
    protected Fixtures fixtures;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.Server server = TestDatabase.get();
        registry.add("spring.datasource.url", server::bankingUrl);
        registry.add("spring.datasource.username", () -> TestDatabase.APP_USER);
        registry.add("spring.datasource.password", () -> TestDatabase.APP_PASSWORD);
        registry.add("spring.flyway.user", () -> TestDatabase.MIGRATOR_USER);
        registry.add("spring.flyway.password", () -> TestDatabase.MIGRATOR_PASSWORD);
        registry.add("spring.flyway.placeholders.app_db_role", () -> TestDatabase.APP_USER);
    }

    @BeforeEach
    void setUpHelpers() {
        CurrentActor.callAsSystem(null, () -> platformUserService.bootstrapOwnerIfAbsent(PLATFORM_USERNAME,
                "owner@platform.test", "Platform Owner", PLATFORM_PASSWORD, false));
        api = new Api(mockMvc, jsonMapper);
        fixtures = new Fixtures(api);
    }
}
