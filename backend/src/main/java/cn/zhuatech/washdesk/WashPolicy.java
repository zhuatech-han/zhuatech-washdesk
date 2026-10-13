// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.washdesk;

import java.util.*;

/** 衣物状态、最小货币单位与取衣核对纯规则。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
public final class WashPolicy {
  /** 只支持ISO两位小数币种，拒绝静默单位转换。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static String currency(Object input) {
    String c = Rules.text(input, 3, true).toUpperCase(Locale.ROOT);
    try {
      Rules.check(Currency.getInstance(c).getDefaultFractionDigits() == 2, "CURRENCY_INVALID");
    } catch (IllegalArgumentException e) {
      throw new Problem(400, "CURRENCY_INVALID");
    }
    return c;
  }

  /** 正整数最小货币单位，拒绝小数和超限。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static long amount(Object v) {
    return Rules.integer(v, 1, 100000000);
  }

  /** 加工顺序和返洗合法来源，交接后不可逆。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static String transition(String state, String action) {
    return switch (action) {
      case "start" -> {
        Rules.check(state.equals("RECEIVED"), "STATE_INVALID");
        yield "PROCESSING";
      }
      case "ready" -> {
        Rules.check(state.equals("PROCESSING"), "STATE_INVALID");
        yield "READY";
      }
      case "rewash" -> {
        Rules.check(Set.of("PROCESSING", "READY", "RACKED").contains(state), "STATE_INVALID");
        yield "PROCESSING";
      }
      case "rack" -> {
        Rules.check(Set.of("READY", "RACKED").contains(state), "STATE_INVALID");
        yield "RACKED";
      }
      default -> throw new Problem(400, "INVALID_INPUT");
    };
  }

  /** 取衣清单去重，且每件必须处于可取状态。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static void collection(List<Long> ids, List<Garment> garments) {
    Rules.check(!ids.isEmpty() && new HashSet<>(ids).size() == ids.size(), "PICKUP_INVALID");
    for (Long id : ids)
      Rules.check(
          garments.stream().anyMatch(g -> g.id.equals(id) && g.state.equals("RACKED")),
          "PICKUP_INVALID");
  }

  private WashPolicy() {}
}
