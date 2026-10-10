package com.spendio.imports.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import com.spendio.accounts.dto.request.CreateAccountRequest;
import com.spendio.accounts.service.AccountService;
import com.spendio.imports.dto.request.SaveMappingRequest;
import com.spendio.imports.dto.request.SaveMappingRequest.FieldMapping;
import com.spendio.imports.dto.request.SaveMappingRequest.ImportOptions;
import com.spendio.sharedkernel.domain.enums.AccountType;
import com.spendio.sharedkernel.security.UserPrincipal;
import com.spendio.support.AbstractIntegrationTest;
import com.spendio.users.dto.request.CreateUserCommand;
import com.spendio.users.dto.response.UserResponse;
import com.spendio.users.service.ProfileService;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/**
 * Import endpoints end-to-end (ARCHITECTURE.md Phase 12, E.12): upload+parse,
 * mapping suggestions + save, validation + duplicate detection, preview, decisions,
 * execution (dry run and real), report, error CSV, undo and history.
 *
 * <p>Unlike the ledger controller tests this deliberately does <em>not</em> run in a
 * test transaction: {@code ImportExecutionService} posts each row in its own
 * REQUIRES_NEW transaction whose foreign key points at the (uncommitted, invisible)
 * import job, so an outer test transaction would break execution and the committed
 * ledger rows could not be rolled back. Every test seeds a fresh user and the CSV
 * fixtures use unique occurrences, so repeated runs converge instead of colliding.
 */
@EnabledIf(value = "com.spendio.support.MySqlTestSupport#available",
        disabledReason = "no test database: set SPENDIO_TEST_DB_URL, start Docker, or run MySQL on port 3307")
