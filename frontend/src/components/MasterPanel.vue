<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, computed, watch } from "vue";
import { Plus, Pencil, X, Search } from "@lucide/vue";
import { t, money, minor, cloneRecord, ask, stateName } from "../ui.js";
const props = defineProps({
  section: { type: String, required: true },
  catalog: { type: Object, required: true },
  pending: Boolean,
  message: { type: String, default: "" },
  perform: { type: Function, required: true },
  refreshCatalog: { type: Function, required: true },
});
const emit = defineEmits(["error"]);
const kind = ref(props.section === "customers" ? "customers" : "services");
const q = ref("");
const editing = ref(null);
const initial = ref("");
const page = ref(1);
watch(
  () => props.section,
  () => {
    kind.value = props.section === "customers" ? "customers" : "services";
    editing.value = null;
  },
);
const names = {
  customers: () => t("客户", "Customers"),
  services: () => t("洗护服务", "Services"),
  racks: () => t("挂架", "Racks"),
};
const rows = computed(() =>
  (props.catalog[kind.value] || [])
    .map((x) => x.record || x)
    .filter((x) =>
      JSON.stringify(x).toLowerCase().includes(q.value.toLowerCase()),
    ),
);
const visible = computed(() =>
  rows.value.slice((page.value - 1) * 15, page.value * 15),
);
watch([q, kind], () => (page.value = 1));
async function refresh() {
  try {
    await props.refreshCatalog();
  } catch (e) {
    emit("error", e);
  }
}
async function canLeave() {
  if (
    editing.value &&
    JSON.stringify(editing.value) !== initial.value &&
    !(await ask(t("放弃未保存内容？", "Discard unsaved changes?")))
  )
    return false;
  editing.value = null;
  return true;
}
defineExpose({ canLeave });
async function change(k) {
  if (await canLeave()) kind.value = k;
}
function edit(r) {
  editing.value = r
    ? cloneRecord(r)
    : {
        name: "",
        nameEn: "",
        departmentId:
          props.catalog.departments.find((d) => d.enabled)?.id || "",
        enabled: true,
        currency: "CNY",
        billing: "RETAIL",
        accountId: "",
        creditLimit: 0,
        price: 0,
        capacity: 20,
        code: "",
        category: props.catalog.categories.find((c) => c.enabled)?.code || "",
        contact: "",
      };
  if (kind.value === "customers")
    editing.value.amount = String(editing.value.creditLimit / 100);
  if (kind.value === "services")
    editing.value.amount = String(editing.value.price / 100);
  initial.value = JSON.stringify(editing.value);
}
async function save() {
  try {
    const b = { ...editing.value };
    if (kind.value === "customers") b.creditLimit = minor(b.amount);
    if (kind.value === "services") b.price = minor(b.amount);
    delete b.amount;
    const r = await props.perform(
      `/masters/${kind.value}${b.id ? "/" + b.id : ""}`,
      b.id ? "PUT" : "POST",
      b,
    );
    if (r) editing.value = null;
  } catch (e) {
    emit("error", e);
  }
}
const dept = (id) =>
  props.catalog.departments.find((d) => d.id === id)?.name || "#" + id;
