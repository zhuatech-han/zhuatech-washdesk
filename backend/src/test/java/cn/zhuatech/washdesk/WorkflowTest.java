// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.washdesk;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.*;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Security、JPA和Flyway整合测试，实际检查五件分批取衣与资金守恒。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。
 */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(WorkflowTest.TimeConfig.class)
class WorkflowTest {
  static final String PW = "Aa9" + UUID.randomUUID();

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add(
        "spring.datasource.url",
        () -> "jdbc:h2:mem:wash;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    r.add("spring.datasource.username", () -> "sa");
    r.add("spring.datasource.password", () -> "");
    r.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.H2Dialect");
    r.add("washdesk.admin-password", () -> PW);
  }

  @TestConfiguration
  static class TimeConfig {
    @Bean
    @Primary
    Clock fixedClock() {
      return Clock.fixed(Instant.parse("2026-10-13T02:00:00Z"), ZoneOffset.UTC);
    }
  }

  @Autowired MockMvc mvc;
  final JsonMapper json = JsonMapper.builder().build();
  MockHttpSession admin;
  long customer, service, rack;
  String suffix;

  Map<String, Object> b(Object... x) {
    var m = new LinkedHashMap<String, Object>();
    for (int i = 0; i < x.length; i += 2) m.put(x[i].toString(), x[i + 1]);
    return m;
  }

  Map<String, Object> cmd(Object... x) {
    var m = b(x);
    m.put("requestId", UUID.randomUUID().toString());
    m.putIfAbsent("note", "QA verification");
    return m;
  }

