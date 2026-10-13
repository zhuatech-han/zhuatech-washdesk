<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, computed, watch } from "vue";
import { Plus, ArrowLeft, X, Search, Printer, Camera } from "@lucide/vue";
import { api } from "../api.js";
import {
  t,
  money,
  minor,
  stateName,
  dateTime,
  ask,
  actionName,
  methodName,
} from "../ui.js";
import BarcodeLabel from "./BarcodeLabel.vue";
const props = defineProps({
  section: { type: String, required: true },
  catalog: { type: Object, required: true },
  profile: { type: Object, required: true },
  pending: Boolean,
  message: { type: String, default: "" },
  perform: { type: Function, required: true },
  refreshCatalog: { type: Function, required: true },
});
const emit = defineEmits(["error"]);
const has = (c) => props.profile.permissions.includes(c);
const list = ref({ rows: [], total: 0, page: 1 });
const q = ref("");
const state = ref("");
const page = ref(1);
const sort = ref("newest");
const selected = ref(null);
const dialog = ref(null);
const form = ref({});
const ready = ref(false);
const codes = ref([]);
const scan = ref("");
const printMode = ref("receipt");
const printPreview = ref(false);
const panel = ref(null);
const title = computed(() =>
  props.section === "portal"
    ? t("我的衣物", "My garments")
    : props.section === "process"
      ? t("加工与挂架", "Processing & racks")
      : t("收衣与取衣", "Orders & pickup"),
);
async function load() {
  try {
    ready.value = false;
    list.value = await api(
      `/orders?q=${encodeURIComponent(q.value)}&state=${state.value}&page=${page.value}&sort=${sort.value}`,
    );
  } catch (e) {
    emit("error", e);
  } finally {
    ready.value = true;
  }
}
watch(
  () => props.section,
  () => {
    selected.value = null;
    page.value = 1;
    q.value = "";
    state.value = "";
    load();
  },
  { immediate: true },
);
async function open(id) {
  try {
    selected.value = await api("/orders/" + id);
    codes.value = [];
  } catch (e) {
    emit("error", e);
  }
}
async function refresh() {
  try {
    await props.refreshCatalog();
  } catch (e) {
    emit("error", e);
    return;
  }
  if (selected.value) await open(selected.value.order.id);
  else await load();
}
async function canLeave() {
  printPreview.value = false;
  if (
    dialog.value &&
    !(await ask(t("关闭尚未提交的表单？", "Close the unsubmitted form?")))
  )
    return false;
  dialog.value = null;
  return true;
}
defineExpose({ canLeave });
function show(kind, g = null) {
  dialog.value = { kind, g };
  form.value = {
    note: "",
    rackId:
      props.catalog.racks.find(
        (x) =>
          x.record.enabled &&
          x.record.departmentId === selected.value?.order.departmentId,
      )?.record.id || "",
    receiver: "",
    amount: "",
    reference: "",
    method: "CASH",
    originalId: "",
  };
  if (kind === "create") {
    form.value = {
      departmentId: props.catalog.departments.find((d) => d.enabled)?.id || "",
      customerId: "",
      dueDate: new Date(Date.now() + 2 * 86400000).toLocaleDateString("en-CA"),
      note: "",
      items: [{ serviceId: "", description: "", conditionNote: "" }],
    };
  }
}
const available = computed(() =>
  props.catalog.services.filter(
    (s) =>
      s.enabled &&
      props.catalog.categories.some(
        (c) => c.code === s.category && c.enabled,
      ) &&
      s.departmentId === form.value.departmentId &&
      s.currency ===
        (props.catalog.customers.find((c) => c.id === form.value.customerId)
          ?.currency || s.currency),
  ),
);
watch(
  () => form.value.departmentId,
  () => {
    if (dialog.value?.kind === "create") {
      form.value.customerId = "";
      form.value.items.forEach((i) => (i.serviceId = ""));
    }
  },
);
watch(
  () => form.value.customerId,
  () => {
    if (dialog.value?.kind === "create")
      form.value.items.forEach((i) => (i.serviceId = ""));
  },
);
const total = computed(
  () =>
    form.value.items?.reduce(
      (n, i) =>
        n +
        (props.catalog.services.find((s) => s.id === i.serviceId)?.price || 0),
      0,
    ) || 0,
);
/** 身份由后台产生；前端只提交已核对的物品和事务UUID。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function submit() {
  try {
    const k = dialog.value.kind;
    let path, body;
    if (k === "create") {
      path = "/orders";
      body = {
        ...form.value,
        customerVersion: props.catalog.customers.find(
          (c) => c.id === form.value.customerId,
        )?.version,
        items: form.value.items.map((i) => ({
          ...i,
          serviceVersion: props.catalog.services.find(
            (s) => s.id === i.serviceId,
          )?.version,
        })),
      };
    } else if (k === "payment" || k === "refund") {
      path = "/payments/ORDER/" + selected.value.order.id;
      body = {
        version: selected.value.order.version,
        kind: k === "payment" ? "PAYMENT" : "REFUND",
        amount: minor(form.value.amount),
        reference: form.value.reference,
        method: form.value.method,
        ...(k === "refund" ? { originalId: form.value.originalId } : {}),
      };
    } else if (k === "pickup") {
      path = "/orders/" + selected.value.order.id + "/pickup";
      body = {
        version: selected.value.order.version,
        barcodes: codes.value,
        receiver: form.value.receiver,
        note: form.value.note,
      };
    } else if (k === "cancel") {
      path = "/orders/" + selected.value.order.id + "/cancel";
      body = { version: selected.value.order.version, note: form.value.note };
    } else {
      path = "/garments/" + dialog.value.g.id + "/" + k;
      body = {
        version: dialog.value.g.version,
        note: form.value.note,
        ...(k === "rack" ? { rackId: form.value.rackId } : {}),
      };
    }
    body.requestId = crypto.randomUUID();
    const r = await props.perform(path, "POST", body);
    if (r) {
      dialog.value = null;
      if (k === "create") await open(r.order.id);
      else await refresh();
    }
  } catch (e) {
    emit("error", e);
  }
}
async function action(g, k) {
  if (k === "start" || k === "ready") {
    const r = await props.perform("/garments/" + g.id + "/" + k, "POST", {
      version: g.version,
      note: "",
      requestId: crypto.randomUUID(),
    });
    if (r) selected.value = r;
  } else show(k, g);
}
function addCode() {
  const code = scan.value.trim().toUpperCase();
  if (
    !selected.value.garments.some(
      (g) => g.barcode === code && g.state === "RACKED",
    ) ||
    codes.value.includes(code)
  ) {
    emit("error", new Error("PICKUP_INVALID"));
    return;
  }
  codes.value.push(code);
  scan.value = "";
}
async function upload(g, event) {
  const file = event.target.files?.[0];
  event.target.value = "";
  if (!file) return;
  const body = new FormData();
  body.append("file", file);
  const r = await props.perform(
    `/garments/${g.id}/photos?version=${g.version}`,
    "POST",
    body,
  );
  if (r) await refresh();
}
function sendPrint() {
  window.print();
}
function print(mode) {
  printMode.value = mode;
  printPreview.value = true;
}
const rackName = (id) =>
  props.catalog.racks.find((x) => x.record.id === id)?.record.code ||
  selected.value?.locations?.find((x) => x.id === id)?.code ||
  "—";
</script>
<template>
  <section ref="panel">
    <header class="page-heading">
      <div>
        <button
          v-if="selected"
          class="back-link"
          :disabled="pending"
          @click="
            selected = null;
            load();
          "
        >
          <ArrowLeft :size="15" />{{ title }}
        </button>
        <h1>{{ selected ? selected.order.number : title }}</h1>
      </div>
      <div class="actions">
        <button :disabled="pending" @click="refresh">
          {{ t("刷新", "Refresh") }}</button
        ><button
          v-if="!selected && has('orders')"
          class="primary"
          :disabled="pending"
          @click="show('create')"
        >
          <Plus :size="16" />{{ t("收衣开单", "Receive garments") }}</button
        ><button v-if="selected" :disabled="pending" @click="print('receipt')">
          <Printer :size="16" />{{ t("打印收据", "Print receipt") }}</button
        ><button
          v-if="selected && has('orders')"
          :disabled="pending"
          @click="print('labels')"
        >
          {{ t("衣物标签", "Garment labels") }}
        </button>
      </div>
    </header>
    <template v-if="!selected"
      ><div class="panel">
        <form
          class="toolbar"
          @submit.prevent="
            page = 1;
            load();
          "
        >
          <label class="search-box"
            ><Search :size="17" /><input
              v-model="q"
              :aria-label="
                t('订单、客户或衣物编号', 'Order, customer or garment code')
              "
              :placeholder="
                t('订单、客户或扫码查衣', 'Order, customer or scan garment')
              "
              maxlength="120" /></label
          ><select v-model="state" :aria-label="t('订单状态', 'Order status')">
            <option value="">{{ t("所有状态", "All states") }}</option>
            <option
              v-for="s in ['OPEN', 'COMPLETED', 'CANCELLED']"
              :key="s"
              :value="s"
            >
              {{ stateName(s) }}
            </option></select
          ><select v-model="sort" :aria-label="t('排序', 'Sort')">
            <option value="newest">{{ t("最新开单", "Newest first") }}</option>
            <option value="oldest">{{ t("最早开单", "Oldest first") }}</option>
            <option value="due">
              {{ t("取衣日期", "Pickup date") }}
            </option></select
          ><button :disabled="pending">{{ t("查询", "Search") }}</button>
        </form>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>{{ t("订单", "Order") }}</th>
                <th>{{ t("客户", "Customer") }}</th>
                <th>{{ t("取衣日期", "Pickup date") }}</th>
                <th>{{ t("衣物进度", "Garments") }}</th>
                <th>{{ t("总额", "Total") }}</th>
                <th>{{ t("结算", "Billing") }}</th>
                <th>{{ t("状态", "Status") }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="r in list.rows" :key="r.order.id">
                <td>
                  <button class="text-link mono" @click="open(r.order.id)">
                    {{ r.order.number }}</button
                  ><small>{{ dateTime(r.order.createdAt) }}</small>
                </td>
                <td>{{ r.order.customerName }}</td>
                <td>{{ r.order.dueDate }}</td>
                <td>
                  <b>{{ r.delivered }} / {{ r.items }}</b>
                  {{ t("已取", "collected")
                  }}<small>{{ r.racked }} {{ t("件可取", "ready") }}</small>
                </td>
                <td class="number">
                  {{ money(r.order.total, r.order.currency) }}
                </td>
                <td>
                  {{ stateName(r.order.billing)
                  }}<small v-if="r.order.billing === 'RETAIL'"
                    >{{ t("已收", "Paid") }}
                    {{ money(r.paid, r.order.currency) }}</small
                  >
                </td>
                <td>
                  <span
                    class="badge"
                    :class="{ gray: r.order.state === 'CANCELLED' }"
                    >{{ stateName(r.order.state) }}</span
                  >
                </td>
              </tr>
            </tbody>
          </table>
          <div v-if="!ready" class="empty">{{ t("读取中…", "Loading…") }}</div>
          <div v-else-if="!list.rows.length" class="empty">
            {{ t("没有匹配订单", "No matching orders") }}
          </div>
        </div>
        <footer class="pagination">
          <span
            >{{ list.total }} {{ t("单", "orders") }} · {{ page }} /
            {{ Math.max(1, Math.ceil(list.total / 20)) }}</span
          ><button
            :disabled="page <= 1"
            @click="
              page--;
              load();
            "
          >
            {{ t("上一页", "Previous") }}</button
          ><button
            :disabled="page * 20 >= list.total"
            @click="
              page++;
              load();
            "
          >
            {{ t("下一页", "Next") }}
          </button>
        </footer>
      </div></template
    >
    <template v-else
      ><div class="order-summary">
        <div>
          <small>{{ t("客户", "Customer") }}</small
          ><b>{{ selected.order.customerName }}</b>
        </div>
        <div>
          <small>{{ t("取衣日期", "Pickup date") }}</small
          ><b>{{ selected.order.dueDate }}</b>
        </div>
        <div>
          <small>{{ t("衣物进度", "Garments") }}</small
          ><b
            >{{ selected.delivered }} / {{ selected.items }}
            {{ t("已取", "collected") }}</b
          >
        </div>
        <div>
          <small>{{ t("总额", "Total") }}</small
          ><b>{{ money(selected.order.total, selected.order.currency) }}</b>
        </div>
        <div>
          <small>{{ stateName(selected.order.billing) }}</small
          ><b>{{
            selected.order.billing === "RETAIL"
              ? t("欠款", "Due") +
                " " +
                money(
                  selected.order.state === "CANCELLED" ? 0 : selected.balance,
                  selected.order.currency,
                )
              : selected.order.invoiceId
                ? t("已开月结单", "Statement issued")
                : t("待月结", "Pending statement")
          }}</b>
        </div>
      </div>
      <div v-if="selected.order.note" class="order-note">
        {{ selected.order.note }}
      </div>
      <div class="panel">
        <div class="toolbar">
          <h2>{{ t("逐件衣物", "Garments") }}</h2>
          <span class="badge">{{ stateName(selected.order.state) }}</span>
          <div class="actions">
            <button
              v-if="
                has('finance') &&
                selected.order.billing === 'RETAIL' &&
                selected.order.state !== 'CANCELLED' &&
                selected.balance > 0
              "
              class="primary"
              :disabled="pending"
              @click="show('payment')"
            >
              {{ t("核实收款", "Record payment") }}</button
            ><button
              v-if="has('pickup') && selected.order.state === 'OPEN'"
              class="primary"
              :disabled="pending || !selected.racked"
              @click="
                codes = [];
                show('pickup');
              "
            >
              {{ t("取衣交接", "Collect garments") }}</button
            ><button
              v-if="
                has('orders') &&
                selected.order.state === 'OPEN' &&
                selected.garments.every((g) => g.state === 'RECEIVED')
              "
              :disabled="pending"
              @click="show('cancel')"
            >
              {{ t("取消订单", "Cancel order") }}</button
            ><button
              v-if="
                has('finance') &&
                selected.order.state === 'CANCELLED' &&
                selected.paid > 0
              "
              :disabled="pending"
              @click="show('refund')"
            >
              {{ t("原款退款", "Refund payment") }}
            </button>
          </div>
        </div>
        <div class="table-wrap">
          <table class="garment-table">
            <thead>
              <tr>
                <th>{{ t("衣物／编号", "Garment / code") }}</th>
                <th>{{ t("服务／外观", "Service / condition") }}</th>
                <th>{{ t("价格", "Price") }}</th>
                <th>{{ t("状态／位置", "Status / rack") }}</th>
                <th>{{ t("操作", "Actions") }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="g in selected.garments" :key="g.id">
                <td>
                  <b>{{ g.description }}</b
                  ><small class="mono wrap">{{ g.barcode }}</small>
                  <div class="photo-row">
                    <a
                      v-for="p in selected.photos.filter(
                        (p) => p.garmentId === g.id,
                      )"
                      :key="p.id"
                      :href="'/api/photos/' + p.id"
                      target="_blank"
                      rel="noopener"
                      ><img
                        :src="'/api/photos/' + p.id"
                        :alt="
                          g.description + ' ' + t('收衣照片', 'Intake photo')
                        "
                    /></a>
                  </div>
                </td>
                <td>
                  {{ t(g.serviceName, g.serviceNameEn)
                  }}<small class="wrap">{{ g.conditionNote }}</small
                  ><small v-if="g.rewashCount"
                    >{{ t("返洗次数", "Rewash count") }}
                    {{ g.rewashCount }}</small
                  >
                </td>
                <td class="number">
                  {{ money(g.price, selected.order.currency) }}
                </td>
                <td>
                  <span
                    class="badge"
                    :class="{ gray: g.state === 'DELIVERED' }"
                    >{{ stateName(g.state) }}</span
                  ><small v-if="g.rackId"
                    >{{ t("挂架", "Rack") }} {{ rackName(g.rackId) }}</small
                  ><small v-if="g.deliveredAt">{{
                    dateTime(g.deliveredAt)
                  }}</small>
                </td>
                <td>
                  <div class="row-actions">
                    <template
                      v-if="has('process') && selected.order.state === 'OPEN'"
                      ><button
                        v-if="g.state === 'RECEIVED'"
                        :disabled="pending"
                        @click="action(g, 'start')"
                      >
                        {{ t("开始加工", "Start") }}</button
                      ><button
                        v-if="g.state === 'PROCESSING'"
                        :disabled="pending"
                        @click="action(g, 'ready')"
                      >
                        {{ t("质检通过", "Pass QC") }}</button
                      ><button
                        v-if="['READY', 'RACKED'].includes(g.state)"
                        :disabled="pending"
                        @click="action(g, 'rack')"
                      >
                        {{
                          g.state === "READY"
                            ? t("挂架", "Rack")
                            : t("换架", "Move rack")
                        }}</button
                      ><button
                        v-if="
                          ['PROCESSING', 'READY', 'RACKED'].includes(g.state)
                        "
                        :disabled="pending"
                        @click="action(g, 'rewash')"
                      >
                        {{ t("返洗", "Rewash") }}
                      </button></template
                    ><label
                      v-if="
                        has('orders') &&
                        g.state === 'RECEIVED' &&
                        selected.order.state === 'OPEN'
                      "
                      class="upload-button"
                      ><Camera :size="15" />{{ t("照片", "Photo")
                      }}<input
                        type="file"
                        accept="image/jpeg,image/png"
                        :disabled="pending"
                        :aria-label="
                          t('上传外观照片', 'Upload intake photo') +
                          ' ' +
                          g.description
                        "
                        @change="upload(g, $event)"
                    /></label>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
      <div class="history-grid">
        <div class="panel">
          <h2 class="panel-title">{{ t("交接记录", "Pickup history") }}</h2>
          <div v-for="p in selected.pickups" :key="p.id" class="history-row">
            <b>{{ p.receiver }}</b
            ><span
              >{{
                selected.pickupItems.filter((i) => i.pickupId === p.id).length
              }}
              {{ t("件", "garments") }} · {{ p.actor }}</span
            ><small>{{ dateTime(p.createdAt) }}</small
            ><small>{{ p.note }}</small>
          </div>
          <div v-if="!selected.pickups.length" class="empty">
            {{ t("尚未取衣", "No pickup yet") }}
          </div>
        </div>
        <div class="panel">
          <h2 class="panel-title">
            {{ t("收退款记录", "Payments & refunds") }}
          </h2>
          <div v-for="p in selected.payments" :key="p.id" class="history-row">
            <b
              >{{ p.kind === "REFUND" ? "-" : ""
              }}{{ money(p.amount, p.currency) }}</b
            ><span>{{ p.reference }} · {{ methodName(p.method) }}</span
            ><small>{{ dateTime(p.createdAt) }}</small>
          </div>
          <div v-if="!selected.payments.length" class="empty">
            {{
              selected.order.billing === "MONTHLY"
                ? t(
                    "企业款项在月结单查看",
                    "Corporate payments appear on statements",
                  )
                : t("尚未收款", "No payment yet")
            }}
          </div>
        </div>
      </div>
      <details class="panel">
        <summary>
          {{ t("加工与操作轨迹", "Processing & activity history") }}
        </summary>
        <div v-for="e in selected.events" :key="e.id" class="history-row">
          <b>{{ actionName(e.action) }} · {{ e.actor }}</b
          ><small>{{ dateTime(e.createdAt) }} · {{ e.note }}</small>
        </div>
      </details>
      <div class="print-only">
        <template v-if="printMode === 'receipt'"
          ><h1>{{ t("收衣／交接收据", "Laundry receipt") }}</h1>
          <p>{{ selected.order.number }} · {{ selected.order.customerName }}</p>
          <p>{{ t("预计取衣", "Pickup date") }} {{ selected.order.dueDate }}</p>
          <table>
            <tbody>
              <tr v-for="g in selected.garments" :key="g.id">
                <td>
                  {{ g.description }}<small>{{ g.barcode }}</small>
                </td>
                <td>{{ stateName(g.state) }}</td>
                <td>{{ money(g.price, selected.order.currency) }}</td>
              </tr>
            </tbody>
          </table>
          <p>
            {{ t("总额", "Total") }}
            {{ money(selected.order.total, selected.order.currency) }} ·
            {{ stateName(selected.order.billing) }}
          </p>
          <p v-if="selected.order.billing === 'RETAIL'">
            {{ t("已核实收款", "Verified payments") }}
            {{ money(selected.paid, selected.order.currency) }}
          </p></template
        ><template v-else
          ><div
            v-for="g in selected.garments"
            :key="g.id"
            class="garment-label"
          >
            <b>{{ g.description }}</b>
            <p>{{ selected.order.number }} · {{ selected.order.dueDate }}</p>
            <BarcodeLabel :code="g.barcode" /></div
        ></template>
      </div>
    </template>
    <div v-if="printPreview && selected" class="modal-backdrop">
      <section
        class="modal wide"
        role="dialog"
        aria-modal="true"
        :aria-label="t('打印预览', 'Print preview')"
      >
        <header>
          <h2>
            {{
              printMode === "labels"
                ? t("衣物标签预览", "Garment labels")
                : t("收衣／交接收据", "Laundry receipt")
            }}
          </h2>
          <button
            class="icon-button"
            :aria-label="t('关闭', 'Close')"
            @click="printPreview = false"
          >
            <X :size="20" />
          </button>
        </header>
        <template v-if="printMode === 'labels'"
          ><div
            v-for="g in selected.garments"
            :key="g.id"
            class="garment-label"
          >
            <b>{{ g.description }}</b>
            <p>{{ selected.order.number }} · {{ selected.order.dueDate }}</p>
            <BarcodeLabel :code="g.barcode" /></div></template
        ><template v-else
          ><p>
            {{ selected.order.number }} · {{ selected.order.customerName }}
          </p>
          <table>
            <tbody>
              <tr v-for="g in selected.garments" :key="g.id">
                <td>
                  {{ g.description }}<small>{{ g.barcode }}</small>
                </td>
                <td>{{ stateName(g.state) }}</td>
                <td>{{ money(g.price, selected.order.currency) }}</td>
              </tr>
            </tbody>
          </table>
          <p>
            {{ t("总额", "Total") }}
            {{ money(selected.order.total, selected.order.currency) }} ·
            {{ stateName(selected.order.billing) }}
          </p>
          <p v-if="selected.order.billing === 'RETAIL'">
            {{ t("已核实收款", "Verified payments") }}
            {{ money(selected.paid, selected.order.currency) }}
          </p></template
        >
        <footer>
          <button @click="printPreview = false">{{ t("关闭", "Close") }}</button
          ><button class="primary" @click="sendPrint">
            {{ t("打印", "Print") }}
          </button>
        </footer>
      </section>
    </div>
    <div v-if="dialog" class="modal-backdrop">
      <section
        class="modal"
        :class="{ wide: dialog.kind === 'create' }"
        role="dialog"
        aria-modal="true"
        :aria-label="
          dialog.kind === 'create'
            ? t('收衣开单', 'Receive garments')
            : t('订单操作', 'Order action')
        "
      >
        <header>
          <h2>
            {{
              {
                create: t("收衣开单", "Receive garments"),
                payment: t("核实收款", "Record payment"),
                refund: t("按原款退款", "Refund payment"),
                pickup: t("取衣交接", "Collect garments"),
                cancel: t("取消订单", "Cancel order"),
                rack: t("挂架位置", "Rack location"),
                rewash: t("返洗", "Rewash"),
              }[dialog.kind]
            }}
          </h2>
          <button
            class="icon-button"
            :disabled="pending"
            :aria-label="t('关闭', 'Close')"
            @click="canLeave"
          >
            <X :size="20" />
          </button>
        </header>
        <p v-if="message" class="form-error" role="alert">{{ message }}</p>
        <form @submit.prevent="submit">
          <template v-if="dialog.kind === 'create'"
            ><div class="form-grid">
              <label
                >{{ t("门店", "Store")
                }}<select
                  v-model="form.departmentId"
                  required
                  :disabled="pending"
                >
                  <option
                    v-for="d in catalog.departments.filter((d) => d.enabled)"
                    :key="d.id"
                    :value="d.id"
                  >
                    {{ d.name }}
                  </option>
                </select></label
              ><label
                >{{ t("客户", "Customer")
                }}<select
                  v-model="form.customerId"
                  required
                  :disabled="pending"
                >
                  <option value="">
                    {{ t("选择客户", "Choose customer") }}
                  </option>
                  <option
                    v-for="c in catalog.customers.filter(
                      (c) => c.enabled && c.departmentId === form.departmentId,
                    )"
                    :key="c.id"
                    :value="c.id"
                  >
                    {{ c.name }} · {{ c.contact || "#" + c.id }} ·
                    {{ stateName(c.billing) }} · {{ c.currency }}
                  </option>
                </select></label
              ><label
                >{{ t("预计取衣日期", "Pickup date")
                }}<input
                  v-model="form.dueDate"
                  type="date"
                  required
                  :disabled="pending"
              /></label>
            </div>
            <div
              v-for="(i, index) in form.items"
              :key="index"
              class="intake-line"
            >
              <b>{{ t("衣物", "Garment") }} {{ index + 1 }}</b>
              <div class="form-grid">
                <label
                  >{{ t("衣物描述", "Description")
                  }}<input
                    v-model="i.description"
                    required
                    maxlength="200"
                    :disabled="pending" /></label
                ><label
                  >{{ t("服务", "Service")
                  }}<select v-model="i.serviceId" required :disabled="pending">
                    <option value="">
                      {{ t("选择服务", "Choose service") }}
                    </option>
                    <option v-for="s in available" :key="s.id" :value="s.id">
                      {{ t(s.name, s.nameEn) }} ·
                      {{ money(s.price, s.currency) }}
                    </option>
                  </select></label
                >
              </div>
              <label
                >{{
                  t(
                    "外观／缺陷／顾客确认",
                    "Condition / defects / acknowledgement",
                  )
                }}<input
                  v-model="i.conditionNote"
                  required
                  maxlength="1000"
                  :disabled="pending" /></label
              ><button
                v-if="form.items.length > 1"
                type="button"
                :disabled="pending"
                @click="form.items.splice(index, 1)"
              >
                {{ t("移除此件", "Remove garment") }}
              </button>
            </div>
            <button
              type="button"
              :disabled="pending || form.items.length >= 100"
              @click="
                form.items.push({
                  serviceId: '',
                  description: '',
                  conditionNote: '',
                })
              "
            >
              <Plus :size="16" />{{ t("添加一件", "Add garment") }}
            </button>
            <p class="total">
              {{ t("合计", "Total") }}
              {{
                money(
                  total,
                  catalog.customers.find((c) => c.id === form.customerId)
                    ?.currency || "CNY",
                )
              }}
            </p>
            <label
              >{{ t("备注", "Note")
              }}<textarea
                v-model="form.note"
                maxlength="2000"
                :disabled="pending"
              /></label
          ></template>
          <template v-else-if="['payment', 'refund'].includes(dialog.kind)"
            ><p v-if="dialog.kind === 'payment'">
              {{ t("待收", "Due") }}
              {{ money(selected.balance, selected.order.currency) }}
            </p>
            <label v-if="dialog.kind === 'refund'"
              >{{ t("原收款", "Original payment")
              }}<select v-model="form.originalId" required :disabled="pending">
                <option value="">{{ t("选择原款", "Choose original") }}</option>
                <option
                  v-for="p in selected.payments.filter(
                    (p) => p.kind === 'PAYMENT',
                  )"
                  :key="p.id"
                  :value="p.id"
                >
                  {{ p.reference }} · {{ money(p.amount, p.currency) }}
                </option>
              </select></label
            ><label
              >{{ t("金额", "Amount") }} ({{ selected.order.currency }})<input
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
                  "核对实际收退款后再登记，不会自动扣款。",
                  "Record only after verifying the actual transfer; no automatic charge.",
                )
              }}
            </p></template
          >
          <template v-else-if="dialog.kind === 'pickup'"
            ><div class="scan-row">
              <label
                >{{
                  t(
                    "扫描／输入完整衣物编号",
                    "Scan / enter complete garment code",
                  )
                }}<input
                  v-model="scan"
                  autocomplete="off"
                  :disabled="pending"
                  @keydown.enter.prevent="addCode" /></label
              ><button type="button" :disabled="pending" @click="addCode">
                {{ t("核对", "Verify") }}
              </button>
            </div>
            <div v-for="code in codes" :key="code" class="verified-code">
              <span
                >{{
                  selected.garments.find((g) => g.barcode === code)
                    ?.description
                }}<small class="mono">{{ code }}</small></span
              ><button
                type="button"
                :disabled="pending"
                @click="codes = codes.filter((c) => c !== code)"
              >
                {{ t("移除", "Remove") }}
              </button>
            </div>
            <p class="hint">
              {{ codes.length }}
              {{
                t(
                  "件已核对。仅交接已挂架衣物。",
                  "verified garments. Only racked garments can be collected.",
                )
              }}
            </p>
            <label
              >{{ t("实际接收人", "Actual recipient")
              }}<input
                v-model="form.receiver"
                required
                maxlength="120"
                :disabled="pending" /></label
            ><label
              >{{ t("交接备注", "Handover note")
              }}<textarea
                v-model="form.note"
                maxlength="1000"
                :disabled="pending"
              /></label
          ></template>
          <template v-else
            ><p v-if="dialog.g">
              {{ dialog.g.description }} · {{ stateName(dialog.g.state) }}
            </p>
            <label v-if="dialog.kind === 'rack'"
              >{{ t("挂架", "Rack")
              }}<select v-model="form.rackId" required :disabled="pending">
                <option
                  v-for="r in catalog.racks.filter(
                    (r) =>
                      r.record.enabled &&
                      r.record.departmentId === selected.order.departmentId,
                  )"
                  :key="r.record.id"
                  :value="r.record.id"
                >
                  {{ r.record.code }} · {{ r.record.name }} ({{ r.occupied }}/{{
                    r.record.capacity
                  }})
                </option>
              </select></label
            ><label
              >{{
                dialog.kind === "rack"
                  ? t("备注", "Note")
                  : t("原因（必填）", "Reason (required)")
              }}<textarea
                v-model="form.note"
                :required="dialog.kind !== 'rack'"
                maxlength="1000"
                :disabled="pending"
              /></label
          ></template>
          <footer>
            <button type="button" :disabled="pending" @click="canLeave">
              {{ t("取消", "Cancel") }}</button
            ><button
              class="primary"
              :disabled="pending || (dialog.kind === 'pickup' && !codes.length)"
            >
              {{
                pending ? t("提交中…", "Submitting…") : t("确认提交", "Confirm")
              }}
            </button>
          </footer>
        </form>
      </section>
    </div>
  </section>
</template>
