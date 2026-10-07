package com.enviro.assessment.junior.thabiso.kojoana;
import com.enviro.assessment.junior.thabiso.kojoana.dto.WithdrawalRequest;
import com.enviro.assessment.junior.thabiso.kojoana.model.*;
import com.enviro.assessment.junior.thabiso.kojoana.repository.*;
import com.enviro.assessment.junior.thabiso.kojoana.service.PortfolioService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.MediaType;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class PortfolioApiTests {
    @Autowired MockMvc mvc;
    @Autowired InvestorRepository investors;
    @Autowired ProductRepository products;
    @Autowired WithdrawalRepository withdrawals;
    @Autowired PortfolioService service;
    Investor thabo;
    InvestmentProduct retirement;
    InvestmentProduct savings;
    @BeforeEach void findExamples() {
        thabo = investors.findAll().stream().filter(i -> i.getName().equals("Thabo Dlamini")).findFirst().orElseThrow();
        retirement = product(thabo, ProductType.RETIREMENT);
        savings = product(thabo, ProductType.SAVINGS);
    }
    InvestmentProduct product(Investor investor, ProductType type) {
        return products.findByInvestorIdOrderById(investor.getId()).stream().filter(p -> p.getType() == type).findFirst().orElseThrow();
    }
    String url(String suffix) { return "/api/investors/" + thabo.getId() + suffix; }
    @Test void listsTheThreeInvestors() throws Exception {
        mvc.perform(get("/api/investors")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(3)));
    }
    @Test void returnsPortfolioDetailsAndProducts() throws Exception {
        mvc.perform(get(url("/portfolio"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.investor.name").value("Thabo Dlamini"))
            .andExpect(jsonPath("$.totalBalance").value(120000))
            .andExpect(jsonPath("$.availableToWithdraw").value(108000))
            .andExpect(jsonPath("$.products", hasSize(2)));
    }
    @Test void savesTheNoticeAndReducesOnlyTheSelectedBalance() throws Exception {
        mvc.perform(post(url("/withdrawals")).contentType(MediaType.APPLICATION_JSON)
            .content("{\"productId\":" + retirement.getId() + ",\"amount\":10000.00}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.remainingBalance").value(90000));
        assertEquals(0, products.findById(retirement.getId()).orElseThrow().getBalance().compareTo(new BigDecimal("90000")));
        assertEquals(0, products.findById(savings.getId()).orElseThrow().getBalance().compareTo(new BigDecimal("20000")));
        assertEquals(1, withdrawals.findByProductInvestorIdOrderByCreatedAtDescIdDesc(thabo.getId()).size());
    }
    @Test void exactly65CannotWithdrawRetirementButCanWithdrawSavings() throws Exception {
        Investor naledi = investors.findAll().stream().filter(i -> i.getName().equals("Naledi Mokoena")).findFirst().orElseThrow();
        String path = "/api/investors/" + naledi.getId() + "/withdrawals";
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
            .content("{\"productId\":" + product(naledi, ProductType.RETIREMENT).getId() + ",\"amount\":100}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.productId").exists());
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
            .content("{\"productId\":" + product(naledi, ProductType.SAVINGS).getId() + ",\"amount\":100}"))
            .andExpect(status().isCreated());
    }
    @Test void rejectsInvalidAmountsWithoutChangingTheBalance() throws Exception {
        for (String amount : new String[] {"0", "-1", "0.001", "90000.01", "100000.01"}) {
            mvc.perform(post(url("/withdrawals")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":" + retirement.getId() + ",\"amount\":" + amount + "}"))
                .andExpect(status().isBadRequest());
        }
        assertEquals(0, products.findById(retirement.getId()).orElseThrow().getBalance().compareTo(new BigDecimal("100000")));
        assertTrue(withdrawals.findByProductInvestorIdOrderByCreatedAtDescIdDesc(thabo.getId()).isEmpty());
    }
    @Test void acceptsExactly90Percent() throws Exception {
        mvc.perform(post(url("/withdrawals")).contentType(MediaType.APPLICATION_JSON)
            .content("{\"productId\":" + retirement.getId() + ",\"amount\":90000}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.remainingBalance").value(10000));
    }
    @Test void rejectsMissingFieldsAndMalformedJson() throws Exception {
        mvc.perform(post(url("/withdrawals")).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.amount").exists())
            .andExpect(jsonPath("$.fieldErrors.productId").exists());
        mvc.perform(post(url("/withdrawals")).contentType(MediaType.APPLICATION_JSON).content("{broken"))
            .andExpect(status().isBadRequest());
    }
    @Test void cannotUseAnotherInvestorsProduct() throws Exception {
        Investor other = investors.findAll().stream().filter(i -> !i.getId().equals(thabo.getId())).findFirst().orElseThrow();
        mvc.perform(post(url("/withdrawals")).contentType(MediaType.APPLICATION_JSON)
            .content("{\"productId\":" + product(other, ProductType.SAVINGS).getId() + ",\"amount\":100}"))
            .andExpect(status().isNotFound());
    }
    @Test void filtersHistoryAndExportsTheSameRecords() throws Exception {
        service.createWithdrawal(thabo.getId(), new WithdrawalRequest(retirement.getId(), new BigDecimal("100")));
        service.createWithdrawal(thabo.getId(), new WithdrawalRequest(savings.getId(), new BigDecimal("50")));
        String query = "?productId=" + savings.getId() + "&from=" + LocalDate.now() + "&to=" + LocalDate.now();
        mvc.perform(get(url("/withdrawals") + query)).andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].productName").value("Savings investment"));
        mvc.perform(get(url("/withdrawals/export") + query)).andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", containsString("attachment")))
            .andExpect(content().string(allOf(containsString("\uFEFFsep=,\r\nNotice ID,Recorded at,Product,Amount (R),Balance before (R),Remaining balance (R)\r\n"), containsString("\"Savings investment\",50.00,20000.00,19950.00\r\n"), not(containsString("Retirement investment")))));
        mvc.perform(get(url("/withdrawals") + "?from=" + LocalDate.now().plusDays(1))).andExpect(jsonPath("$", hasSize(0)));
    }
    @Test void invalidFiltersAndMissingInvestorsReturnUsefulErrors() throws Exception {
        mvc.perform(get(url("/withdrawals") + "?from=2026-10-09&to=2026-10-08"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.from").exists());
        mvc.perform(get(url("/withdrawals") + "?from=not-a-date")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/investors/999999/portfolio")).andExpect(status().isNotFound());
    }
    @Test void openingAndWithdrawalBalancesAreRecordedForTheChart() {
        service.createWithdrawal(thabo.getId(), new WithdrawalRequest(retirement.getId(), new BigDecimal("100")));
        var portfolio = service.portfolio(thabo.getId());
        var history = portfolio.products().stream().filter(p -> p.id().equals(retirement.getId())).findFirst().orElseThrow().history();
        assertEquals(2, history.size());
        assertEquals(0, history.get(1).balance().compareTo(new BigDecimal("99900")));
    }
    @Test void theCreatedNoticeCanBeRetrieved() throws Exception {
        var notice = service.createWithdrawal(thabo.getId(), new WithdrawalRequest(savings.getId(), new BigDecimal("25")));
        mvc.perform(get(url("/withdrawals/") + notice.id())).andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(notice.id())).andExpect(jsonPath("$.amount").value(25));
    }
    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void concurrentRequestsCannotSpendTheSameBalanceTwice() throws Exception {
        // Separate transactions are needed to exercise the real database lock.
        Investor investor = investors.saveAndFlush(new Investor("Concurrency test", LocalDate.now().minusYears(70)));
        InvestmentProduct product = products.saveAndFlush(new InvestmentProduct(investor, "Lock test", ProductType.SAVINGS,
            new BigDecimal("100000"), new BigDecimal("100000"), java.time.LocalDateTime.now()));
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            java.util.concurrent.Callable<Boolean> attempt = () -> {
                start.await();
                try {
                    service.createWithdrawal(investor.getId(), new WithdrawalRequest(product.getId(), new BigDecimal("60000")));
                    return true;
                } catch (com.enviro.assessment.junior.thabiso.kojoana.exception.ValidationException expected) { return false; }
            };
            var first = executor.submit(attempt);
            var second = executor.submit(attempt);
            start.countDown();
            int successes = (first.get(20, java.util.concurrent.TimeUnit.SECONDS) ? 1 : 0) + (second.get(20, java.util.concurrent.TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, successes);
            assertEquals(0, products.findById(product.getId()).orElseThrow().getBalance().compareTo(new BigDecimal("40000")));
            assertEquals(1, withdrawals.findByProductInvestorIdOrderByCreatedAtDescIdDesc(investor.getId()).size());
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(20, java.util.concurrent.TimeUnit.SECONDS);
            withdrawals.deleteAll(withdrawals.findByProductInvestorIdOrderByCreatedAtDescIdDesc(investor.getId()));
            products.deleteById(product.getId());
            investors.deleteById(investor.getId());
        }
    }
}
