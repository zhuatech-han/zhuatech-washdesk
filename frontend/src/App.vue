<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch } from "vue";
import { LogOut, RefreshCw, KeyRound, X, Menu } from "@lucide/vue";
import { api, resetApi, download } from "./api.js";
import { dialogFocus } from "./dialog-focus.js";
import {
  t,
  language,
  confirmation,
  answerConfirmation,
  errorMessage,
  money,
  stateName,
  actionName,
  dateTime,
} from "./ui.js";
import AdminPanel from "./components/AdminPanel.vue";
import MasterPanel from "./components/MasterPanel.vue";
import OrderPanel from "./components/OrderPanel.vue";
import FinancePanel from "./components/FinancePanel.vue";
const profile = ref(null);
const pending = ref(false);
const message = ref("");
const notice = ref("");
const section = ref("orders");
const catalog = ref({
  departments: [],
  customers: [],
  services: [],
  racks: [],
  categories: [],
  customerAccounts: [],
});
const child = ref(null);
const mobile = ref(false);
const username = ref("");
const password = ref("");
const passwordDialog = ref(false);
const oldPassword = ref("");
const newPassword = ref("");
const report = ref(null);
const events = ref([]);
const securityEvents = ref([]);
const booting = ref(true);
const has = (c) => profile.value?.permissions.includes(c);
const menus = computed(() => {
  const p = profile.value;
  if (!p) return [];
  const list = [...p.menus];
  if (p.scope === "ALL")
    for (const [code, name, nameEn] of [
      ["users", "登录账号", "Accounts"],
      ["roles", "角色权限", "Roles"],
      ["settings", "门店与设置", "Stores & settings"],
    ])
      if (p.permissions.includes(code) && !list.some((m) => m.code === code))
        list.push({ code, name, nameEn });
  return list;
});
function failure(e) {
  message.value = errorMessage(e);
  if (e.status === 401) {
    profile.value = null;
    password.value = "";
    resetApi();
  }
}
watch(
  language,
  () =>
    (document.documentElement.lang = language.value === "zh" ? "zh-CN" : "en"),
);
async function loadCatalog() {
  if (has("catalog")) catalog.value = await api("/catalog");
}
async function loadPage() {
  try {
    if (section.value === "reports") report.value = await api("/reports");
    if (section.value === "audit") {
      events.value = await api("/audit");
      securityEvents.value = await api("/security-audit");
    }
  } catch (e) {
    failure(e);
  }
}
async function navigate(code) {
  if (pending.value) return;
  if (child.value?.canLeave && !(await child.value.canLeave())) return;
  section.value = code;
  message.value = "";
  notice.value = "";
  mobile.value = false;
  try {
    await loadCatalog();
    await loadPage();
  } catch (e) {
    failure(e);
  }
}
/** 所有变更先处理业务错误，仅成功后重读目录；连接中断不自动重放。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function perform(path, method, body) {
  if (pending.value) return null;
  pending.value = true;
  message.value = "";
  notice.value = "";
  try {
    const result = await api(path, { method, body });
    try {
      if (!path.startsWith("/auth/") && profile.value) await loadCatalog();
    } catch (e) {
      failure(e);
    }
    notice.value = t("已保存", "Saved");
    return result;
  } catch (e) {
    failure(e);
    return null;
  } finally {
    pending.value = false;
  }
}
async function setup(p) {
  profile.value = p;
  await loadCatalog();
  section.value = p.menus[0]?.code || "none";
  await loadPage();
}
async function login() {
  if (pending.value) return;
  pending.value = true;
  message.value = "";
  try {
    const p = await api("/auth/login", {
      method: "POST",
      body: { username: username.value, password: password.value },
    });
    password.value = "";
    resetApi();
    await setup(p);
  } catch (e) {
    failure(e);
  } finally {
    password.value = "";
    pending.value = false;
  }
}
async function logout() {
  if (pending.value) return;
  if (child.value?.canLeave && !(await child.value.canLeave())) return;
  const r = await perform("/auth/logout", "POST", {});
  if (r) {
    profile.value = null;
    username.value = "";
    resetApi();
    notice.value = "";
  }
}
async function changePassword() {
  const r = await perform("/auth/password", "POST", {
    oldPassword: oldPassword.value,
    newPassword: newPassword.value,
  });
  oldPassword.value = "";
  newPassword.value = "";
  if (r) {
    passwordDialog.value = false;
    profile.value = null;
    resetApi();
  }
}
async function exportCsv() {
  try {
    await download("/export", "washdesk-orders.csv");
  } catch (e) {
    failure(e);
  }
}
let releaseDialogFocus;
onMounted(() => {
  releaseDialogFocus = dialogFocus(async () => {
    if (pending.value) return;
    if (confirmation.value) {
      answerConfirmation(false);
      return;
    }
    if (passwordDialog.value) {
      passwordDialog.value = false;
      oldPassword.value = "";
      newPassword.value = "";
      return;
    }
    if (child.value?.canLeave) await child.value.canLeave();
  });
});
onBeforeUnmount(() => releaseDialogFocus?.());
onMounted(async () => {
  try {
    await setup(await api("/auth/me"));
  } catch (e) {
    if (e.status !== 401) failure(e);
  } finally {
    booting.value = false;
  }
});
const pageTitle = computed(() =>
  menus.value.find((m) => m.code === section.value),
);
</script>
<template>
  <div v-if="!profile" class="login-page">
    <section class="login-card">
      <div class="brand-row">
        <img src="/brand/logo.jpg" alt="知华科技" />
        <div>
          <strong>WashDesk</strong
          ><span>{{ t("知华洗衣门店管理", "ZhiHua laundry management") }}</span>
        </div>
        <button
          class="language-button"
          @click="language = language === 'zh' ? 'en' : 'zh'"
        >
          {{ language === "zh" ? "EN" : "中文" }}
        </button>
      </div>
      <h1>{{ t("登录门店", "Sign in") }}</h1>
      <p v-if="booting" class="muted">
        {{ t("检查会话…", "Checking session…") }}
      </p>
      <p v-if="message" class="form-error" role="alert">{{ message }}</p>
      <form autocomplete="off" @submit.prevent="login">
        <label
          >{{ t("登录账号", "Username")
          }}<input
            v-model="username"
            autocomplete="off"
            maxlength="60"
            required
            :disabled="pending" /></label
        ><label
          >{{ t("密码", "Password")
          }}<input
            v-model="password"
            type="password"
            autocomplete="off"
            maxlength="128"
            required
            :disabled="pending" /></label
        ><button class="primary full" :disabled="pending || booting">
          {{ pending ? t("登录中…", "Signing in…") : t("登录", "Sign in") }}
        </button>
      </form>
      <footer>
        <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener">{{
          t("知华科技", "ZhiHua Technology")
        }}</a
        ><span>{{
          t(
            "公开源码学习版 · 商用须授权",
            "Source-available learning edition · commercial authorization required",
          )
        }}</span>
      </footer>
    </section>
  </div>
  <div v-else class="app-shell">
    <aside :class="{ show: mobile }">
      <div class="brand-row">
        <img src="/brand/logo.jpg" alt="知华科技" />
        <div>
          <strong>WashDesk</strong
          ><span>{{ t("洗衣门店管理", "Laundry operations") }}</span>
        </div>
      </div>
      <nav>
        <button
          v-for="m in menus"
          :key="m.code"
          :class="{ active: section === m.code }"
          :disabled="pending"
          @click="navigate(m.code)"
        >
          {{ t(m.name, m.nameEn) }}</button
        ><button
          v-if="profile.scope === 'CUSTOMER'"
          :class="{ active: section === 'statements' }"
          :disabled="pending"
          @click="navigate('statements')"
        >
          {{ t("我的月结单", "My statements") }}
        </button>
        <button
          :class="{ active: section === 'about' }"
          @click="navigate('about')"
        >
          {{ t("关于与授权", "About & license") }}
        </button>
      </nav>
      <footer>
        <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener">{{
          t("知华科技 · 商业咨询", "ZhiHua · commercial inquiries")
        }}</a
        ><small>{{
          t(
            "公开源码学习版 · 商用须授权",
            "Learning edition · authorization required",
          )
        }}</small>
      </footer>
    </aside>
    <main>
      <header class="topbar">
        <button
          class="mobile-toggle icon-button"
          :aria-label="t('打开菜单', 'Open menu')"
          @click="mobile = !mobile"
        >
          <Menu :size="20" /></button
        ><span>{{
          pageTitle ? t(pageTitle.name, pageTitle.nameEn) : "WashDesk"
        }}</span>
        <div class="actions">
          <span class="identity"
            >{{ profile.displayName }}<small>{{ profile.role }}</small></span
          ><button
            class="language-button"
            @click="language = language === 'zh' ? 'en' : 'zh'"
          >
            {{ language === "zh" ? "EN" : "中文" }}</button
          ><button
            class="icon-button"
            :disabled="pending"
            :aria-label="t('修改密码', 'Change password')"
            @click="passwordDialog = true"
          >
            <KeyRound :size="17" /></button
          ><button
            class="icon-button"
            :disabled="pending"
            :aria-label="t('退出', 'Sign out')"
            @click="logout"
          >
            <LogOut :size="17" />
          </button>
        </div>
      </header>
      <div class="workspace">
        <div v-if="message" class="form-error global-message" role="alert">
          <span>{{ message }}</span
          ><button
            class="icon-button"
            :aria-label="t('关闭提示', 'Dismiss')"
            @click="message = ''"
          >
            <X :size="16" />
          </button>
        </div>
        <p v-else-if="notice" class="notice" role="status">{{ notice }}</p>
        <AdminPanel
          v-if="['users', 'roles', 'settings'].includes(section)"
          :key="section"
          ref="child"
          :section="section"
          :pending="pending"
          :message="message"
          :perform="perform"
          @error="failure"
        />
        <MasterPanel
          v-else-if="['masters', 'customers'].includes(section)"
          :key="section"
          ref="child"
          :section="section"
          :catalog="catalog"
          :refresh-catalog="loadCatalog"
          :message="message"
          :pending="pending"
          :perform="perform"
          @error="failure"
        />
        <OrderPanel
          v-else-if="
            ['orders', 'pickup', 'process', 'portal'].includes(section)
          "
          :key="section"
          ref="child"
          :section="section"
          :profile="profile"
          :catalog="catalog"
          :refresh-catalog="loadCatalog"
          :message="message"
          :pending="pending"
          :perform="perform"
          @error="failure"
        />
        <FinancePanel
          v-else-if="['finance', 'statements'].includes(section)"
          :key="section"
          ref="child"
          :portal="section === 'statements'"
          :catalog="catalog"
          :message="message"
          :pending="pending"
          :perform="perform"
          @orders="navigate('orders')"
          @error="failure"
        />
        <section v-else-if="section === 'reports'">
          <header class="page-heading">
            <h1>{{ t("经营报表", "Reports") }}</h1>
            <div class="actions">
              <button @click="loadPage">
                <RefreshCw :size="15" />{{ t("刷新", "Refresh") }}</button
              ><button v-if="has('export')" @click="exportCsv">
                {{ t("导出订单CSV", "Export orders CSV") }}
              </button>
            </div>
          </header>
          <template v-if="report"
            ><div class="stat-grid">
              <div class="stat">
                <small>{{ t("订单数", "Orders") }}</small
                ><strong>{{ report.orders }}</strong>
              </div>
              <div v-for="(n, s) in report.garments" :key="s" class="stat">
                <small>{{ stateName(s) }}</small
                ><strong>{{ n }}</strong>
              </div>
            </div>
            <div class="panel">
              <div class="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>{{ t("币种", "Currency") }}</th>
                      <th>{{ t("有效订单总额", "Active order totals") }}</th>
                      <th>{{ t("净收款", "Net receipts") }}</th>
                      <th>{{ t("待收款", "Receivables") }}</th>
                      <th>{{ t("取消单待退款", "Refunds pending") }}</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="(v, c) in report.currencies" :key="c">
                      <td>{{ c }}</td>
                      <td>{{ money(v.sales, c) }}</td>
                      <td>{{ money(v.received, c) }}</td>
                      <td>{{ money(v.receivable, c) }}</td>
                      <td>{{ money(v.refundDue, c) }}</td>
                    </tr>
                  </tbody>
                </table>
                <div
                  v-if="!Object.keys(report.currencies).length"
                  class="empty"
                >
                  {{ t("暂无业务数据", "No business data") }}
                </div>
              </div>
            </div></template
          >
        </section>
        <section v-else-if="section === 'audit'">
          <header class="page-heading">
            <h1>{{ t("操作记录", "Activity") }}</h1>
            <button @click="loadPage">{{ t("刷新", "Refresh") }}</button>
          </header>
          <div class="panel">
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>{{ t("时间", "Time") }}</th>
                    <th>{{ t("操作者", "Actor") }}</th>
                    <th>{{ t("对象", "Object") }}</th>
                    <th>{{ t("操作", "Action") }}</th>
                    <th>{{ t("备注", "Note") }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="e in events" :key="e.id">
                    <td>{{ dateTime(e.createdAt) }}</td>
                    <td>{{ e.actor }}</td>
                    <td>{{ e.objectType }} #{{ e.objectId }}</td>
                    <td>{{ actionName(e.action) }}</td>
                    <td class="wrap">{{ e.note }}</td>
                  </tr>
                </tbody>
              </table>
              <div v-if="!events.length" class="empty">
                {{ t("暂无记录", "No activity") }}
              </div>
            </div>
          </div>
          <div class="panel">
            <h2 class="panel-title">
              {{ t("登录与管理审计", "Authentication & administration audit") }}
            </h2>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>{{ t("时间", "Time") }}</th>
                    <th>{{ t("操作者", "Actor") }}</th>
                    <th>{{ t("操作", "Action") }}</th>
                    <th>{{ t("对象", "Object") }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="e in securityEvents" :key="e.id">
                    <td>{{ dateTime(e.createdAt) }}</td>
                    <td>{{ e.actor }}</td>
                    <td>{{ actionName(e.action) }}</td>
                    <td>{{ e.objectId }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </section>
        <section v-else-if="section === 'about'">
          <header class="page-heading">
            <h1>{{ t("关于 WashDesk", "About WashDesk") }}</h1>
          </header>
          <div class="panel about-panel">
            <img src="/brand/logo.jpg" width="52" alt="知华科技" />
            <h2>
              {{
                t(
                  "知华科技（上海如静知华信息科技有限公司）",
                  "ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)",
                )
              }}
            </h2>
            <p>WashDesk 1.0.0 · Java 21 / Spring Boot / Vue 3 / MySQL</p>
            <p>
              {{
                t(
                  "源码公开、非商业使用。仅限个人学习、技术研究与非商业交流；未经书面授权不得商用。",
                  "Source available for personal learning, research and non-commercial exchange. Commercial use requires written authorization.",
                )
              }}
            </p>
            <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
              >https://www.zhuatech.cn/</a
            ><template v-if="language === 'zh'"
              ><p>商业授权或深度定制开发请联系知华科技。</p>
              <div class="contact-grid">
                <div>
                  <img src="/brand/wechat-zhuatech.png" alt="微信 zhuatech" />
                  <p>微信 zhuatech</p>
                </div>
                <div>
                  <img src="/brand/wechat-zhuatech2.png" alt="微信 zhuatech2" />
                  <p>微信 zhuatech2</p>
                </div>
              </div></template
            ><template v-else
              ><p>
                <a href="mailto:han@zhuatech.cn">han@zhuatech.cn</a> ·
                <a href="mailto:jack@zhuatech.cn">jack@zhuatech.cn</a>
              </p>
              <p>
                <a
                  href="https://wa.me/8617521234993"
                  target="_blank"
                  rel="noopener"
                  >WhatsApp +86 17521234993</a
                >
              </p></template
            >
          </div>
        </section>
        <div v-else class="empty">
          {{
            t(
              "当前角色没有可用菜单，请联系管理员。",
              "No available menu. Contact the administrator.",
            )
          }}
        </div>
      </div>
    </main>
  </div>
  <div v-if="passwordDialog" class="modal-backdrop">
    <section
      class="modal"
      role="dialog"
      aria-modal="true"
      :aria-label="t('修改密码', 'Change password')"
    >
      <header>
        <h2>{{ t("修改密码", "Change password") }}</h2>
        <button
          class="icon-button"
          :aria-label="t('关闭', 'Close')"
          :disabled="pending"
          @click="
            passwordDialog = false;
            oldPassword = '';
            newPassword = '';
          "
        >
          <X :size="20" />
        </button>
      </header>
      <p v-if="message" class="form-error" role="alert">{{ message }}</p>
      <form autocomplete="off" @submit.prevent="changePassword">
        <label
          >{{ t("原密码", "Current password")
          }}<input
            v-model="oldPassword"
            type="password"
            autocomplete="off"
            required
            :disabled="pending" /></label
        ><label
          >{{ t("新密码", "New password")
          }}<input
            v-model="newPassword"
            type="password"
            autocomplete="off"
            required
            :disabled="pending"
        /></label>
        <p class="hint">
          {{
            t(
              "12–72字节，含大小写字母和数字。修改后重新登录。",
              "12–72 bytes, upper/lowercase and digit. Sign in again after change.",
            )
          }}
        </p>
        <footer>
          <button class="primary" :disabled="pending">
            {{ t("修改", "Change") }}
          </button>
        </footer>
      </form>
    </section>
  </div>
  <div v-if="confirmation" class="modal-backdrop confirm-backdrop">
    <section
      class="modal"
      role="dialog"
      aria-modal="true"
      :aria-label="t('确认', 'Confirm')"
    >
      <h2>{{ t("确认", "Confirm") }}</h2>
      <p>{{ confirmation.message }}</p>
      <footer>
        <button @click="answerConfirmation(false)">
          {{ t("返回", "Back") }}</button
        ><button class="primary" @click="answerConfirmation(true)">
          {{ t("确认", "Confirm") }}
        </button>
      </footer>
    </section>
  </div>
</template>
