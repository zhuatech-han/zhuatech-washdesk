// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.washdesk;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

/** 独立状态、金额和图像输入边界测试。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
class WashPolicyTest {
  @Test
  void deliveredGarmentNeverReturnsToProcessing() {
    for (var a : List.of("start", "ready", "rack", "rewash"))
      assertThrows(Problem.class, () -> WashPolicy.transition("DELIVERED", a));
  }

  @Test
  void cannotSkipQualityControl() {
    assertThrows(Problem.class, () -> WashPolicy.transition("RECEIVED", "rack"));
    assertEquals("READY", WashPolicy.transition("PROCESSING", "ready"));
  }

  @Test
  void moneyRejectsFractionalCentsAndNegative() {
    for (var n : List.of(-1, 0, 1.5, 100000001))
      assertThrows(Problem.class, () -> WashPolicy.amount(n));
    assertEquals(1299, WashPolicy.amount(1299));
  }

  @Test
  void currencyDoesNotMisrepresentThreeDecimalUnits() {
    assertEquals("USD", WashPolicy.currency("usd"));
    assertThrows(Problem.class, () -> WashPolicy.currency("JPY"));
    assertThrows(Problem.class, () -> WashPolicy.currency("KWD"));
  }

  @Test
  void invalidImageAndSizeRejected() {
    assertThrows(Problem.class, () -> PhotoService.validate("<svg>not a photo</svg>".getBytes()));
    assertThrows(Problem.class, () -> PhotoService.validate(new byte[2097153]));
  }

  @Test
  void csvFormulaAndQuotesSanitized() {
    assertEquals("\"'=1+1\"", Rules.csv("=1+1"));
    assertEquals("\"a\"\"b\"", Rules.csv("a\"b"));
  }

  @Test
  void pickupCannotRepeatTheSameGarment() {
    var g = new Garment();
    g.id = 1L;
    g.state = "RACKED";
    assertThrows(Problem.class, () -> WashPolicy.collection(List.of(1L, 1L), List.of(g)));
    assertThrows(Problem.class, () -> WashPolicy.collection(List.of(), List.of(g)));
  }
}
