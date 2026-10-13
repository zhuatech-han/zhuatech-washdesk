# WashDesk 部署、升级与备份

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2

## 环境及首次启动
Docker Engine/Desktop和Compose v2可运行Java21/Maven、Node24.19.0、MySQL8.4、Nginx镜像，能访问官方镜像与包源。建议本机至少4GB可用内存，规划数据库照片容量；未做并发容量承诺。

```sh
python3 scripts/init-env.py
docker compose -p washdesk config --quiet
docker compose -p washdesk up --build -d --wait --wait-timeout 1800
```

init-env生成mode0600私有.env，存在则拒绝覆盖。账号默认admin，可在首次初始化前用ADMIN_USERNAME设置小写3–60位账号；使用ADMIN_PASSWORD的独立强密码登录，不提供通用演示密码。仅首次空库建立账号，重启不覆盖密码；改.env不重置已存在管理员。

访问 <http://127.0.0.1:8158/>，健康 <http://127.0.0.1:8158/actuator/health>。BIND_ADDRESS默认127.0.0.1；WEB_PORT可覆盖，勿停止其他项目占用的端口。MySQL与后端无宿主公网端口。首次空卷初始化允许20分钟宽限，再进行60次健康重试；低速本机磁盘可能需要数分钟，不能把健康检查失败当成已启动。首次Flyway依次执行V1身份结构、V2洗衣业务；Hibernate validate保留检查。不要手动修改已执行迁移，升级追加新版本SQL，先备份并在独立实例演练。

## HTTPS与外部数据库
正式部署由经营者维护域名、HTTPS反代、访问控制、系统更新、监控和备份。确认HTTPS后设置COOKIE_SECURE=true；默认server.forward-headers-strategy=none，不信任任意客户端转发头；如需配置可信代理须自行限定来源。外部MySQL使用jdbc:mariadb://连接串，独立设置DATABASE_USER和DATABASE_PASSWORD，要求sslMode=verify-full与可信CA，外部库备份由其管理员承担。不要把外部数据库凭据写到仓库。照片存在MySQL，不依赖文件名或静态目录。

## 备份恢复
以下仅支持内置数据库。备份短暂暂停该项目后端写入，恢复运行；包含照片、Flyway历史、业务和密码散列，ZIP mode0600，不得上传Git。仅处理可信备份。

```sh
python3 scripts/backup.py --project washdesk --output private-backups/washdesk.zip
# 另准备一个私有环境文件，使用未占用的本机WEB_PORT，例如8159
python3 scripts/restore.py private-backups/washdesk.zip --project washdesk-restore --env-file /path/to/private-restore.env
```

恢复拒绝现有目标容器、卷、网络与被占用端口；验证产品标识、格式、大小和SHA256，只创建独立目标库，非覆盖式恢复。单个SQL恢复限制512MiB，超过需独立数据库运维方案。备份默认压缩但非加密，异地保存须经营者另行加密和控制权限。

验证目标健康、管理员登录、衣物数量／位置、资金余额、交接和照片；切换业务前完成这些检查。本仓库不提供自动切换生产或零停机迁移。

## 故障定位
构建下载失败核对镜像和包源网络，保留错误日志后重试；不要跳过测试。健康失败用docker compose -p washdesk logs backend/mysql查看日志，日志不应公开未脱敏凭据。迁移失败核对数据库类型、执行历史、权限、时区；不能禁用结构检查掩盖问题。初始化密码必须12–72字节，含大小写字母、数字，不能含控制字符。502可能是后端尚未健康；Nginx动态解析服务名，后端重启后会恢复连接。

## 清理
正常停止保留卷：docker compose -p washdesk down。只有明确可丢弃的测试库才执行down -v；生产卷和其他项目不能清理。镜像和依赖缓存不含业务资料；请勿使用全局prune清理本项目。

后台就绪探针为/actuator/health/readiness，包含应用就绪状态和数据库连接，等待初始化任务完成再允许前端启动。[Spring Boot官方就绪探针说明](https://docs.spring.io/spring-boot/4.0/reference/actuator/endpoints.html)。首次空卷检测用应用账号执行SELECT 1，不将临时初始化服务器误判为可用。