  JsonNode call(MockHttpSession s, String method, String path, Map<String, Object> b, int status)
      throws Exception {
    MockHttpServletRequestBuilder r =
        switch (method) {
          case "POST" ->
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path);
          case "PUT" -> put(path);
          default -> get(path);
        };
    if (s != null) r.session(s);
    if (!method.equals("GET"))
      r.with(csrf()).contentType("application/json").content(json.writeValueAsString(b));
    var out = mvc.perform(r).andReturn().getResponse();
    assertEquals(status, out.getStatus(), path + " " + out.getContentAsString());
    return json.readTree(out.getContentAsString());
  }

  JsonNode post(String path, Map<String, Object> b, int status) throws Exception {
    return call(admin, "POST", path, b, status);
  }

  JsonNode detail(long id) throws Exception {
    return call(admin, "GET", "/api/orders/" + id, null, 200);
  }

  MockHttpSession login(String name) throws Exception {
    var s = new MockHttpSession();
    call(s, "POST", "/api/auth/login", b("username", name, "password", PW), 200);
    return s;
  }

  @BeforeEach
  void setup() throws Exception {
    suffix = UUID.randomUUID().toString().substring(0, 8);
    admin = login("admin");
    customer =
        post(
                "/api/masters/customers",
                b(
                    "departmentId",
                    1,
                    "name",
                    "QA " + suffix,
                    "contact",
                    "",
                    "billing",
                    "RETAIL",
                    "currency",
                    "CNY",
                    "creditLimit",
                    0,
                    "enabled",
                    true),
                200)
            .get("id")
            .asLong();
    service =
        post(
                "/api/masters/services",
                b(
                    "departmentId",
                    1,
                    "name",
                    "QA 干洗 " + suffix,
                    "nameEn",
                    "QA Dry clean",
                    "category",
                    "DRYCLEAN",
                    "currency",
                    "CNY",
                    "price",
                    1299,
                    "enabled",
                    true),
                200)
            .get("id")
            .asLong();
    rack =
        post(
                "/api/masters/racks",
                b(
                    "departmentId",
                    1,
                    "code",
                    "R_" + suffix,
                    "name",
                    "QA rack",
                    "capacity",
                    10,
                    "enabled",
                    true),
                200)
            .get("id")
            .asLong();
  }

  JsonNode order(long customer, int n) throws Exception {
    var items = new ArrayList<>();
    for (int i = 0; i < n; i++)
      items.add(
          b(
              "serviceId",
              service,
              "serviceVersion",
              1,
              "description",
              "QA garment " + i,
              "conditionNote",
              "QA collar marks acknowledged"));
    return post(
        "/api/orders",
        cmd(
            "departmentId",
            1,
            "customerId",
            customer,
            "customerVersion",
            1,
            "dueDate",
            "2026-10-15",
            "items",
            items),
        200);
  }

  JsonNode change(long order, int i, String action) throws Exception {
    var d = detail(order);
    var g = d.get("garments").get(i);
    return post(
        "/api/garments/" + g.get("id").asLong() + "/" + action,
        cmd("version", g.get("version").asLong(), "rackId", rack),
        200);
  }

  void ready(long order, int n) throws Exception {
    for (int i = 0; i < n; i++) {
      change(order, i, "start");
      change(order, i, "ready");
      change(order, i, "rack");
    }
  }

  Map<String, Object> pay(JsonNode d, long amount, String reference) {
    return cmd(
        "version",
        d.get("order").get("version").asLong(),
        "kind",
        "PAYMENT",
        "amount",
        amount,
        "reference",
        reference,
        "method",
        "CASH");
  }

  @Test
  void fiveGarmentsPartialCollectionAndRackConservation() throws Exception {
    var o = order(customer, 5);
    long id = o.get("order").get("id").asLong();
    ready(id, 5);
    var d = detail(id);
    post("/api/payments/ORDER/" + id, pay(d, 6495, "P_" + suffix), 200);
    d = detail(id);
    var codes = new ArrayList<String>();
    for (int i = 0; i < 3; i++) codes.add(d.get("garments").get(i).get("barcode").asText());
    var body =
        cmd(
            "version",
            d.get("order").get("version").asLong(),
            "barcodes",
            codes,
            "receiver",
            "QA recipient");
    var partial = post("/api/orders/" + id + "/pickup", body, 200);
    assertEquals(3, partial.get("delivered").asInt());
    assertEquals(2, partial.get("racked").asInt());
    assertEquals("OPEN", partial.get("order").get("state").asText());
    assertEquals(3, post("/api/orders/" + id + "/pickup", body, 200).get("delivered").asInt());
    codes.clear();
    for (int i = 3; i < 5; i++) codes.add(partial.get("garments").get(i).get("barcode").asText());
    var completed =
        post(
            "/api/orders/" + id + "/pickup",
            cmd(
                "version",
                partial.get("order").get("version").asLong(),
                "barcodes",
                codes,
                "receiver",
                "QA recipient"),
            200);
    assertEquals("COMPLETED", completed.get("order").get("state").asText());
    assertEquals(2, completed.get("pickups").size());
    assertEquals(0, completed.get("balance").asLong());
    assertTrue(completed.get("garments").valueStream().allMatch(g -> g.get("rackId").isNull()));
  }

  @Test
  void pickupNeedsFullPayment() throws Exception {
    var o = order(customer, 1);
    long id = o.get("order").get("id").asLong();
    ready(id, 1);
    var d = detail(id);
    post(
        "/api/orders/" + id + "/pickup",
        cmd(
            "version",
            d.get("order").get("version").asLong(),
            "barcodes",
            List.of(d.get("garments").get(0).get("barcode").asText()),
            "receiver",
            "QA"),
        409);
  }

  @Test
  void wrongOrderAndDuplicateBarcodesRejected() throws Exception {
    var o = order(customer, 2);
    long id = o.get("order").get("id").asLong();
    ready(id, 2);
    var d = detail(id);
    post("/api/payments/ORDER/" + id, pay(d, 2598, "P_" + suffix), 200);
    d = detail(id);
    String code = d.get("garments").get(0).get("barcode").asText();
    post(
        "/api/orders/" + id + "/pickup",
        cmd(
            "version",
            d.get("order").get("version").asLong(),
            "barcodes",
            List.of(code, code),
            "receiver",
            "QA"),
        409);
    post(
        "/api/orders/" + id + "/pickup",
        cmd(
            "version",
            d.get("order").get("version").asLong(),
            "barcodes",
            List.of("G-OTHER"),
            "receiver",
            "QA"),
        409);
    assertEquals(0, detail(id).get("delivered").asInt());
  }

  @Test
  void rewashReleasesRackAndPreservesPrice() throws Exception {
    var o = order(customer, 1);
    long id = o.get("order").get("id").asLong();
    ready(id, 1);
    var d = change(id, 0, "rewash");
    assertEquals("PROCESSING", d.get("garments").get(0).get("state").asText());
    assertTrue(d.get("garments").get(0).get("rackId").isNull());
    assertEquals(1299, d.get("order").get("total").asLong());
    assertEquals(1, d.get("garments").get(0).get("rewashCount").asInt());
  }

  @Test
  void rackCapacityAndDisableGuard() throws Exception {
    var r =
        call(admin, "GET", "/api/catalog", null, 200)
            .get("racks")
            .valueStream()
            .filter(x -> x.get("record").get("id").asLong() == rack)
            .findFirst()
            .orElseThrow()
            .get("record");
    var rb = json.convertValue(r, Map.class);
    rb.put("capacity", 1);
    call(admin, "PUT", "/api/masters/racks/" + rack, rb, 200);
    var o = order(customer, 2);
    long id = o.get("order").get("id").asLong();
    ready(id, 1);
    change(id, 1, "start");
    change(id, 1, "ready");
    var g = detail(id).get("garments").get(1);
    post(
        "/api/garments/" + g.get("id").asLong() + "/rack",
        cmd("version", g.get("version").asLong(), "rackId", rack),
        409);
    rb.put("enabled", false);
    rb.put("version", 2);
    call(admin, "PUT", "/api/masters/racks/" + rack, rb, 409);
  }

  @Test
  void duplicatePaymentAndExcessGuard() throws Exception {
    var o = order(customer, 1);
    long id = o.get("order").get("id").asLong();
    var p = pay(o, 1000, "P_" + suffix);
    var a = post("/api/payments/ORDER/" + id, p, 200);
    assertEquals(
        a.get("id").asLong(), post("/api/payments/ORDER/" + id, p, 200).get("id").asLong());
    post("/api/payments/ORDER/" + id, pay(detail(id), 300, "Q_" + suffix), 409);
    assertEquals(1000, detail(id).get("paid").asLong());
  }

  @Test
  void staleVersionAndChangedRequestCannotOverwrite() throws Exception {
    var o = order(customer, 1);
    long id = o.get("order").get("id").asLong();
    var p = pay(o, 100, "P_" + suffix);
    post("/api/payments/ORDER/" + id, p, 200);
    p.put("amount", 200);
    post("/api/payments/ORDER/" + id, p, 409);
    post("/api/payments/ORDER/" + id, pay(o, 200, "Q_" + suffix), 409);
    assertEquals(100, detail(id).get("paid").asLong());
  }

  @Test
  void cancellationRefundReferencesOriginalAndRetainsHistory() throws Exception {
    var o = order(customer, 1);
    long id = o.get("order").get("id").asLong();
    var p = post("/api/payments/ORDER/" + id, pay(o, 1000, "P_" + suffix), 200);
    var d = detail(id);
    var cancelled =
        post(
            "/api/orders/" + id + "/cancel",
            cmd("version", d.get("order").get("version").asLong()),
            200);
    var body =
        cmd(
            "version",
            cancelled.get("order").get("version").asLong(),
            "kind",
            "REFUND",
            "amount",
            700,
            "originalId",
            p.get("id").asLong(),
            "reference",
            "R_" + suffix,
            "method",
            "BANK");
    post("/api/payments/ORDER/" + id, body, 200);
    d = detail(id);
    body =
        cmd(
            "version",
            d.get("order").get("version").asLong(),
            "kind",
            "REFUND",
            "amount",
            301,
            "originalId",
            p.get("id").asLong(),
            "reference",
            "R2_" + suffix,
            "method",
            "BANK");
    post("/api/payments/ORDER/" + id, body, 409);
    assertEquals(300, detail(id).get("paid").asLong());
    assertEquals(2, detail(id).get("payments").size());
  }

  @Test
  void processedOrderCannotCancel() throws Exception {
    var o = order(customer, 1);
    long id = o.get("order").get("id").asLong();
    var d = change(id, 0, "start");
    post(
        "/api/orders/" + id + "/cancel",
        cmd("version", d.get("order").get("version").asLong()),
        409);
  }

  @Test
  void corporateCreditAndMonthlyNoDoubleAccounting() throws Exception {
    long corp =
        post(
                "/api/masters/customers",
                b(
                    "departmentId",
                    1,
                    "name",
                    "QA corp",
                    "contact",
                    "",
                    "billing",
                    "MONTHLY",
                    "currency",
                    "CNY",
                    "creditLimit",
                    1500,
                    "enabled",
                    true),
                200)
            .get("id")
            .asLong();
    var o = order(corp, 1);
    long id = o.get("order").get("id").asLong();
    var items =
        List.of(
            b(
                "serviceId",
                service,
                "serviceVersion",
                1,
                "description",
                "QA extra",
                "conditionNote",
                "QA"));
    post(
        "/api/orders",
        cmd(
            "departmentId",
            1,
            "customerId",
            corp,
            "customerVersion",
            1,
            "dueDate",
            "2026-10-15",
            "items",
            items),
        409);
    ready(id, 1);
    var d = detail(id);
    post("/api/payments/ORDER/" + id, pay(d, 1299, "P_" + suffix), 409);
    post(
        "/api/orders/" + id + "/pickup",
        cmd(
            "version",
            d.get("order").get("version").asLong(),
            "barcodes",
            List.of(d.get("garments").get(0).get("barcode").asText()),
            "receiver",
            "QA corp recipient"),
        200);
    var body = cmd("customerId", corp, "month", "2026-10");
    var inv = post("/api/invoices", body, 200);
    long invoice = inv.get("invoice").get("id").asLong();
    assertEquals(1299, inv.get("balance").asLong());
    assertEquals(invoice, post("/api/invoices", body, 200).get("invoice").get("id").asLong());
    post("/api/invoices", cmd("customerId", corp, "month", "2026-10"), 409);
    post(
        "/api/payments/INVOICE/" + invoice,
        cmd(
            "version",
            1,
            "kind",
            "PAYMENT",
            "amount",
            1299,
            "reference",
            "I_" + suffix,
            "method",
            "BANK"),
        200);
    var result = call(admin, "GET", "/api/invoices/" + invoice, null, 200);
    assertEquals(0, result.get("balance").asLong());
    assertEquals("PAID", result.get("invoice").get("state").asText());
    assertNotNull(order(corp, 1));
  }

  MockHttpSession user(String scope, Set<String> permissions) throws Exception {
    var role =
        post(
            "/api/admin/roles",
            b("name", "QA role " + suffix, "scope", scope, "permissions", permissions),
            200);
    var account =
        post(
            "/api/admin/users",
            b(
                "username",
                "u_" + suffix,
                "displayName",
                "QA user",
                "password",
                PW,
                "roleId",
                role.get("id").asLong(),
                "departmentId",
                1,
                "enabled",
                true),
            200);
    return login(account.get("username").asText());
  }

  @Test
  void processingCannotCollectOrViewCustomerDirectory() throws Exception {
    var s = user("DEPARTMENT", Set.of("catalog", "process"));
    var o = order(customer, 1);
    long id = o.get("order").get("id").asLong();
    assertEquals(0, call(s, "GET", "/api/catalog", null, 200).get("customers").size());
    call(
        s,
        "POST",
        "/api/orders/" + id + "/pickup",
        cmd("version", 1, "barcodes", List.of(), "receiver", "QA"),
        403);
    call(s, "GET", "/api/admin/users", null, 403);
  }

  @Test
  void unboundCustomerNoDataAndNoElevatedRole() throws Exception {
    var s = user("CUSTOMER", Set.of("portal"));
    call(s, "GET", "/api/orders", null, 409);
    call(s, "GET", "/api/catalog", null, 403);
    post(
        "/api/admin/roles",
        b("name", "QA invalid", "scope", "CUSTOMER", "permissions", List.of("portal", "finance")),
        409);
  }

  @Test
  void customerOnlyOwnOrders() throws Exception {
    var s = user("CUSTOMER", Set.of("portal"));
    var me = call(s, "GET", "/api/auth/me", null, 200);
    long own =
        post(
                "/api/masters/customers",
                b(
                    "departmentId",
                    1,
                    "name",
                    "QA own",
                    "contact",
                    "",
                    "billing",
                    "RETAIL",
                    "currency",
                    "CNY",
                    "creditLimit",
                    0,
                    "accountId",
                    me.get("id").asLong(),
                    "enabled",
                    true),
                200)
            .get("id")
            .asLong();
    var a = order(own, 1);
    var other = order(customer, 1);
    ready(a.get("order").get("id").asLong(), 1);
    var portal = call(s, "GET", "/api/orders/" + a.get("order").get("id").asLong(), null, 200);
    assertEquals(1, portal.get("locations").size());
    assertEquals("R_" + suffix, portal.get("locations").get(0).get("code").asText());
    call(s, "GET", "/api/orders/" + a.get("order").get("id").asLong(), null, 200);
    call(s, "GET", "/api/orders/" + other.get("order").get("id").asLong(), null, 403);
    assertEquals(1, call(s, "GET", "/api/orders", null, 200).get("total").asInt());
  }

  @Test
  void customerCannotBorrowStaffScope() throws Exception {
    post(
        "/api/admin/roles",
        b("name", "QA invalid", "scope", "CUSTOMER", "permissions", List.of("portal", "orders")),
        409);
  }

  @Test
  void anonymousAndMissingCsrfAreRejected() throws Exception {
    call(null, "GET", "/api/orders", null, 401);
    assertEquals(
        403,
        mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                        "/api/orders")
                    .session(admin)
                    .contentType("application/json")
                    .content("{}"))
            .andReturn()
            .getResponse()
            .getStatus());
  }

  @Test
  void passwordHashNeverReturned() throws Exception {
    var rows = call(admin, "GET", "/api/admin/users", null, 200);
    assertFalse(rows.toString().contains("passwordHash"));
    assertFalse(rows.toString().contains("$2"));
  }

  @Test
  void priceSnapshotDoesNotFollowEditedCatalog() throws Exception {
    var o = order(customer, 1);
    var c =
        call(admin, "GET", "/api/catalog", null, 200)
            .get("services")
            .valueStream()
            .filter(x -> x.get("id").asLong() == service)
            .findFirst()
            .orElseThrow();
    var b = json.convertValue(c, Map.class);
    b.put("price", 9999);
    call(admin, "PUT", "/api/masters/services/" + service, b, 200);
    assertEquals(
        1299, detail(o.get("order").get("id").asLong()).get("order").get("total").asLong());
  }

  @Test
  void concurrentIdenticalPaymentOnlyCreatesOneLedgerEntry() throws Exception {
    var o = order(customer, 1);
    long id = o.get("order").get("id").asLong();
    var b = pay(o, 1299, "P_" + suffix);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var a = executor.submit(() -> post("/api/payments/ORDER/" + id, b, 200));
      var c = executor.submit(() -> post("/api/payments/ORDER/" + id, b, 200));
      assertEquals(a.get().get("id").asLong(), c.get().get("id").asLong());
    }
    assertEquals(1, detail(id).get("payments").size());
  }

  @Test
  void photoRoundTripAndStateLock() throws Exception {
    var o = order(customer, 1);
    long id = o.get("order").get("id").asLong(), g = o.get("garments").get(0).get("id").asLong();
    var bytes = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(
        new java.awt.image.BufferedImage(12, 12, java.awt.image.BufferedImage.TYPE_INT_RGB),
        "png",
        bytes);
    var file = new MockMultipartFile("file", "qa.png", "image/png", bytes.toByteArray());
    var out =
        mvc.perform(
                multipart("/api/garments/" + g + "/photos")
                    .file(file)
                    .param("version", "1")
                    .session(admin)
                    .with(csrf()))
            .andReturn()
            .getResponse();
    assertEquals(200, out.getStatus(), out.getContentAsString());
    long p = json.readTree(out.getContentAsString()).get("id").asLong();
    var image = mvc.perform(get("/api/photos/" + p).session(admin)).andReturn().getResponse();
    assertArrayEquals(bytes.toByteArray(), image.getContentAsByteArray());
    assertEquals("no-store", image.getHeader("Cache-Control"));
    change(id, 0, "start");
    out =
        mvc.perform(
                multipart("/api/garments/" + g + "/photos")
                    .file(file)
                    .param("version", "3")
                    .session(admin)
                    .with(csrf()))
            .andReturn()
            .getResponse();
    assertEquals(409, out.getStatus());
  }

  @Test
  void disablingLastAdministratorRollsBack() throws Exception {
    var users = call(admin, "GET", "/api/admin/users", null, 200);
    var row =
        users
            .valueStream()
            .filter(a -> a.get("username").asText().equals("admin"))
            .findFirst()
            .orElseThrow();
    var body = json.convertValue(row, Map.class);
    body.put("enabled", false);
    body.put("password", "");
    call(admin, "PUT", "/api/admin/users/" + row.get("id").asLong(), body, 409);
    call(admin, "GET", "/api/auth/me", null, 200);
  }

  @Test
  void resettingPasswordRevokesExistingSession() throws Exception {
    var session = user("DEPARTMENT", Set.of("catalog", "orders"));
    var me = call(session, "GET", "/api/auth/me", null, 200);
    var row =
        call(admin, "GET", "/api/admin/users", null, 200)
            .valueStream()
            .filter(a -> a.get("id").asLong() == me.get("id").asLong())
            .findFirst()
            .orElseThrow();
    var body = json.convertValue(row, Map.class);
    body.put("password", "Aa9" + UUID.randomUUID());
    call(admin, "PUT", "/api/admin/users/" + me.get("id").asLong(), body, 200);
    call(session, "GET", "/api/auth/me", null, 401);
  }

  @Test
  void roleChangesApplyWithoutAnotherLogin() throws Exception {
    var session = user("DEPARTMENT", Set.of("catalog", "orders"));
    var me = call(session, "GET", "/api/auth/me", null, 200);
    var role =
        call(admin, "GET", "/api/admin/roles", null, 200)
            .valueStream()
            .filter(a -> a.get("name").asText().equals(me.get("role").asText()))
            .findFirst()
            .orElseThrow();
    var body = json.convertValue(role, Map.class);
    body.put("permissions", List.of("catalog", "process"));
    call(admin, "PUT", "/api/admin/roles/" + role.get("id").asLong(), body, 200);
    call(session, "POST", "/api/orders", cmd("departmentId", 1), 403);
    call(session, "GET", "/api/catalog", null, 200);
  }

  @Test
  void retailCustomerContactRemainsEditableWithAnOrder() throws Exception {
    order(customer, 1);
    var c =
        call(admin, "GET", "/api/catalog", null, 200)
            .get("customers")
            .valueStream()
            .filter(x -> x.get("id").asLong() == customer)
            .findFirst()
            .orElseThrow();
    var b = json.convertValue(c, Map.class);
    b.put("contact", "QA preferred contact note");
    call(admin, "PUT", "/api/masters/customers/" + customer, b, 200);
  }

  @Test
  void oversizedLoginPasswordHasUniformFailure() throws Exception {
    var session = new MockHttpSession();
    String value = "A9" + "a".repeat(73);
    call(session, "POST", "/api/auth/login", b("username", "admin", "password", value), 401);
    call(
        session,
        "POST",
        "/api/auth/login",
        b("username", "missing_" + suffix, "password", value),
        401);
  }

  @Test
  void disabledCategoryCanArchiveServiceWithoutReopeningIt() throws Exception {
    var row =
        call(admin, "GET", "/api/admin/dictionaries", null, 200)
            .valueStream()
            .filter(c -> c.get("code").asText().equals("DRYCLEAN"))
            .findFirst()
            .orElseThrow();
    var category = json.convertValue(row, Map.class);
    category.put("enabled", false);
    call(admin, "PUT", "/api/admin/dictionaries/" + row.get("id").asLong(), category, 200);
    try {
      var serviceRow =
          call(admin, "GET", "/api/catalog", null, 200)
              .get("services")
              .valueStream()
              .filter(x -> x.get("id").asLong() == service)
              .findFirst()
              .orElseThrow();
      var body = json.convertValue(serviceRow, Map.class);
      body.put("enabled", false);
      call(admin, "PUT", "/api/masters/services/" + service, body, 200);
    } finally {
      category.put("enabled", true);
      call(admin, "PUT", "/api/admin/dictionaries/" + row.get("id").asLong(), category, 200);
    }
  }

  @Test
  void intakeRejectsChangedPriceOrCustomerSnapshot() throws Exception {
    var serviceRow =
        call(admin, "GET", "/api/catalog", null, 200)
            .get("services")
            .valueStream()
            .filter(x -> x.get("id").asLong() == service)
            .findFirst()
            .orElseThrow();
    var rate = json.convertValue(serviceRow, Map.class);
    rate.put("price", 2000);
    call(admin, "PUT", "/api/masters/services/" + service, rate, 200);
    var items =
        List.of(
            b(
                "serviceId",
                service,
                "serviceVersion",
                1,
                "description",
                "QA stale quote",
                "conditionNote",
                "QA"));
    post(
        "/api/orders",
        cmd(
            "departmentId",
            1,
            "customerId",
            customer,
            "customerVersion",
            1,
            "dueDate",
            "2026-10-15",
            "items",
            items),
        409);
    var c =
        call(admin, "GET", "/api/catalog", null, 200)
            .get("customers")
            .valueStream()
            .filter(x -> x.get("id").asLong() == customer)
            .findFirst()
            .orElseThrow();
    var customerBody = json.convertValue(c, Map.class);
    customerBody.put("contact", "QA changed contact");
    call(admin, "PUT", "/api/masters/customers/" + customer, customerBody, 200);
    items =
        List.of(
            b(
                "serviceId",
                service,
                "serviceVersion",
                2,
                "description",
                "QA fresh quote",
                "conditionNote",
                "QA"));
    post(
        "/api/orders",
        cmd(
            "departmentId",
            1,
            "customerId",
            customer,
            "customerVersion",
            1,
            "dueDate",
            "2026-10-15",
            "items",
            items),
        409);
    var accepted =
        post(
            "/api/orders",
            cmd(
                "departmentId",
                1,
                "customerId",
                customer,
                "customerVersion",
                2,
                "dueDate",
                "2026-10-15",
                "items",
                items),
            200);
    assertEquals(2000, accepted.get("order").get("total").asLong());
  }
}
