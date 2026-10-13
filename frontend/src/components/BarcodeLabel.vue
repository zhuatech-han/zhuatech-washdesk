<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, onMounted, watch } from "vue";
import JsBarcode from "jsbarcode";
const props = defineProps({ code: { type: String, required: true } });
const el = ref(null);
/** 渲染本单真实Code128标签，扫码设备兼容性需部署者验证。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
function render() {
  if (el.value)
    JsBarcode(el.value, props.code, {
      format: "CODE128",
      width: 1.5,
      height: 36,
      fontSize: 10,
      margin: 10,
    });
}
onMounted(render);
watch(() => props.code, render);
</script>
<template><svg ref="el" :aria-label="code" role="img"></svg></template>
