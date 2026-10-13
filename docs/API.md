# WashDesk API

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2

同源/api接口，30分钟HttpOnly／SameSite Strict会话。GET /auth/csrf取header与token；所有写请求带返回的请求头。POST /auth/login用户名和密码，登录后重新取CSRF；GET /auth/me读取实时权限；POST /auth/logout使会话失效，POST /auth/password验证原密码并撤销会话。

|接口|能力|
|---|---|
|GET /catalog|岗位内客户、门店、类别、服务、挂架占用；加工岗无客户目录|
|POST /masters/{customers,services,racks}|新增业务目录|
|PUT /masters/{type}/{id}|完整更新，含version；历史身份不可改|
|GET /orders?q=&state=&page=1&sort=newest|范围内列表，sort为newest/oldest/due|
|POST /orders|departmentId/customerId/customerVersion/dueDate/items/note/requestId；每项serviceId/serviceVersion/description/conditionNote|
|GET /orders/{id}|衣物、照片元数据、交接、收退款、轨迹；金额paid/balance对零售订单解释，企业款项查月结单|
|POST /garments/{id}/{start,ready,rewash,rack}|version/requestId/note，rack另带rackId|
|POST /garments/{id}/photos?version=|multipart file，JPEG/PNG≤2MiB，仅加工前|
|GET /photos/{id}|当前身份下的照片字节，no-store|
|POST /orders/{id}/pickup|version/requestId/barcodes/receiver/note；完整衣物编号数组|
|POST /orders/{id}/cancel|version/requestId/note；仅加工前|
|POST /payments/{ORDER,INVOICE}/{id}|version/requestId/kind/amount/reference/method；退款另带originalId|
|GET/POST /invoices|查询或用customerId/month/requestId生成月结单|
|GET /invoices/{id}|冻结订单成员、款项、余额|
|GET /reports、/export|按币种报表和安全CSV，需对应权限|
|GET /audit、/security-audit|业务轨迹与认证配置审计|
|GET /admin/options、/admin/{kind}|管理目录，kind为users/roles/permissions/departments/dictionaries/menus/settings|
|POST /admin/{kind}、PUT /admin/{kind}/{id}|对应权限及ALL范围，版本与最后管理员校验|

amount和price均为整数最小货币单位，CNY1299代表12.99元，拒绝浮点分／零／负款项。只支持两位小数ISO货币。所有关键命令UUID一次绑定账号和载荷；相同完整请求返回结果身份，不同载荷／账号重用拒绝。请求结果可能是当前记录（不是历史状态快照）；网络中断后先重读业务。身份无效401、权限或门店／客户范围不足403、参数400、业务／版本冲突409、载荷超限413、登录失败节流429。

本版不开放匿名查询，不提供第三方支付回调或通知端点。内部生成衣物编号不是客户公开查单凭据。客户登录需门店管理者绑定本人档案，不支持公共注册。SQL查询条件由服务固定，客户无法提交动态字段或SQL。权限目录名称可改，执行能力代码固定。