</script>
<template>
  <section>
    <header class="page-heading">
      <div>
        <h1>
          {{
            section === "customers"
              ? t("客户档案", "Customers")
              : t("价目与挂架", "Services & racks")
          }}
        </h1>
      </div>
      <div class="actions">
        <button :disabled="pending" @click="refresh">
          {{ t("刷新", "Refresh") }}</button
        ><button class="primary" :disabled="pending" @click="edit(null)">
          <Plus :size="16" />{{ t("新增", "Add") }} {{ names[kind]() }}
        </button>
      </div>
    </header>
    <nav v-if="section !== 'customers'" class="sub-tabs">
      <button
        v-for="k in ['services', 'racks']"
        :key="k"
        :class="{ active: kind === k }"
        @click="change(k)"
      >
        {{ names[k]() }}
      </button>
    </nav>
    <div class="panel">
      <div class="toolbar">
        <label class="search-box"
          ><Search :size="16" /><input
            v-model="q"
            :aria-label="t('搜索', 'Search')"
            :placeholder="t('搜索', 'Search')" /></label
        ><span class="muted"
          >{{ rows.length }} {{ t("条记录", "records") }}</span
        >
      </div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>{{ t("名称", "Name") }}</th>
              <th>{{ t("门店", "Store") }}</th>
              <template v-if="kind === 'customers'"
                ><th>{{ t("联系方式", "Contact") }}</th>
                <th>{{ t("结算／授信", "Billing / credit") }}</th></template
              ><template v-if="kind === 'services'"
                ><th>{{ t("洗护类别", "Category") }}</th>
                <th>{{ t("价格", "Price") }}</th></template
              ><template v-if="kind === 'racks'"
                ><th>{{ t("代码", "Code") }}</th>
                <th>{{ t("占用／容量", "Occupied / capacity") }}</th></template
              >
              <th>{{ t("状态", "Status") }}</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in visible" :key="r.id">
              <td>
                <b>{{ r.name }}</b
                ><small v-if="kind === 'services'">{{ r.nameEn }}</small>
              </td>
              <td>{{ dept(r.departmentId) }}</td>
              <template v-if="kind === 'customers'"
                ><td>{{ r.contact || "—" }}</td>
                <td>
                  {{ stateName(r.billing)
                  }}<small v-if="r.billing === 'MONTHLY'">{{
                    money(r.creditLimit, r.currency)
                  }}</small>
                </td></template
              ><template v-if="kind === 'services'"
                ><td>
                  {{
                    catalog.categories.find((c) => c.code === r.category)
                      ?.name || r.category
                  }}
                </td>
                <td>{{ money(r.price, r.currency) }}</td></template
              ><template v-if="kind === 'racks'"
                ><td class="mono">{{ r.code }}</td>
                <td>
                  {{
                    catalog.racks.find((x) => x.record.id === r.id)?.occupied ||
                    0
                  }}
                  / {{ r.capacity }}
                </td></template
              >
              <td>
                <span class="badge" :class="{ gray: !r.enabled }">{{
                  r.enabled ? t("启用", "Active") : t("停用", "Disabled")
                }}</span>
              </td>
              <td>
                <button
                  class="icon-button"
                  :aria-label="t('编辑', 'Edit') + ' ' + r.name"
                  :disabled="pending"
                  @click="edit(r)"
                >
                  <Pencil :size="16" />
                </button>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-if="!rows.length" class="empty">
          {{ t("还没有记录", "No records yet") }}
        </div>
      </div>
      <footer class="pagination">
        <span>{{ page }} / {{ Math.max(1, Math.ceil(rows.length / 15)) }}</span
        ><button :disabled="page === 1" @click="page--">
          {{ t("上一页", "Previous") }}</button
        ><button :disabled="page * 15 >= rows.length" @click="page++">
          {{ t("下一页", "Next") }}
        </button>
      </footer>
    </div>
    <div v-if="editing" class="modal-backdrop">
      <section
        class="modal"
        role="dialog"
        aria-modal="true"
        :aria-label="names[kind]()"
      >
        <header>
          <h2>
            {{ editing.id ? t("编辑", "Edit") : t("新增", "Add") }}
            {{ names[kind]() }}
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
        <form @submit.prevent="save">
          <label
            >{{ t("名称", "Name")
            }}<input
              v-model="editing.name"
              required
              maxlength="120"
              :disabled="pending" /></label
          ><label
            >{{ t("门店", "Store")
            }}<select
              v-model="editing.departmentId"
              required
              :disabled="pending || !!editing.id"
            >
              <option
                v-for="d in catalog.departments.filter(
                  (d) => d.enabled || d.id === editing.departmentId,
                )"
                :key="d.id"
                :value="d.id"
              >
                {{ d.name }}
              </option>
            </select></label
          >
          <template v-if="kind === 'customers'"
            ><label
              >{{ t("联系方式", "Contact")
              }}<input
                v-model="editing.contact"
                maxlength="120"
                :disabled="pending"
            /></label>
            <div class="form-grid">
              <label
                >{{ t("结算方式", "Billing")
                }}<select v-model="editing.billing" :disabled="pending">
                  <option value="RETAIL">{{ t("零售", "Retail") }}</option>
                  <option value="MONTHLY">
                    {{ t("企业月结", "Monthly corporate") }}
                  </option>
                </select></label
              ><label
                >{{ t("币种", "Currency")
                }}<input
                  v-model="editing.currency"
                  required
                  pattern="[A-Z]{3}"
                  :disabled="pending"
              /></label>
            </div>
            <label
              >{{ t("授信额度", "Credit limit")
              }}<input
                v-model="editing.amount"
                required
                inputmode="decimal"
                pattern="[0-9]+(\.[0-9]{1,2})?"
                :disabled="pending" /></label
            ><label
              >{{ t("绑定客户登录账号", "Customer account")
              }}<select v-model="editing.accountId" :disabled="pending">
                <option value="">{{ t("不绑定", "Unbound") }}</option>
                <option
                  v-for="a in catalog.customerAccounts.filter(
                    (a) => a.departmentId === editing.departmentId,
                  )"
                  :key="a.id"
                  :value="a.id"
                >
                  {{ a.username }}
                </option>
              </select></label
            ></template
          >
          <template v-if="kind === 'services'"
            ><label
              >English name<input
                v-model="editing.nameEn"
                required
                maxlength="120"
                :disabled="pending" /></label
            ><label
              >{{ t("洗护类别", "Category")
              }}<select v-model="editing.category" required :disabled="pending">
                <option
                  v-for="c in catalog.categories.filter(
                    (c) => c.enabled || c.code === editing.category,
                  )"
                  :key="c.id"
                  :value="c.code"
                >
                  {{ t(c.name, c.nameEn) }}
                </option>
              </select></label
            >
            <div class="form-grid">
              <label
                >{{ t("币种", "Currency")
                }}<input
                  v-model="editing.currency"
                  required
                  pattern="[A-Z]{3}"
                  :disabled="pending" /></label
              ><label
                >{{ t("单件价格", "Price per garment")
                }}<input
                  v-model="editing.amount"
                  required
                  inputmode="decimal"
                  pattern="[0-9]+(\.[0-9]{1,2})?"
                  :disabled="pending"
              /></label></div
          ></template>
          <template v-if="kind === 'racks'"
            ><label
              >{{ t("稳定挂架代码", "Rack code")
              }}<input
                v-model="editing.code"
                required
                pattern="[A-Za-z0-9_-]{1,60}"
                :readonly="!!editing.id"
                :disabled="pending" /></label
            ><label
              >{{ t("容量（件）", "Capacity (garments)")
              }}<input
                v-model.number="editing.capacity"
                type="number"
                min="1"
                max="10000"
                required
                :disabled="pending" /></label></template
          ><label class="check"
            ><input
              v-model="editing.enabled"
              type="checkbox"
              :disabled="pending"
            />{{ t("启用", "Active") }}</label
          >
          <footer>
            <button type="button" :disabled="pending" @click="canLeave">
              {{ t("取消", "Cancel") }}</button
            ><button class="primary" :disabled="pending">
              {{ t("保存", "Save") }}
            </button>
          </footer>
        </form>
      </section>
    </div>
  </section>
</template>
