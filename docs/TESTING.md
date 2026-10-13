# WashDesk 验证方式

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2

Java21和Maven3.9：在backend运行mvn spotless:check test，集成测试实际使用Spring Security、Flyway、JPA及H2的MySQL模式；H2不能替代真实MySQL验证。前端Node≥24.19.0：在frontend运行npm ci、npm run format:check、npm run lint、npm test、npm run build。

镜像构建运行后端测试及前端全部格式／代码检查／测试／构建，不跳过测试。全新数据库流程：

```sh
python3 scripts/init-env.py
docker compose -p washdesk-quality config --quiet
docker compose -p washdesk-quality up --build -d --wait --wait-timeout 1800
python3 scripts/quality.py --base http://127.0.0.1:8158 --state output/washdesk-quality-state.json
docker compose -p washdesk-quality restart mysql backend
# 等待健康再验证，不在服务未启动时宣称通过
docker compose -p washdesk-quality up -d --wait --wait-timeout 1800
python3 scripts/verify-persistence.py --state output/washdesk-quality-state.json
python3 scripts/release-check.py
```

quality脚本只供独立可丢弃测试实例，创建QA客户、账号和资金记录；不得对真实经营库运行。状态文件包括测试凭据mode0600，必须保留私有、不提交。覆盖五件先取三件再取两件、原款退款、返洗、挂架容量、企业授信／月结、重复收款和命令、客户照片隐私、实时角色和跨门店拒绝。备份恢复后同一state加--base目标端口检查业务、资金、取衣和照片SHA256。运行完成只停止清理自己的测试项目，删除私有测试状态和备份；不能影响其他项目。

手工浏览器验收须实际完成收衣表单、加工／挂架、编号核对分批取衣、资金和月结、账号／权限／字典／菜单、客户视图、移动端和中英文。测试密码不保存浏览器，复用同一会话；只切换QA账号的实时角色，不操作真实账号。所有截图来自当前运行页面，使用明确QA资料，不是实际客户案例。截图前检查Apple／保存密码弹窗、敏感值、遮挡和内容截断。

已执行检查及准确数量以发布交付记录为准，README不将未执行硬件或压力测试标为通过。