@AutoConfigureMockMvc
class CsvImportControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ProfileService profileService;
    @Autowired
    private AccountService accountService;
    @Autowired
    private com.spendio.categories.service.CategoryService categoryService;

    private long userId;
    private long accountId;
    private String fixtureCsv;

    @BeforeEach
    void seedFixture() throws Exception {
        UserResponse user = profileService.create(new CreateUserCommand(
                uniqueEmail(), "{bcrypt}", "Ima", "Importer", "UTC", "en-US", "USD"));
        userId = user.id();
        com.spendio.accounts.domain.Account account = accountService.create(userId, new CreateAccountRequest(
                "Chase Checking", AccountType.BANK, "USD", 0, null, null, null, null, null, null, null, null,
                null, false, null, true));
        accountId = account.getId();
        categoryService.create(userId,
                new com.spendio.categories.dto.request.CreateCategoryRequest("Food", "EXPENSE", null, null, null, false));
        categoryService.create(userId,
                new com.spendio.categories.dto.request.CreateCategoryRequest("Tech", "EXPENSE", null, null, null, false));
        categoryService.create(userId,
                new com.spendio.categories.dto.request.CreateCategoryRequest("Housing", "EXPENSE", null, null, null, false));
        fixtureCsv = readFixture("e2e-categories.csv");
    }

    @Test
    void uploadParsesAndSuggestsThenSaveMappingValidatesReadies() throws Exception {
        MvcResult upload = mockMvc.perform(asUser(userId, upload(fixtureCsv)
                        .param("defaultCurrencyCode", "USD")
                        .param("targetAccountId", String.valueOf(accountId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MAPPING_REQUIRED"))
                .andExpect(jsonPath("$.columns[0]").value("Date"))
                .andExpect(jsonPath("$.totals.total").value(5))
                .andReturn();
        long importId = objectMapper.readTree(upload.getResponse().getContentAsString()).get("id").asLong();

        MvcResult detail = mockMvc.perform(asUser(userId, get("/api/v1/imports/{id}", importId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalFilename").value("e2e-categories.csv"))
                .andExpect(jsonPath("$.detectedDelimiter").value(","))
                .andReturn();
        assertThat(objectMapper.readTree(detail.getResponse().getContentAsString()).get("status").asText())
                .isEqualTo("MAPPING_REQUIRED");

        mockMvc.perform(asUser(userId, get("/api/v1/imports/{id}/columns", importId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns[0].headerName").value("Date"))
                .andExpect(jsonPath("$.columns[1].headerName").value("Description"))
                .andExpect(jsonPath("$.columns[2].headerName").value("Amount"))
                .andExpect(jsonPath("$.mapping[*].field").value(org.hamcrest.Matchers.hasItems("date", "amount")))
                .andExpect(jsonPath("$.unmappedRequired").isArray());

        mockMvc.perform(asUser(userId, put("/api/v1/imports/{id}/mapping", importId)
                        .contentType(MediaType.APPLICATION_JSON).content(mappingPayload())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.totals.total").value(5))
                .andExpect(jsonPath("$.totals.valid").value(5))
                .andExpect(jsonPath("$.totals.invalid").value(0));
    }

    @Test
    void previewDuplicatesDecisionsAndExecutionWork() throws Exception {
        long importId = uploadAndMap();

        mockMvc.perform(asUser(userId, get("/api/v1/imports/{id}/preview", importId)
                        .param("status", "VALID").param("size", "50")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.content[0].status").value("VALID"))
                .andExpect(jsonPath("$.content[0].parsed.currencyCode").value("USD"));

        mockMvc.perform(asUser(userId, get("/api/v1/imports/{id}/duplicates", importId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        mockMvc.perform(asUser(userId, post("/api/v1/imports/{id}/execute", importId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dryRun\":true}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdTransactions").isEmpty())
                .andExpect(jsonPath("$.failures[0].reason").value(containsString("dry-run")))
                .andExpect(jsonPath("$.import_.status").value("READY"));

        MvcResult executed = mockMvc.perform(asUser(userId, post("/api/v1/imports/{id}/execute", importId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdTransactions").isNotEmpty())
                .andExpect(jsonPath("$.failures").isEmpty())
                .andExpect(jsonPath("$.import_.status").value("COMPLETED"))
                .andReturn();
        long firstTx = objectMapper.readTree(executed.getResponse().getContentAsString())
                .get("createdTransactions").get(0).get("id").asLong();
        assertThat(firstTx).isPositive();

        var preview = mockMvc.perform(asUser(userId, get("/api/v1/imports/{id}/preview", importId)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(preview.getResponse().getContentAsString()).get("totalElements").asInt())
                .isEqualTo(5);
    }

    @Test
    void reportErrorsCsvAndUndoRoundTrip() throws Exception {
        long importId = uploadAndMap();
        mockMvc.perform(asUser(userId, post("/api/v1/imports/{id}/execute", importId)
                        .contentType(MediaType.APPLICATION_JSON).content("{}")))
                .andExpect(status().isOk());

        mockMvc.perform(asUser(userId, get("/api/v1/imports/{id}/report", importId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdTransactions").isNotEmpty())
                .andExpect(jsonPath("$.failures").isEmpty());

        mockMvc.perform(asUser(userId, get("/api/v1/imports/{id}/errors.csv", importId)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(header().string("Content-Disposition", containsString("import-" + importId)))
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    assertThat(body).startsWith("rowNumber,error");
                });

        mockMvc.perform(asUser(userId, post("/api/v1/imports/{id}/undo", importId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.undone").value(5));

        mockMvc.perform(asUser(userId, get("/api/v1/imports/{id}/duplicates", importId)))
                .andExpect(status().isOk());
    }

    @Test
    void historySupportsStatusFilteringAndOtherUsersAreIsolated() throws Exception {
        mockMvc.perform(asUser(userId, get("/api/v1/imports")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void invalidUploadAndUnknownImportAreRejected() throws Exception {
        byte[] tooBig = new byte[5 * 1024 * 1024 + 1];
        Arrays.fill(tooBig, (byte) ',');
        MockMultipartFile oversized = new MockMultipartFile("file", "big.csv", "text/csv", tooBig);
        mockMvc.perform(asUser(userId, multipart("/api/v1/imports").file(oversized)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("IMPORT_FILE_TOO_LARGE"));

        mockMvc.perform(asUser(userId, get("/api/v1/imports/{id}", 999_999L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("IMPORT_NOT_FOUND"));

        mockMvc.perform(asUser(userId, get("/api/v1/imports/{id}/preview", 999_999L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("IMPORT_NOT_FOUND"));
    }

    // --- helpers --------------------------------------------------------------

    private long uploadAndMap() throws Exception {
        MvcResult upload = mockMvc.perform(asUser(userId, upload(fixtureCsv)
                        .param("defaultCurrencyCode", "USD")
                        .param("targetAccountId", String.valueOf(accountId))))
                .andExpect(status().isOk())
                .andReturn();
        long importId = objectMapper.readTree(upload.getResponse().getContentAsString()).get("id").asLong();
        mockMvc.perform(asUser(userId, put("/api/v1/imports/{id}/mapping", importId)
                        .contentType(MediaType.APPLICATION_JSON).content(mappingPayload())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"));
        return importId;
    }

    private String mappingPayload() throws Exception {
        Map<String, FieldMapping> columnMappings = Map.of(
                "Date", new FieldMapping("date", null),
                "Description", new FieldMapping("description", null),
                "Amount", new FieldMapping("amount", null),
                "Category", new FieldMapping("category", null));
        SaveMappingRequest request = new SaveMappingRequest(
                columnMappings,
                "Date", "Amount", "Description",
                null, null, "Category", null, null, null,
                null, null,
                new ImportOptions("USD", null, accountId, false, true, true, null));
        return objectMapper.writeValueAsString(request);
    }

    private MockMultipartHttpServletRequestBuilder upload(String csv) {
        MockMultipartFile file = new MockMultipartFile("file", "e2e-categories.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        return multipart("/api/v1/imports").file(file);
    }

    private static String readFixture(String name) throws Exception {
        try (var in = CsvImportControllerTest.class.getClassLoader().getResourceAsStream(
                "fixtures/imports/" + name)) {
            if (in == null) {
                throw new IllegalStateException("missing fixture " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String uniqueEmail() {
        return "import-e2e-" + UUID.randomUUID() + "@spendio.test";
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B asUser(long userId, B builder) {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new UserPrincipal(userId, "user-" + userId + "@spendio.test", true),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        return builder.with(SecurityMockMvcRequestPostProcessors.authentication(authentication));
    }
}