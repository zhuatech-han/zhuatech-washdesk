<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, watch } from "vue";
import { X, Plus, Printer } from "@lucide/vue";
import { api } from "../api.js";
import { t, money, minor, stateName, dateTime, ask } from "../ui.js";
const props = defineProps({
  catalog: { type: Object, required: true },
  pending: Boolean,
  message: { type: String, default: "" },
  perform: { type: Function, required: true },
  portal: Boolean,
});
const emit = defineEmits(["error", "orders"]);
const rows = ref([]);
const selected = ref(null);
const dialog = ref("");
const form = ref({});
async function load() {
  try {
    rows.value = await api("/invoices");
  } catch (e) {
    emit("error", e);
  }
}
watch(() => props.portal, load, { immediate: true });
async function open(id) {
  try {
    selected.value = await api("/invoices/" + id);
  } catch (e) {
    emit("error", e);
  }
}
function show(k) {
  dialog.value = k;
  form.value = {
    customerId: "",
    month: new Date().toISOString().slice(0, 7),
    amount: "",
    reference: "",
    method: "BANK",
  };
}
async function canLeave() {
  if (
    dialog.value &&
    !(await ask(t("关闭尚未提交的表单？", "Close the unsubmitted form?")))
  )
    return false;
  dialog.value = "";
  return true;
}
defineExpose({ canLeave });
async function submit() {
  try {
    const issue = dialog.value === "issue";
    const body = issue
      ? { customerId: form.value.customerId, month: form.value.month }
      : {
          version: selected.value.invoice.version,
          kind: "PAYMENT",
          amount: minor(form.value.amount),
          reference: form.value.reference,
          method: form.value.method,
        };
    body.requestId = crypto.randomUUID();
    const r = await props.perform(
      issue ? "/invoices" : "/payments/INVOICE/" + selected.value.invoice.id,
      "POST",
      body,
    );
    if (r) {
      dialog.value = "";
      await load();
      await open(issue ? r.invoice.id : selected.value.invoice.id);
    }
  } catch (e) {
    emit("error", e);
  }
}
function print() {
  window.print();
}
</script>
<template>
  <section>
    <header class="page-heading">
      <div>
        <h1>
          {{
            portal
              ? t("我的月结单", "My statements")
              : t("收款与企业月结", "Payments & statements")
          }}
        </h1>
      </div>
      <div class="actions">
        <button v-if="!portal" :disabled="pending" @click="emit('orders')">
          {{ t("零售收款／退款", "Retail payments / refunds") }}</button
        ><button :disabled="pending" @click="load">
          {{ t("刷新", "Refresh") }}</button
        ><button
          v-if="!portal"
          class="primary"
          :disabled="pending"
          @click="show('issue')"
        >
          <Plus :size="16" />{{ t("生成月结单", "Issue statement") }}
        </button>
      </div>
    </header>
    <div class="panel">
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>{{ t("月结单", "Statement") }}</th>
              <th>{{ t("客户", "Customer") }}</th>
              <th>{{ t("月份", "Month") }}</th>
              <th>{{ t("总额", "Total") }}</th>
              <th>{{ t("已收", "Paid") }}</th>
              <th>{{ t("待收", "Due") }}</th>
              <th>{{ t("状态", "Status") }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in rows" :key="r.invoice.id">
              <td>
                <button class="text-link mono" @click="open(r.invoice.id)">
                  {{ r.invoice.number }}
                </button>
              </td>
              <td>{{ r.invoice.customerName }}</td>
              <td>{{ r.invoice.month }}</td>
              <td>{{ money(r.invoice.total, r.invoice.currency) }}</td>
              <td>{{ money(r.paid, r.invoice.currency) }}</td>
              <td>{{ money(r.balance, r.invoice.currency) }}</td>
              <td>
                <span class="badge">{{
                  r.invoice.state === "OPEN"
                    ? t("待收款", "Unpaid")
                    : stateName(r.invoice.state)
                }}</span>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-if="!rows.length" class="empty">
          {{ t("暂无月结单", "No statements yet") }}
        </div>
      </div>
    </div>
    <div v-if="selected" class="panel invoice-detail">
      <header class="toolbar">
        <h2>
          {{ selected.invoice.number }} · {{ selected.invoice.customerName }}
        </h2>
        <div class="actions">
          <button @click="print">
            <Printer :size="16" />{{
              t("打印月结单", "Print statement")
            }}</button
          ><button
            v-if="!portal && selected.balance > 0"
            class="primary"
            :disabled="pending"
            @click="show('payment')"
          >
            {{ t("核实收款", "Record payment") }}
          </button>
        </div>
      </header>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>{{ t("订单", "Order") }}</th>
              <th>{{ t("取完时间", "Collected at") }}</th>
              <th>{{ t("金额", "Amount") }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="o in selected.orders" :key="o.id">
              <td>{{ o.number }}</td>
              <td>{{ dateTime(o.completedAt) }}</td>
              <td>{{ money(o.total, o.currency) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="history-row">
        <b
          >{{ t("待收", "Balance") }}
          {{ money(selected.balance, selected.invoice.currency) }}</b
        >
        <p>
          {{ t("总额", "Total") }}
          {{ money(selected.invoice.total, selected.invoice.currency) }} ·
          {{ t("已收", "Paid") }}
          {{ money(selected.paid, selected.invoice.currency) }}
        </p>
      </div>
      <div v-for="p in selected.payments" :key="p.id" class="history-row">
        {{ p.reference }} · {{ money(p.amount, p.currency) }} ·
        {{ dateTime(p.createdAt) }}
      </div>
    </div>
    <div v-if="dialog" class="modal-backdrop">
      <section
        class="modal"
        role="dialog"
        aria-modal="true"
        :aria-label="
          dialog === 'issue'
            ? t('生成月结单', 'Issue statement')
            : t('核实收款', 'Record payment')
        "
      >
        <header>
          <h2>
            {{
              dialog === "issue"
                ? t("生成月结单", "Issue statement")
                : t("核实收款", "Record payment")
            }}
          </h2>
          <button
            class="icon-button"
            :aria-label="t('关闭', 'Close')"
            :disabled="pending"
            @click="canLeave"
          >
            <X :size="20" />
          </button>
        </header>
        <p v-if="message" class="form-error" role="alert">{{ message }}</p>
        <form @submit.prevent="submit">
          <template v-if="dialog === 'issue'"
            ><label
              >{{ t("企业客户", "Corporate customer")
              }}<select v-model="form.customerId" required :disabled="pending">
                <option value="">{{ t("选择客户", "Choose customer") }}</option>
                <option
                  v-for="c in catalog.customers.filter(
                    (c) => c.billing === 'MONTHLY',
                  )"
                  :key="c.id"
                  :value="c.id"
                >
                  {{ c.name }} · {{ c.currency }}
                </option>
              </select></label
            ><label
              >{{ t("取完月份", "Collection month")
              }}<input
                v-model="form.month"
                type="month"
                required
                :disabled="pending"
            /></label>
            <p class="hint">
              {{
                t(
                  "包含该客户、本月份已全部取完且尚未开单的订单。已开单订单不会重复纳入。",
                  "Includes this customer’s fully collected, unbilled orders in the selected month; excludes orders already billed.",
                )
              }}
            </p></template
          ><template v-else
            ><p>
              {{ t("待收", "Due") }}
              {{ money(selected.balance, selected.invoice.currency) }}
            </p>
            <label
              >{{ t("金额", "Amount") }} ({{ selected.invoice.currency }})<input
                v-model="form.amount"
                required
                inputmode="decimal"
                pattern="[0-9]+(\.[0-9]{1,2})?"
                :disabled="pending" /></label
            ><label
              >{{ t("凭据编号", "Receipt reference")
              }}<input
                v-model="form.reference"
                required
                maxlength="120"
                :disabled="pending" /></label
            ><label
              >{{ t("核实方式", "Verified method")
              }}<select v-model="form.method" :disabled="pending">
                <option value="CASH">{{ t("现金", "Cash") }}</option>
                <option value="BANK">
                  {{ t("银行转账", "Bank transfer") }}
                </option>
                <option value="VERIFIED_OTHER">
                  {{ t("其他已核实方式", "Other verified method") }}
                </option>
              </select></label
            >
            <p class="hint">
              {{
                t(
                  "请核实真实到账后登记。",
                  "Record only after verifying actual payment.",
                )
              }}
            </p></template
          >
          <footer>
            <button type="button" :disabled="pending" @click="canLeave">
              {{ t("取消", "Cancel") }}</button
            ><button class="primary" :disabled="pending">
              {{ t("确认提交", "Confirm") }}
            </button>
          </footer>
        </form>
      </section>
    </div>
  </section>
</template>
