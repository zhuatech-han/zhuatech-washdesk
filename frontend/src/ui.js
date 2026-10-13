// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
import { ref } from "vue";
export const language = ref("zh");
/** 双语界面，保持业务名称。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const t = (zh, en) => (language.value === "zh" ? zh : en);
const states = {
  OPEN: ["未取完", "Awaiting collection"],
  COMPLETED: ["已取完", "Collected"],
  CANCELLED: ["已取消", "Cancelled"],
  RECEIVED: ["待加工", "Received"],
  PROCESSING: ["加工中", "Processing"],
  READY: ["待挂架", "Quality passed"],
  RACKED: ["可取衣", "Ready for pickup"],
  DELIVERED: ["已取走", "Collected"],
  PAID: ["已结清", "Paid"],
  RETAIL: ["零售", "Retail"],
  MONTHLY: ["企业月结", "Monthly account"],
};
export const stateName = (s) => (states[s] ? t(...states[s]) : s);
export const money = (n, c = "CNY") =>
  new Intl.NumberFormat(language.value === "zh" ? "zh-CN" : "en", {
    style: "currency",
    currency: c,
  }).format(Number(n || 0) / 100);
/** 严格转分，不静默舍入。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function minor(s) {
  s = String(s);
  if (!/^\d{1,7}(\.\d{1,2})?$/.test(s)) throw new Error("INVALID_INPUT");
  const [a, b = ""] = s.split(".");
  const n = Number(a) * 100 + Number(b.padEnd(2, "0"));
  if (n > 100000000) throw new Error("AMOUNT_LIMIT");
  return n;
}
export const dateTime = (v) =>
  v
    ? new Intl.DateTimeFormat(language.value === "zh" ? "zh-CN" : "en", {
        dateStyle: "medium",
        timeStyle: "short",
      }).format(new Date(v))
    : "—";
export const cloneRecord = (r) => JSON.parse(JSON.stringify(r));
export const confirmation = ref(null);
export function ask(message) {
  if (confirmation.value) return Promise.resolve(false);
  return new Promise((resolve) => (confirmation.value = { message, resolve }));
}
export function answerConfirmation(ok) {
  const x = confirmation.value;
  confirmation.value = null;
  x?.resolve(ok);
}
const errors = {
  UNAUTHENTICATED: [
    "登录已失效，请重新登录。",
    "Session expired. Sign in again.",
  ],
  LOGIN_FAILED: [
    "账号或密码错误，或账号已停用。",
    "Invalid credentials or disabled account.",
  ],
  LOGIN_THROTTLED: [
    "尝试过多，请五分钟后重试。",
    "Too many attempts. Retry in five minutes.",
  ],
  FORBIDDEN: ["没有操作权限。", "Permission denied."],
  OUT_OF_SCOPE: ["不在当前账号的业务范围内。", "Record outside your scope."],
  VERSION_CONFLICT: [
    "记录已变化，请刷新核对。",
    "Record changed. Refresh and verify.",
  ],
  STATE_INVALID: ["当前状态不能操作。", "Action unavailable in this state."],
  INVALID_INPUT: ["请核对必填项和格式。", "Check required fields and formats."],
  NOT_FOUND: ["记录不存在。", "Record not found."],
  CONFLICT: ["记录重复或仍被引用。", "Duplicate or referenced record."],
  LAST_ADMIN: ["必须保留完整管理员。", "Keep an active full administrator."],
  PASSWORD_WEAK: [
    "密码需12–72字节，含大小写字母和数字。",
    "Password: 12–72 bytes, upper/lowercase and digit.",
  ],
  CUSTOMER_PROFILE_REQUIRED: [
    "账号尚未绑定客户，请联系门店。",
    "No active customer profile. Contact the store.",
  ],
  CUSTOMER_ROLE_INVALID: [
    "客户角色仅可有本人查单权限。",
    "Customer role requires only Own orders.",
  ],
  CUSTOMER_ACCOUNT_INVALID: [
    "请选择同门店启用的客户账号。",
    "Choose an active customer account in this store.",
  ],
  CUSTOMER_INVALID: [
    "客户停用、门店不匹配或结算方式不适用。",
    "Customer disabled, wrong store or billing mode.",
  ],
  CATEGORY_DISABLED: ["洗护类别已停用。", "Category disabled."],
  SERVICE_INVALID: [
    "服务停用、类别停用、门店或币种不匹配。",
    "Service disabled or store/currency/category mismatch.",
  ],
  RACK_FULL: [
    "挂架已满、停用或门店不匹配。",
    "Rack full, disabled or wrong store.",
  ],
  RACK_OCCUPIED: [
    "挂架有衣物，不能停用或减少容量。",
    "Rack occupied: cannot disable or shrink below occupancy.",
  ],
  PICKUP_INVALID: [
    "编号必须属于本单、已挂架且不能重复。",
    "Each code must be ready, belong to this order and appear once.",
  ],
  PAYMENT_REQUIRED: [
    "取衣前须核实足额收款。",
    "Verify full retail payment before pickup.",
  ],
  PAYMENT_EXCESS: [
    "收款超过应收或订单已取消。",
    "Payment exceeds balance or order cancelled.",
  ],
  PAYMENT_REFERENCE_REUSED: [
    "凭据编号已使用。",
    "Receipt reference already used.",
  ],
  REFUND_INVALID: [
    "仅支持取消订单按本单原款退款。",
    "Refunds require a cancelled order and its original payment.",
  ],
  REFUND_EXCESS: [
    "超过原款可退余额。",
    "Refund exceeds original payment balance.",
  ],
  INVOICE_REQUIRED: [
    "企业订单通过月结单收款。",
    "Use the monthly statement for corporate payment.",
  ],
  INVOICE_EMPTY: [
    "本月无已取完且未开单的企业订单。",
    "No unbilled collected corporate orders in this month.",
  ],
  CREDIT_LIMIT: ["超出企业客户授信额度。", "Corporate credit limit exceeded."],
  CANCEL_INVALID: [
    "仅全部衣物尚未加工时可取消。",
    "Cancel only before processing any garment.",
  ],
  IDENTITY_LOCKED: [
    "已有历史，身份或结算信息不能改换。",
    "Identity, ownership or billing locked by history.",
  ],
  TIMEZONE_LOCKED: [
    "已有订单，不能修改时区。",
    "Store timezone locked after its first order.",
  ],
  DATE_INVALID: [
    "请核对取衣日期或月结月份。",
    "Check pickup date or statement month.",
  ],
  PHOTO_INVALID: [
    "仅支持有效JPEG/PNG，边长≤4096px、≤1200万像素。",
    "Valid JPEG/PNG: max 4096px per side and 12MP.",
  ],
  PHOTO_LOCKED: [
    "照片仅在收衣后、加工前录入。",
    "Intake photos locked after processing begins.",
  ],
  PHOTO_LIMIT: ["达到每件照片上限。", "Photo limit reached."],
  FILE_TOO_LARGE: ["每张照片最多2MiB。", "Photo maximum: 2MiB."],
  CURRENCY_INVALID: [
    "请选择两位小数的ISO货币代码。",
    "Use a two-decimal ISO currency.",
  ],
  AMOUNT_LIMIT: ["金额超过上限。", "Amount limit exceeded."],
  REQUEST_REUSED: [
    "请求编号已用于其他载荷。",
    "Request ID reused with different input.",
  ],
  REQUEST_ID_INVALID: [
    "请求编号无效，请刷新。",
    "Invalid request ID. Refresh.",
  ],
  DEPARTMENT_DISABLED: ["门店已停用。", "Store disabled."],
  SYSTEM_DIRECTORY_FIXED: [
    "请编辑已有系统目录。",
    "Edit existing system entries.",
  ],
  UNKNOWN_PERMISSION: ["权限代码不存在。", "Unknown permission."],
  RESOURCE_LIMIT: [
    "达到单类数据10000条上限。",
    "Entity limit of 10000 reached.",
  ],
  OLD_PASSWORD_INVALID: ["原密码不正确。", "Original password invalid."],
  RESULT_UNKNOWN: [
    "连接中断，结果未知。刷新核对后再操作，勿直接重复提交。",
    "Connection lost; result unknown. Refresh and verify before resubmitting.",
  ],
  NETWORK_ERROR: ["无法连接服务器。", "Server unavailable."],
};
export const errorMessage = (e) =>
  errors[e.message]
    ? t(...errors[e.message])
    : t("操作未完成", "Action failed") + " (" + e.message + ")";

/** 人可读操作轨迹，对应持久化动作代码。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const actionName = (code) =>
  ({
    RECEIVE: t("收衣开单", "Garment intake"),
    GARMENT_START: t("开始加工", "Start processing"),
    GARMENT_READY: t("质检通过", "Quality passed"),
    GARMENT_REWASH: t("返洗", "Rewash"),
    GARMENT_RACK: t("挂架／换架", "Rack / move"),
    PHOTO_ADD: t("收衣照片", "Intake photo"),
    PICKUP: t("分批取衣", "Partial collection"),
    CANCEL: t("取消订单", "Cancel order"),
    PAYMENT: t("核实收款", "Verified payment"),
    REFUND: t("原款退款", "Original-payment refund"),
    ISSUE: t("生成月结单", "Issue statement"),
    MASTER_SAVE: t("维护业务目录", "Maintain business directory"),
    LOGIN: t("登录", "Sign in"),
    PASSWORD_CHANGE: t("修改密码", "Change password"),
    ADMIN_USERS: t("维护账号", "Maintain accounts"),
    ADMIN_ROLES: t("维护角色", "Maintain roles"),
    ADMIN_DEPARTMENTS: t("维护门店", "Maintain stores"),
    ADMIN_DICTIONARIES: t("维护洗护类别", "Maintain care categories"),
    ADMIN_SETTINGS: t("修改参数", "Change parameters"),
    ADMIN_MENUS: t("维护菜单", "Maintain menus"),
    ADMIN_PERMISSIONS: t("权限名称", "Permission labels"),
  })[code] || code;
/** 核实方式用于人可读收据，不改变原始台账代码。知华科技 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const methodName = (code) =>
  ({
    CASH: t("现金", "Cash"),
    BANK: t("银行转账", "Bank transfer"),
    VERIFIED_OTHER: t("其他已核实方式", "Other verified method"),
  })[code] || code;
