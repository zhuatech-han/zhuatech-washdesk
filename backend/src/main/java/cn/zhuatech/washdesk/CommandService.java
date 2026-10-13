// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.washdesk;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** 事务内请求UUID绑定载荷和账号；调用者先持有统一写锁。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
public class CommandService {
  final Store db;
  final AccessService access;
  final ObjectMapper mapper;

  public CommandService(Store db, AccessService access, ObjectMapper mapper) {
    this.db = db;
    this.access = access;
    this.mapper = mapper;
  }

  /** 相同请求可重读结果，不同账号或载荷复用UUID拒绝。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public CommandStamp begin(String action, Map<String, Object> b) {
    String request = Rules.text(b.get("requestId"), 36, true);
    try {
      Rules.check(UUID.fromString(request).toString().equals(request), "REQUEST_ID_INVALID");
    } catch (IllegalArgumentException e) {
      throw new Problem(400, "REQUEST_ID_INVALID");
    }
    String fingerprint;
    try {
      fingerprint =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(
                          (action + "\n" + mapper.writeValueAsString(new TreeMap<>(b)))
                              .getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("Fingerprint unavailable");
    }
    var rows = db.query(CommandStamp.class, "from CommandStamp where requestId=?1", request);
    if (!rows.isEmpty()) {
      var s = rows.getFirst();
      Rules.check(
          s.actorId.equals(access.current().id) && s.fingerprint.equals(fingerprint),
          "REQUEST_REUSED");
      return s;
    }
    var s = new CommandStamp();
    s.requestId = request;
    s.actorId = access.current().id;
    s.fingerprint = fingerprint;
    return s;
  }

  /** 结果身份在同一业务事务提交；事务失败不会保留命令。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void complete(CommandStamp s, String type, Long id) {
    s.resultType = type;
    s.resultId = id;
    db.save(s);
  }
}
