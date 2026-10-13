// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import test from "node:test";
import assert from "node:assert/strict";
import { minor, language, stateName, money } from "./ui.js";
test("money input preserves cents without silent rounding", () => {
  assert.equal(minor("12.01"), 1201);
  assert.equal(minor("0.1"), 10);
  for (const n of ["12.001", "-1", "1e3", "NaN", "1000001"])
    assert.throws(() => minor(n));
});
test("states and money follow selected locale", () => {
  language.value = "en";
  assert.equal(stateName("RACKED"), "Ready for pickup");
  assert.match(money(1299, "USD"), /12.99/);
  language.value = "zh";
  assert.equal(stateName("RACKED"), "可取衣");
});
