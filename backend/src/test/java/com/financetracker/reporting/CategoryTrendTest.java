package com.financetracker.reporting;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financetracker.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Fixed-fixture correctness for the category-stacked trend: subcategory spend rolls up to its
 * parent series, uncategorized is its own stack, series are ordered by total, buckets zero-fill.
 */
class CategoryTrendTest extends AbstractIntegrationTest {

  @Test
  void stacksExpenseByTopLevelCategoryOverTime() throws Exception {
    RegisteredUser user = register("cat-trend@example.com", "password123");
    clearCategories(user);
    long account = createAccount(user);
    long food = createCategory(user, "Food", null);
    long groceries = createCategory(user, "Groceries", food);
    long transport = createCategory(user, "Transport", null);

    expense(user, account, "2026-05-05", 1000, food); // direct on Food
    expense(user, account, "2026-05-10", 2000, groceries); // rolls up to Food
    expense(user, account, "2026-05-15", 500, transport);
    expense(user, account, "2026-05-20", 300, null); // uncategorized
    expense(user, account, "2026-06-10", 4000, transport);

    mockMvc
        .perform(
            get("/api/v1/reports/category-trend?from=2026-05-01&to=2026-06-30&interval=month&kind=expense")
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.series.length()").value(3))
        .andExpect(jsonPath("$.series[0].name").value("Transport")) // 4500, highest total
        .andExpect(jsonPath("$.series[1].name").value("Food")) // 3000
        .andExpect(jsonPath("$.buckets.length()").value(2))
        .andExpect(jsonPath("$.buckets[0].period").value("2026-05"))
        .andExpect(jsonPath("$.buckets[0].amounts['" + food + "']").value(3000)) // 1000 + 2000
        .andExpect(jsonPath("$.buckets[0].amounts['" + transport + "']").value(500))
        .andExpect(jsonPath("$.buckets[0].amounts.uncategorized").value(300))
        .andExpect(jsonPath("$.buckets[1].amounts['" + transport + "']").value(4000));
  }

  private long createAccount(RegisteredUser user) throws Exception {
    return id(
        mockMvc
            .perform(
                post("/api/v1/accounts")
                    .header(HttpHeaders.AUTHORIZATION, bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Checking\",\"type\":\"checking\",\"currency\":\"PLN\"}"))
            .andExpect(status().isCreated())
            .andReturn());
  }

  private long createCategory(RegisteredUser user, String name, Long parentId) throws Exception {
    String body =
        parentId == null
            ? "{\"name\":\"" + name + "\",\"kind\":\"expense\"}"
            : "{\"name\":\"" + name + "\",\"kind\":\"expense\",\"parentId\":" + parentId + "}";
    return id(
        mockMvc
            .perform(
                post("/api/v1/categories")
                    .header(HttpHeaders.AUTHORIZATION, bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn());
  }

  private void expense(RegisteredUser user, long account, String date, long amount, Long categoryId)
      throws Exception {
    String cat = categoryId == null ? "" : ",\"categoryId\":" + categoryId;
    mockMvc
        .perform(
            post("/api/v1/transactions")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"date\":\""
                        + date
                        + "\",\"amountMinor\":"
                        + amount
                        + ",\"type\":\"expense\",\"accountId\":"
                        + account
                        + cat
                        + "}"))
        .andExpect(status().isCreated());
  }

  private long id(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
  }
}
