// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.washdesk;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 空库建立权限、目录和部署者管理员，不插入虚构订单。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Component
public class Bootstrap implements ApplicationRunner {
  public static final Set<String> CODES =
      Set.of(
          "catalog",
          "masters",
          "customers",
          "orders",
          "process",
          "pickup",
          "finance",
          "portal",
          "reports",
          "export",
          "users",
          "roles",
          "settings",
          "audit");
  final Store db;
  final BCryptPasswordEncoder encoder;
  final String password;
  final String username;

  public Bootstrap(
      Store db,
      BCryptPasswordEncoder encoder,
      @Value("${washdesk.admin-password}") String password,
      @Value("${washdesk.admin-username}") String username) {
    this.db = db;
    this.encoder = encoder;
    this.password = password;
    this.username = username;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!db.all(Account.class).isEmpty()) return;
    AdminService.validatePassword(password);
    Rules.check(username.matches("[a-z0-9_.-]{3,60}"), "INVALID_INPUT");
    var d = new Department();
    d.name = "洗衣门店";
    d.zone = "Asia/Shanghai";
    db.save(d);
    String[][] names = {
      {"catalog", "业务目录"},
      {"masters", "价目与挂架"},
      {"customers", "客户档案"},
      {"orders", "收衣订单"},
      {"process", "加工质检"},
      {"pickup", "分批取衣"},
      {"finance", "核实收退款与月结"},
      {"portal", "本人查单"},
      {"reports", "经营报表"},
      {"export", "数据导出"},
      {"users", "登录账号"},
      {"roles", "角色权限"},
      {"settings", "门店设置"},
      {"audit", "操作审计"}
    };
    for (var n : names) {
      var p = new Permission();
      p.code = n[0];
      p.name = n[1];
      db.save(p);
    }
    role("管理员", "ALL", CODES);
    role(
        "前台",
        "DEPARTMENT",
        Set.of(
            "catalog", "customers", "orders", "pickup", "finance", "reports", "export", "audit"));
    role("加工质检", "DEPARTMENT", Set.of("catalog", "process"));
    role("财务", "DEPARTMENT", Set.of("catalog", "finance", "reports", "export"));
    role("客户", "CUSTOMER", Set.of("portal"));
    var a = new Account();
    a.username = username;
    a.displayName = "管理员";
    a.passwordHash = encoder.encode(password);
    a.departmentId = d.id;
    a.roleId = db.all(AccessRole.class).getFirst().id;
    db.save(a);
    String[][] menus = {
      {"orders", "收衣与取衣", "Orders & pickup", "orders"},
      {"process", "加工与挂架", "Processing & racks", "process"},
      {"portal", "我的衣物", "My garments", "portal"},
      {"customers", "客户", "Customers", "customers"},
      {"masters", "价目与挂架", "Services & racks", "masters"},
      {"finance", "收款与企业月结", "Payments & statements", "finance"},
      {"reports", "经营报表", "Reports", "reports"},
      {"audit", "操作记录", "Activity", "audit"},
      {"users", "登录账号", "Accounts", "users"},
      {"roles", "角色权限", "Roles", "roles"},
      {"settings", "门店与设置", "Stores & settings", "settings"}
    };
    for (int i = 0; i < menus.length; i++) {
      var m = new NavMenu();
      m.code = menus[i][0];
      m.name = menus[i][1];
      m.nameEn = menus[i][2];
      m.permissionCode = menus[i][3];
      m.position = i;
      m.enabled = true;
      db.save(m);
    }
    for (var kv : Map.of("photoLimit", "3", "orderItemLimit", "50").entrySet()) {
      var s = new SystemSetting();
      s.code = kv.getKey();
      s.value = kv.getValue();
      db.save(s);
    }
    String[][] cats = {
      {"LAUNDRY", "水洗", "Laundry"}, {"DRYCLEAN", "干洗", "Dry cleaning"}, {"CARE", "护理", "Care"}
    };
    for (var c : cats) {
      var x = new DictionaryEntry();
      x.type = "CATEGORY";
      x.code = c[0];
      x.name = c[1];
      x.nameEn = c[2];
      x.enabled = true;
      db.save(x);
    }
  }

  void role(String name, String scope, Set<String> p) {
    var r = new AccessRole();
    r.name = name;
    r.scope = scope;
    r.permissions.addAll(p);
    db.save(r);
  }
}
