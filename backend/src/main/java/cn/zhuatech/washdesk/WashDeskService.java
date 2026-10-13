// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.washdesk;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 逐件收衣、加工、分批交接和企业月结；事务锁保护资金和物品守恒。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class WashDeskService {
  final Store db;
  final AccessService access;
  final AdminService admin;
  final CommandService commands;
  final Clock clock;

  public WashDeskService(
      Store db, AccessService access, AdminService admin, CommandService commands, Clock clock) {
    this.db = db;
    this.access = access;
    this.admin = admin;
    this.commands = commands;
    this.clock = clock;
  }

  void scope(Long dept, String code) {
    access.library(dept, code);
    Rules.check(!access.role().scope.equals("CUSTOMER"), "FORBIDDEN");
  }

  void event(String type, Long id, Long dept, String action, String note) {
    var e = new BusinessEvent();
    e.objectType = type;
    e.objectId = id;
    e.departmentId = dept;
    e.action = action;
    e.note = Rules.paragraph(note, 2000, false);
    e.actor = access.current().username;
    e.createdAt = clock.instant();
    db.save(e);
    access.audit(action, id, dept);
  }

  List<Garment> garments(Long order) {
    return db.query(Garment.class, "from Garment where orderId=?1 order by id", order);
  }

  Customer ownCustomer() {
    var rows = db.query(Customer.class, "from Customer where accountId=?1", access.current().id);
    Rules.check(rows.size() == 1 && rows.getFirst().enabled, "CUSTOMER_PROFILE_REQUIRED");
    return rows.getFirst();
  }

  boolean customer() {
    return access.role().scope.equals("CUSTOMER");
  }

  void read(WashOrder o) {
    if (customer()) {
      access.require("portal");
      if (!o.customerId.equals(ownCustomer().id)) throw new Problem(403, "OUT_OF_SCOPE");
    } else {
      if (!(access.has("orders")
          || access.has("process")
          || access.has("pickup")
          || access.has("finance")
          || access.has("reports"))) throw new Problem(403, "FORBIDDEN");
      if (!access.department(o.departmentId)) throw new Problem(403, "OUT_OF_SCOPE");
    }
  }

  void invoiceRead(MonthlyInvoice x) {
    if (customer()) {
      access.require("portal");
      if (!x.customerId.equals(ownCustomer().id)) throw new Problem(403, "OUT_OF_SCOPE");
    } else scope(x.departmentId, "finance");
  }

  long paid(String type, Long id) {
    return db
        .query(LedgerEntry.class, "from LedgerEntry where targetType=?1 and targetId=?2", type, id)
        .stream()
        .mapToLong(e -> e.kind.equals("REFUND") ? -e.amount : e.amount)
        .sum();
  }

  /** 角色内最小业务目录，客户不接收全体客户／员工资料。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> catalog() {
    access.require("catalog");
    Rules.check(!customer(), "FORBIDDEN");
    var depts = db.all(Department.class).stream().filter(d -> access.department(d.id)).toList();
    var rows = new LinkedHashMap<String, Object>();
    rows.put("departments", depts);
    rows.put("categories", db.all(DictionaryEntry.class));
    rows.put(
        "services",
        db.all(WashService.class).stream().filter(x -> access.department(x.departmentId)).toList());
    rows.put(
        "racks",
        db.all(Rack.class).stream()
            .filter(x -> access.department(x.departmentId))
            .map(x -> Map.of("record", x, "occupied", occupied(x.id)))
            .toList());
    if (access.has("customers") || access.has("orders") || access.has("finance"))
      rows.put(
          "customers",
          db.all(Customer.class).stream().filter(x -> access.department(x.departmentId)).toList());
    else rows.put("customers", List.of());
    if (access.has("customers"))
      rows.put(
          "customerAccounts",
          db.all(Account.class).stream()
              .filter(
                  a ->
                      access.department(a.departmentId)
                          && db.get(AccessRole.class, a.roleId).scope.equals("CUSTOMER"))
              .map(
                  a ->
                      Map.of(
                          "id",
                          a.id,
                          "username",
                          a.username,
                          "departmentId",
                          a.departmentId,
                          "enabled",
                          a.enabled))
              .toList());
    else rows.put("customerAccounts", List.of());
    return rows;
  }

  long occupied(Long rack) {
    return db.query(Garment.class, "from Garment where rackId=?1 and state='RACKED'", rack).size();
  }

  /** 客户／价目／挂架维护；已有历史冻结客户结算方式、币种、门店和账号归属。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object master(String type, Long id, Map<String, Object> b) {
    admin.lock();
    Long dept = Rules.id(b.get("departmentId"));
    scope(dept, type.equals("customers") ? "customers" : "masters");
    Rules.check(db.get(Department.class, dept).enabled, "DEPARTMENT_DISABLED");
    RecordBase entity;
    switch (type) {
      case "customers" -> {
        var x = id == null ? new Customer() : db.get(Customer.class, id);
        identity(x, dept, id, b);
        String mode = Rules.text(b.get("billing"), 20, true);
        Rules.check(Set.of("RETAIL", "MONTHLY").contains(mode), "INVALID_INPUT");
        String curr = WashPolicy.currency(b.get("currency"));
        Long account =
            b.get("accountId") == null || b.get("accountId").toString().isEmpty()
                ? null
                : Rules.id(b.get("accountId"));
        if (account != null) {
          var a = db.get(Account.class, account);
          Rules.check(
              a.enabled
                  && a.departmentId.equals(dept)
                  && db.get(AccessRole.class, a.roleId).scope.equals("CUSTOMER"),
              "CUSTOMER_ACCOUNT_INVALID");
        }
        if (id != null
            && !db.query(WashOrder.class, "from WashOrder where customerId=?1", id).isEmpty())
          Rules.check(
              x.currency.equals(curr)
                  && x.billing.equals(mode)
                  && (x.accountId == null || Objects.equals(x.accountId, account)),
              "IDENTITY_LOCKED");
        x.name = Rules.text(b.get("name"), 120, true);
        x.contact = Rules.text(b.get("contact"), 120, false);
        x.billing = mode;
        x.currency = curr;
        x.accountId = account;
        x.creditLimit = Rules.integer(b.get("creditLimit"), 0, 100000000);
        if (mode.equals("MONTHLY")) Rules.check(x.creditLimit >= exposure(x.id), "CREDIT_LIMIT");
        x.enabled = Rules.flag(b.get("enabled"));
        entity = x;
      }
      case "services" -> {
        var x = id == null ? new WashService() : db.get(WashService.class, id);
        identity(x, dept, id, b);
        String category = Rules.text(b.get("category"), 60, true);
        boolean serviceEnabled = Rules.flag(b.get("enabled"));
        Rules.check(
            db.all(DictionaryEntry.class).stream()
                .anyMatch(c -> c.code.equals(category) && (!serviceEnabled || c.enabled)),
            "CATEGORY_DISABLED");
        x.name = Rules.text(b.get("name"), 120, true);
        x.nameEn = Rules.text(b.get("nameEn"), 120, true);
        x.category = category;
        x.currency = WashPolicy.currency(b.get("currency"));
        x.price = WashPolicy.amount(b.get("price"));
        x.enabled = Rules.flag(b.get("enabled"));
        entity = x;
      }
      case "racks" -> {
        var x = id == null ? new Rack() : db.get(Rack.class, id);
        identity(x, dept, id, b);
        String code = Rules.text(b.get("code"), 60, true);
        Rules.check(code.matches("[A-Za-z0-9_-]{1,60}"), "INVALID_INPUT");
        if (id != null) Rules.check(code.equals(x.code), "IDENTITY_LOCKED");
        x.code = code;
        x.name = Rules.text(b.get("name"), 120, true);
        x.capacity = Rules.integer(b.get("capacity"), 1, 10000);
        x.enabled = Rules.flag(b.get("enabled"));
        Rules.check(
            id == null || (x.capacity >= occupied(id) && (x.enabled || occupied(id) == 0)),
            "RACK_OCCUPIED");
        entity = x;
      }
      default -> throw new Problem(404, "NOT_FOUND");
    }
    entity.departmentId = dept;
    if (id == null) db.save(entity);
    else entity.version++;
    db.flush();
    event(type, entity.id, dept, "MASTER_SAVE", "");
    return entity;
  }

  void identity(RecordBase x, Long dept, Long id, Map<String, Object> b) {
    if (id != null) {
      scope(x.departmentId, x instanceof Customer ? "customers" : "masters");
      Rules.check(x.departmentId.equals(dept), "IDENTITY_LOCKED");
      version(x, b);
    }
  }

  void version(RecordBase x, Map<String, Object> b) {
    Rules.check(x.version == Rules.id(b.get("version")), "VERSION_CONFLICT");
  }

  long exposure(Long customerId) {
    if (customerId == null) return 0;
    return db
            .query(
                WashOrder.class,
                "from WashOrder where customerId=?1 and state<>'CANCELLED'",
                customerId)
            .stream()
            .mapToLong(o -> o.invoiceId == null ? o.total : 0)
            .sum()
        + db
            .query(MonthlyInvoice.class, "from MonthlyInvoice where customerId=?1", customerId)
            .stream()
            .mapToLong(x -> x.total - paid("INVOICE", x.id))
            .sum();
  }

  /** 冻结逐件价格和身份，企业授信在接单时原子核验。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object createOrder(Map<String, Object> b) {
    admin.lock();
    Long dept = Rules.id(b.get("departmentId"));
    scope(dept, "orders");
    var stamp = commands.begin("CREATE_ORDER", b);
    if (stamp.id != null) return detail(stamp.resultId);
    var customer = db.get(Customer.class, Rules.id(b.get("customerId")));
    Rules.check(
        customer.enabled
            && customer.departmentId.equals(dept)
            && db.get(Department.class, dept).enabled,
        "CUSTOMER_INVALID");
    Rules.check(customer.version == Rules.id(b.get("customerVersion")), "VERSION_CONFLICT");
    var date = LocalDate.parse(Rules.text(b.get("dueDate"), 10, true));
    Rules.check(
        !date.isBefore(
                LocalDate.now(clock.withZone(ZoneId.of(db.get(Department.class, dept).zone))))
            && date.isBefore(LocalDate.now(clock).plusYears(2)),
        "DATE_INVALID");
    if (!(b.get("items") instanceof List<?> list)
        || list.isEmpty()
        || list.size() > admin.setting("orderItemLimit")) throw new Problem(400, "INVALID_INPUT");
    var order = new WashOrder();
    order.departmentId = dept;
    order.customerId = customer.id;
    order.customerName = customer.name;
    order.billing = customer.billing;
    order.currency = customer.currency;
    order.dueDate = date;
    order.note = Rules.paragraph(b.get("note"), 2000, false);
    order.createdAt = clock.instant();
    order.number = "WD-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT);
    var garments = new ArrayList<Garment>();
    for (Object item : list) {
      if (!(item instanceof Map<?, ?> m)) throw new Problem(400, "INVALID_INPUT");
      var rate = db.get(WashService.class, Rules.id(m.get("serviceId")));
      Rules.check(
          rate.enabled
              && rate.departmentId.equals(dept)
              && rate.currency.equals(order.currency)
              && db.all(DictionaryEntry.class).stream()
                  .anyMatch(c -> c.enabled && c.code.equals(rate.category)),
          "SERVICE_INVALID");
      Rules.check(rate.version == Rules.id(m.get("serviceVersion")), "VERSION_CONFLICT");
      var g = new Garment();
      g.departmentId = dept;
      g.description = Rules.text(m.get("description"), 200, true);
      g.conditionNote = Rules.paragraph(m.get("conditionNote"), 1000, true);
      g.serviceName = rate.name;
      g.serviceNameEn = rate.nameEn;
      g.price = rate.price;
      g.barcode = "G-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
      order.total = Math.addExact(order.total, g.price);
      garments.add(g);
    }
    Rules.check(order.total <= 100000000, "AMOUNT_LIMIT");
    if (customer.billing.equals("MONTHLY"))
      Rules.check(exposure(customer.id) + order.total <= customer.creditLimit, "CREDIT_LIMIT");
    db.save(order);
    for (var g : garments) {
      g.orderId = order.id;
      db.save(g);
    }
    event("ORDER", order.id, dept, "RECEIVE", order.note);
    commands.complete(stamp, "ORDER", order.id);
    return detail(order.id);
  }

  /** 列表分页、按单号或逐件编号精确定位，门店／客户范围先过滤。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> orders(String q, String state, int page, String sort) {
    Rules.check(page >= 1 && page <= 1000, "INVALID_INPUT");
    String search = Rules.text(q, 120, false).toLowerCase(Locale.ROOT);
    Rules.check(Set.of("newest", "oldest", "due").contains(sort), "INVALID_INPUT");
    Comparator<WashOrder> ordering =
        sort.equals("due")
            ? Comparator.comparing((WashOrder o) -> o.dueDate).thenComparing(o -> o.id)
            : sort.equals("oldest")
                ? Comparator.comparing(o -> o.id)
                : Comparator.comparing((WashOrder o) -> o.id).reversed();
    Long owner = customer() ? ownCustomer().id : null;
    if (customer()) access.require("portal");
    else if (!(access.has("orders")
        || access.has("process")
        || access.has("pickup")
        || access.has("finance")
        || access.has("reports"))) throw new Problem(403, "FORBIDDEN");
    var matching =
        db.all(WashOrder.class).stream()
            .filter(
                o -> owner != null ? o.customerId.equals(owner) : access.department(o.departmentId))
            .filter(o -> state.isEmpty() || o.state.equals(state))
            .filter(
                o ->
                    search.isEmpty()
                        || (o.number + " " + o.customerName)
                            .toLowerCase(Locale.ROOT)
                            .contains(search)
                        || garments(o.id).stream()
                            .anyMatch(g -> g.barcode.toLowerCase(Locale.ROOT).equals(search)))
            .sorted(ordering)
            .toList();
    return Map.of(
        "total",
        matching.size(),
        "page",
        page,
        "rows",
        matching.stream().skip((page - 1L) * 20).limit(20).map(this::summary).toList());
  }

  Map<String, Object> summary(WashOrder o) {
    var items = garments(o.id);
    return Map.of(
        "order",
        o,
        "items",
        items.size(),
        "racked",
        items.stream().filter(g -> g.state.equals("RACKED")).count(),
        "delivered",
        items.stream().filter(g -> g.state.equals("DELIVERED")).count(),
        "paid",
        o.billing.equals("RETAIL") ? paid("ORDER", o.id) : 0,
        "balance",
        o.state.equals("CANCELLED")
            ? 0
            : o.billing.equals("RETAIL") ? o.total - paid("ORDER", o.id) : o.total);
  }

  /** 一个订单的衣物、外观照片元数据、交接与资金历史。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> detail(Long id) {
    var o = db.get(WashOrder.class, id);
    read(o);
    var result = new LinkedHashMap<>(summary(o));
    result.put("garments", garments(id));
    result.put(
        "locations",
        garments(id).stream()
            .map(g -> g.rackId)
            .filter(Objects::nonNull)
            .distinct()
            .map(
                rid -> {
                  var rack = db.get(Rack.class, rid);
                  return Map.of("id", rack.id, "code", rack.code, "name", rack.name);
                })
            .toList());
    result.put("pickups", db.query(Pickup.class, "from Pickup where orderId=?1", id));
    result.put(
        "pickupItems",
        db.all(PickupItem.class).stream()
            .filter(x -> garments(id).stream().anyMatch(g -> g.id.equals(x.garmentId)))
            .toList());
    result.put(
        "payments",
        db.query(
            LedgerEntry.class, "from LedgerEntry where targetType='ORDER' and targetId=?1", id));
    result.put(
        "events",
        db.query(
            BusinessEvent.class,
            "from BusinessEvent where objectType='ORDER' and objectId=?1 order by id",
            id));
    result.put(
        "photos",
        garments(id).stream()
            .flatMap(
                g ->
                    db
                        .query(GarmentPhoto.class, "from GarmentPhoto where garmentId=?1", g.id)
                        .stream())
            .map(p -> Map.of("id", p.id, "garmentId", p.garmentId, "createdAt", p.createdAt))
            .toList());
    return result;
  }

  /** 加工、质检、返洗和挂架使用当前衣物版本；返洗必须有原因。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object garment(Long id, String action, Map<String, Object> b) {
    admin.lock();
    var g = db.get(Garment.class, id);
    scope(g.departmentId, "process");
    var stamp = commands.begin("GARMENT_" + id + "_" + action, b);
    if (stamp.id != null) return detail(g.orderId);
    var o = db.get(WashOrder.class, g.orderId);
    Rules.check(o.state.equals("OPEN"), "STATE_INVALID");
    version(g, b);
    String next = WashPolicy.transition(g.state, action);
    String note = Rules.paragraph(b.get("note"), 1000, action.equals("rewash"));
    if (action.equals("rack")) {
      var rack = db.get(Rack.class, Rules.id(b.get("rackId")));
      Rules.check(
          rack.enabled
              && rack.departmentId.equals(g.departmentId)
              && ((Objects.equals(g.rackId, rack.id) && g.state.equals("RACKED"))
                  || occupied(rack.id) < rack.capacity),
          "RACK_FULL");
      g.rackId = rack.id;
    } else g.rackId = null;
    if (action.equals("rewash")) g.rewashCount++;
    g.state = next;
    g.version++;
    o.version++;
    event(
        "ORDER",
        o.id,
        o.departmentId,
        "GARMENT_" + action.toUpperCase(Locale.ROOT),
        g.barcode + " " + note);
    commands.complete(stamp, "ORDER", o.id);
    return detail(o.id);
  }

  /** 扫描清单必须匹配本单、去重、已挂架；取走和挂架释放在同一事务。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object pickup(Long id, Map<String, Object> b) {
    admin.lock();
    var o = db.get(WashOrder.class, id);
    scope(o.departmentId, "pickup");
    var stamp = commands.begin("PICKUP_" + id, b);
    if (stamp.id != null) return detail(id);
    version(o, b);
    Rules.check(o.state.equals("OPEN"), "STATE_INVALID");
    if (o.billing.equals("RETAIL")) Rules.check(paid("ORDER", id) == o.total, "PAYMENT_REQUIRED");
    if (!(b.get("barcodes") instanceof List<?> codes)) throw new Problem(400, "INVALID_INPUT");
    var items = garments(id);
    var ids = new ArrayList<Long>();
    for (var code : codes) {
      String barcode = Rules.text(code, 60, true);
      var row =
          items.stream()
              .filter(g -> g.barcode.equals(barcode))
              .findFirst()
              .orElseThrow(() -> new Problem(409, "PICKUP_INVALID"));
      ids.add(row.id);
    }
    WashPolicy.collection(ids, items);
    var p = new Pickup();
    p.departmentId = o.departmentId;
    p.orderId = id;
    p.receiver = Rules.text(b.get("receiver"), 120, true);
    p.note = Rules.paragraph(b.get("note"), 1000, false);
    p.actor = access.current().username;
    p.createdAt = clock.instant();
    db.save(p);
    for (Long gid : ids) {
      var g = db.get(Garment.class, gid);
      g.state = "DELIVERED";
      g.rackId = null;
      g.deliveredAt = clock.instant();
      g.version++;
      var pi = new PickupItem();
      pi.departmentId = o.departmentId;
      pi.pickupId = p.id;
      pi.garmentId = g.id;
      pi.barcode = g.barcode;
      db.save(pi);
    }
    if (items.stream().allMatch(g -> g.state.equals("DELIVERED"))) {
      o.state = "COMPLETED";
      o.completedAt = clock.instant();
    }
    o.version++;
    event("ORDER", o.id, o.departmentId, "PICKUP", p.note);
    commands.complete(stamp, "ORDER", o.id);
    return detail(id);
  }

  /** 取消仅限加工前，资金不自动删除，之后按原款退款。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object cancel(Long id, Map<String, Object> b) {
    admin.lock();
    var o = db.get(WashOrder.class, id);
    scope(o.departmentId, "orders");
    var stamp = commands.begin("CANCEL_" + id, b);
    if (stamp.id != null) return detail(id);
    version(o, b);
    Rules.check(
        o.state.equals("OPEN") && garments(id).stream().allMatch(g -> g.state.equals("RECEIVED")),
        "CANCEL_INVALID");
    String note = Rules.paragraph(b.get("note"), 1000, true);
    o.state = "CANCELLED";
    o.version++;
    event("ORDER", id, o.departmentId, "CANCEL", note);
    commands.complete(stamp, "ORDER", id);
    return detail(id);
  }

  /** 人工核实收退款；退款绑定已取消订单原款，凭据和请求幂等。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object payment(String type, Long id, Map<String, Object> b) {
    admin.lock();
    RecordBase target;
    String currency;
    long total;
    String state;
    if (type.equals("ORDER")) {
      var o = db.get(WashOrder.class, id);
      scope(o.departmentId, "finance");
      target = o;
      currency = o.currency;
      total = o.total;
      state = o.state;
      Rules.check(o.billing.equals("RETAIL"), "INVOICE_REQUIRED");
    } else if (type.equals("INVOICE")) {
      var x = db.get(MonthlyInvoice.class, id);
      scope(x.departmentId, "finance");
      target = x;
      currency = x.currency;
      total = x.total;
      state = x.state;
    } else throw new Problem(400, "INVALID_INPUT");
    scope(target.departmentId, "finance");
    var stamp = commands.begin("PAYMENT_" + type + "_" + id, b);
    if (stamp.id != null) return db.get(LedgerEntry.class, stamp.resultId);
    version(target, b);
    String kind = Rules.text(b.get("kind"), 20, true);
    Rules.check(Set.of("PAYMENT", "REFUND").contains(kind), "INVALID_INPUT");
    long amount = WashPolicy.amount(b.get("amount"));
    var e = new LedgerEntry();
    e.departmentId = target.departmentId;
    e.targetType = type;
    e.targetId = id;
    e.kind = kind;
    e.amount = amount;
    e.currency = currency;
    e.reference = Rules.text(b.get("reference"), 120, true);
    e.method = Rules.text(b.get("method"), 30, true);
    Rules.check(Set.of("CASH", "BANK", "VERIFIED_OTHER").contains(e.method), "INVALID_INPUT");
    Rules.check(
        db.query(LedgerEntry.class, "from LedgerEntry where reference=?1", e.reference).isEmpty(),
        "PAYMENT_REFERENCE_REUSED");
    long net = paid(type, id);
    if (kind.equals("PAYMENT"))
      Rules.check(!state.equals("CANCELLED") && amount <= total - net, "PAYMENT_EXCESS");
    else {
      Rules.check(type.equals("ORDER") && state.equals("CANCELLED"), "REFUND_INVALID");
      var original = db.get(LedgerEntry.class, Rules.id(b.get("originalId")));
      Rules.check(
          original.targetType.equals(type)
              && original.targetId.equals(id)
              && original.kind.equals("PAYMENT"),
          "REFUND_INVALID");
      long refunded =
          db.query(LedgerEntry.class, "from LedgerEntry where originalId=?1", original.id).stream()
              .mapToLong(x -> x.amount)
              .sum();
      Rules.check(amount <= original.amount - refunded && amount <= net, "REFUND_EXCESS");
      e.originalId = original.id;
    }
    e.actor = access.current().username;
    e.createdAt = clock.instant();
    db.save(e);
    target.version++;
    if (target instanceof MonthlyInvoice x) x.state = net + amount == total ? "PAID" : "OPEN";
    event(type, id, target.departmentId, kind, "");
    commands.complete(stamp, "LEDGER", e.id);
    return e;
  }

  /** 月结冻结本月已取完、未开单的企业订单，不与零售收款混算。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object invoice(Map<String, Object> b) {
    admin.lock();
    var c = db.get(Customer.class, Rules.id(b.get("customerId")));
    scope(c.departmentId, "finance");
    var stamp = commands.begin("INVOICE", b);
    if (stamp.id != null) return invoiceDetail(stamp.resultId);
    Rules.check(c.billing.equals("MONTHLY"), "CUSTOMER_INVALID");
    var month = YearMonth.parse(Rules.text(b.get("month"), 7, true));
    var zone = ZoneId.of(db.get(Department.class, c.departmentId).zone);
    Rules.check(!month.isAfter(YearMonth.now(clock.withZone(zone))), "DATE_INVALID");
    var rows =
        db
            .query(
                WashOrder.class,
                "from WashOrder where customerId=?1 and state='COMPLETED' and invoiceId is null",
                c.id)
            .stream()
            .filter(o -> YearMonth.from(o.completedAt.atZone(zone)).equals(month))
            .toList();
    Rules.check(!rows.isEmpty(), "INVOICE_EMPTY");
    var x = new MonthlyInvoice();
    x.customerId = c.id;
    x.customerName = c.name;
    x.departmentId = c.departmentId;
    x.month = month.toString();
    x.currency = c.currency;
    x.number = "MI-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT);
    x.total = rows.stream().mapToLong(o -> o.total).sum();
    x.createdAt = clock.instant();
    db.save(x);
    for (var o : rows) {
      o.invoiceId = x.id;
      o.version++;
    }
    event("INVOICE", x.id, x.departmentId, "ISSUE", "");
    commands.complete(stamp, "INVOICE", x.id);
    return invoiceDetail(x.id);
  }

  /** 岗位／本人范围月结列表。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object invoices() {
    if (customer()) access.require("portal");
    else access.require("finance");
    Long own = customer() ? ownCustomer().id : null;
    return db.all(MonthlyInvoice.class).stream()
        .filter(x -> own != null ? x.customerId.equals(own) : access.department(x.departmentId))
        .map(
            x ->
                Map.of(
                    "invoice",
                    x,
                    "paid",
                    paid("INVOICE", x.id),
                    "balance",
                    x.total - paid("INVOICE", x.id)))
        .toList();
  }

  /** 开单成员和款项只读详情。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object invoiceDetail(Long id) {
    var x = db.get(MonthlyInvoice.class, id);
    invoiceRead(x);
    return Map.of(
        "invoice",
        x,
        "orders",
        db.query(WashOrder.class, "from WashOrder where invoiceId=?1", id),
        "payments",
        db.query(
            LedgerEntry.class, "from LedgerEntry where targetType='INVOICE' and targetId=?1", id),
        "paid",
        paid("INVOICE", id),
        "balance",
        x.total - paid("INVOICE", id));
  }

  /** 实际衣物状态与应收分币种聚合；不将订单月结应收重复统计。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object report() {
    access.require("reports");
    var orders =
        db.all(WashOrder.class).stream().filter(o -> access.department(o.departmentId)).toList();
    var currencies = new TreeMap<String, Map<String, Long>>();
    for (var o : orders) {
      var m =
          currencies.computeIfAbsent(
              o.currency,
              k ->
                  new TreeMap<>(
                      Map.of("sales", 0L, "received", 0L, "receivable", 0L, "refundDue", 0L)));
      if (!o.state.equals("CANCELLED")) {
        m.merge("sales", o.total, Long::sum);
        if (o.billing.equals("RETAIL"))
          m.merge("receivable", o.total - paid("ORDER", o.id), Long::sum);
        else if (o.invoiceId == null) m.merge("receivable", o.total, Long::sum);
      } else m.merge("refundDue", paid("ORDER", o.id), Long::sum);
    }
    for (var x : db.all(MonthlyInvoice.class))
      if (access.department(x.departmentId))
        currencies.get(x.currency).merge("receivable", x.total - paid("INVOICE", x.id), Long::sum);
    for (var e : db.all(LedgerEntry.class))
      if (access.department(e.departmentId))
        currencies
            .get(e.currency)
            .merge("received", e.kind.equals("REFUND") ? -e.amount : e.amount, Long::sum);
    var states = new TreeMap<String, Long>();
    for (var g : db.all(Garment.class))
      if (access.department(g.departmentId)
          && !db.get(WashOrder.class, g.orderId).state.equals("CANCELLED"))
        states.merge(g.state, 1L, Long::sum);
    return Map.of("orders", orders.size(), "garments", states, "currencies", currencies);
  }

  /** 门店内业务操作历史。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object audit() {
    access.require("audit");
    return db.all(BusinessEvent.class).stream()
        .filter(e -> access.department(e.departmentId))
        .sorted(Comparator.comparing((BusinessEvent e) -> e.id).reversed())
        .limit(500)
        .toList();
  }

  /** CSV逐单资金与衣物概况，防止公式注入。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public String export() {
    access.require("export");
    access.require("reports");
    StringBuilder s =
        new StringBuilder(
            "\uFEFForder,customer,currency,total_minor,paid_minor,state,garments,delivered\r\n");
    for (var o : db.all(WashOrder.class))
      if (access.department(o.departmentId)) {
        var gs = garments(o.id);
        s.append(
                String.join(
                    ",",
                    Rules.csv(o.number),
                    Rules.csv(o.customerName),
                    Rules.csv(o.currency),
                    Rules.csv(o.total),
                    Rules.csv(o.billing.equals("RETAIL") ? paid("ORDER", o.id) : 0),
                    Rules.csv(o.state),
                    Rules.csv(gs.size()),
                    Rules.csv(gs.stream().filter(g -> g.state.equals("DELIVERED")).count())))
            .append("\r\n");
      }
    return s.toString();
  }

  /** 登录、目录和密码操作的门店审计，不记录凭据。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object securityAudit() {
    access.require("audit");
    return db.all(AuditEvent.class).stream()
        .filter(e -> access.department(e.departmentId))
        .sorted(Comparator.comparing((AuditEvent e) -> e.id).reversed())
        .limit(500)
        .toList();
  }
}
