[简体中文](README.md) · [English](README.en.md)

# WashDesk · Laundry and dry-cleaning garment management

**ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)** · [Website](https://www.zhuatech.cn/)

A customer brings five garments and collects three: identify where the other two are, whether they are ready, and how the retail balance or corporate statement is settled. WashDesk tracks **individual intake → processing / QC / rewash → racking → partial collection → verified payments / corporate monthly billing**. It manages customer garments, rather than bulk hotel linen batches.

**Source-available, non-commercial learning edition.** Personal learning, research and non-commercial exchange only; commercial use requires written authorization from the company under [LICENSE](LICENSE). This is not a free-for-commercial-use MIT/Apache project. Dependencies retain their own licenses. No customer adoption, paid transaction, production operation or device certification is claimed.

## Operators and boundaries

For laundry/dry-cleaning shops and operators handling corporate garment accounts, studying garment identification, processing locations, partial handover and receivables. One operating entity with store-level data scope; not a multi-tenant SaaS.

|Interface / role|Implemented operations|
|---|---|
|Counter desk|Customers, individual intake, condition photos, labels, verified retail receipts/refunds, code checking and partial handover|
|Processing / QC|This store’s orders, start, pass QC, reasoned rewash, racking/moving and location lookup|
|Finance|Verified retail receipts/refunds, corporate credit, statements freezing fully collected orders, partial payments, reports/export|
|Customer portal|Authenticated access only to a bound customer’s orders, photos, locations, pickups and statements|
|Administration|Accounts, roles, fixed permission registry, menus, stores, IANA zones, care categories, photo/item limits and audit|

Role names are editable; execution permission codes are fixed by implemented APIs. Custom roles require proper permissions and scope. CUSTOMER roles have only own-order access and must be bound to an active customer in the same store; initial binding is permitted after orders exist, but an existing binding cannot be reassigned. Counter staff have manual finance permission by default; administrators can split roles. Independent two-person QC/payment approval is not enforced.

## Implemented workflow

- Independent garment IDs, descriptions, defects/condition acknowledgement and frozen service charges. Private JPEG/PNG intake photos stored in MySQL; ≤2MiB each, ≤4096px per side and ≤12MP. Default 3, configurable 1–5 per garment, upload only before processing.
- Valid received / processing / quality-passed / racked / collected transitions, version checks, reasoned rewash and rack release; collected garments cannot return to processing.
- Store rack codes, capacity, occupancy and moves; no over-capacity or disabling occupied racks. Search orders/customers or full garment codes. Keyboard-wedge scanner input/manual entry supported; camera scanning is not implemented.
- Partial collection verifies each complete code belongs to this order, is racked and is not repeated; retains actual recipient, staff, time and item membership. Collection releases racks atomically.
- Retail deposits and partial verified payments; full payment before collection. Reject excess amounts, duplicate references and repeated commands. Cancellation only before processing, with reason/history; refunds refer to the cancelled order’s original payment and cannot exceed its remaining amount.
- Corporate credit checked against total unsettled exposure when receiving orders. Corporate orders have no direct payments. Fully collected, unbilled orders are frozen into statements by customer and collection month in the store’s IANA zone. Later orders can form a supplemental statement for that month. Print and partial verified payments supported; transferring receivables to a statement does not double-count them.
- Integer minor units for ISO currencies with two decimal places; reports separated by currency and formula-safe CSV export. Last 500 business and authentication/configuration audit entries.
- BCrypt12, 30-minute HttpOnly/SameSite Strict sessions, CSRF, login throttling, live permissions and store/customer scope. Disabling/resetting passwords revokes existing sessions; last complete administrator protected.
- Chinese/English interface, responsive menu/tables, search/state filter, 20-row order paging and newest/oldest/pickup-date sorting. Operator-entered text is not automatically translated.

**Payments are manually verified ledger records, not automatic charges or gateway confirmations. Statements are not tax invoices.** Not implemented: payment gateways, SMS, official social accounts, public registration, multi-tenancy, automated conveyors, washing-machine interfaces, camera scanning, independent two-person QC, post-collection refunds, damage/loss claims or tax invoicing. Core operation needs no paid third-party API key; operators configure/validate HTTPS, optional external MySQL/CA, printers and scanners. No placeholder integration is described as complete.

## Actual running pages

Screenshots show the current running application with explicitly marked QA records, not real customer cases, generated mockups or production-use evidence. Business names may remain Chinese because operator data is preserved.

### Login

Separate deployment password and bilingual login.

![Login](docs/screenshots/login.jpg)

### Store desk

Search, state filters, paging and order progress.

![Store desk](docs/screenshots/office-home.jpg)

### Garment intake

Per-garment services, condition notes and frozen charges.

![Garment intake](docs/screenshots/intake.jpg)

### Partial collection

Three of five collected; two retain their progress and rack location.

![Partial collection](docs/screenshots/partial-pickup.jpg)

### Processing and racks

Processing, QC, rewash, racking and locations.

![Processing and racks](docs/screenshots/processing.jpg)

### Accounts

Manage login identities, roles and store assignments.

![Accounts](docs/screenshots/accounts.jpg)

### Reports

Net receipts and receivables by currency, without mixing currencies.

![Reports](docs/screenshots/reports.jpg)

### Stores and settings

Store time zones, categories, parameters, menus and permissions.

![Stores and settings](docs/screenshots/settings.jpg)

### Customer portal

Customers see only their own garments and pickups.

![Customer portal](docs/screenshots/customer-home.jpg)

### Corporate statement

Freeze collected orders, verify payments and review the balance.

![Corporate statement](docs/screenshots/statements.jpg)

### Barcode labels

Code128 labels from runtime records; physical devices not certified.

![Barcode labels](docs/screenshots/labels.jpg)

### Mobile view

Narrow-screen navigation, detail and scrollable tables.

![Mobile view](docs/screenshots/mobile.jpg)

### English interface

Language switch preserves operator-entered business names.

![English interface](docs/screenshots/english.jpg)

## Architecture and requirements

Java21, Spring Boot4.0.7, Security, JPA, Flyway, MariaDB Connector/J3.5.10 connecting to **MySQL8.4**. Frontend Vue3.5.43, Vite8.1.5, JsBarcode3.12.3 and Lucide. Node≥24.19.0, Maven3.9, Python3.10+, Docker Engine/Desktop and Compose v2. Container startup requires no host Java/Node. Allow at least 4GB available RAM and plan database photo storage; no capacity/load-test commitment.

```text
backend/               Java APIs, access, garment workflow, photos and ledger
  src/main/resources/  application.yml and Flyway V1/V2
frontend/              Vue, private photos and Code128 preview
  public/brand/        Official brand assets
scripts/               Initialization, verification, backup/restore and release checks
compose.yaml           MySQL, backend and Nginx
compose.dev.yaml       Local source-debug database port only
.github/workflows/     Frontend/backend and fresh MySQL verification
docs/                  Business, operation, API, security, testing, deployment, screenshots
```

Browser → same-origin Nginx /api → Spring Security and transactions → MySQL. Flyway migrations and Hibernate schema validation both remain enabled. Private photo BLOBs are included in database backups. Writes are serialized by a shared row lock for low-volume learning; queries capped at 10000 records per entity. This is not a high-concurrency production commitment.

## Install, initialize and start

```sh
git clone https://github.com/zhuatech-han/zhuatech-washdesk.git
cd zhuatech-washdesk
python3 scripts/init-env.py
docker compose -p washdesk config --quiet
docker compose -p washdesk up --build -d --wait --wait-timeout 1800
```

[Frontend](http://127.0.0.1:8158/) and [health](http://127.0.0.1:8158/actuator/health). Default administrator `admin`; read **ADMIN_PASSWORD privately from .env**. Initialization creates an independent strong password; no shared demo password. The mode0600 file is never committed and the script refuses to overwrite it. Restart preserves existing accounts/passwords; changing .env does not reset an existing account.

Fresh MySQL applies [V1 identity](backend/src/main/resources/db/migration/V1__identity.sql) then [V2 laundry](backend/src/main/resources/db/migration/V2__laundry.sql), including tables, FKs, indexes and Flyway history. Bootstrap creates only the administrator, roles, permissions, menus, categories and limits, not customers/garments/fictitious payments. Configure stores, services, racks and customers before intake.

|Environment name|Purpose|
|---|---|
|MYSQL_ROOT_PASSWORD / DATABASE_PASSWORD|Required independent database credentials|
|ADMIN_USERNAME / ADMIN_PASSWORD|First administrator; password 12–72 bytes with upper/lowercase and digit|
|WEB_PORT / BIND_ADDRESS|Default8158 / 127.0.0.1; override a busy port|
|COOKIE_SECURE|false for local HTTP; true after verified HTTPS reverse proxy|
|DATABASE_URL / DATABASE_USER|Optional external MySQL via jdbc:mariadb://; sslMode=verify-full and trusted CA required externally|
|MYSQL_DEV_PORT|Local source debugging only; default33158|

.env.example contains names and safety notes only. No host database/backend port exposed by the normal Compose file. Store IANA zone controls due dates and statement months, and is fixed after the first order.

For source debugging, use separate terminals after generating .env:

```sh
docker compose -p washdesk-dev -f compose.yaml -f compose.dev.yaml up -d --wait mysql
python3 scripts/dev-backend.py
# another terminal
cd frontend
npm ci --no-audit --no-fund
npm run dev
```

Host Java21/Maven3.9 required for the backend script, which passes private configuration only to its child process. Vite defaults to http://127.0.0.1:5173 and proxies host8080. Avoid other processes occupying that port; deployed operation uses Compose/Nginx, not Vite dev server.

## Verification

```sh
mvn -B -f backend/pom.xml spotless:check test
cd frontend
npm ci --no-audit --no-fund
npm run format:check
npm run lint
npm test
npm run build
cd ..
# Disposable, isolated test database only
python3 scripts/quality.py --base http://127.0.0.1:8158 --state output/washdesk-quality-state.json
python3 scripts/verify-persistence.py --state output/washdesk-quality-state.json
python3 scripts/release-check.py
git diff --check
```

33 backend tests (7 policy / 26 Spring Security/JPA/Flyway integration) and 11 frontend tests. H2 tests are supplemented by a real fresh MySQL8.4 volume: healthy startup/login, five garments collected three then two, rewash/racks, payments/statements/cancel/refunds, idempotency, customer/cross-store scope, private photo byte checks, restart and isolated backup/restore. Docker builds run tests. Exact execution results belong to the release delivery record. QA scripts create test records and mode0600 private credential state; never run them against real operations or commit their output. Physical hardware, real payment rails, production loads and external customer adoption are unverified.

## Deployment, upgrades and security

[Deployment and recovery](docs/DEPLOYMENT.md), [operations](docs/OPERATIONS.md), [business](docs/BUSINESS.md), [API](docs/API.md), [testing](docs/TESTING.md) and [security](docs/SECURITY.md) include detailed Chinese instructions. Backup before upgrading, rehearse in an isolated instance and append migrations rather than modifying executed SQL. Backup pauses only the named backend’s writes and includes photos/password hashes; restore validates hashes and refuses existing projects/volumes/networks/busy ports, accepting trusted local ZIP only (SQL≤512MiB). Backups are not encrypted; control access and encrypt off-site copies separately.

For health failures inspect this project’s mysql/backend logs and private configuration. Fix network/package-source failures before rebuilding; do not skip tests. Override WEB_PORT rather than stopping other projects. For502 wait for backend health. Re-login after account changes or server restart. Version conflicts require refresh; unknown write results require verification before resubmission. Operators maintain HTTPS, backups, least privilege, monitoring and privacy notices. Login throttling is process-local and also needs proxy controls. No production-readiness or security certification claim.

## Licensing, contributions and contact

Personal learning, research and non-commercial exchange only. **Commercial use without written authorization from Shanghai Rujing Zhihua Information Technology Co., Ltd. is prohibited**, including paid deployment, customer delivery, SaaS operation and source resale. Preserve attribution, website and contact information; [LICENSE](LICENSE) governs own code. [Third-party notices](docs/THIRD_PARTY.md) and original dependency licenses remain independent.

Contributions should include sanitized reproduction and meaningful tests, following [AGENTS.md](AGENTS.md). Use repository Issues for ordinary bugs; disclose security problems privately through the website/email rather than publishing credentials or customer photos. Source is provided as-is; deployment risk and business-use review remain with the operator.

For source commercial licensing, private deployment, custom development, implementation, system integration, enterprise information systems, SME digital/AI transformation, software outsourcing, FDE support or OPC technical support, contact **ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)**:

- Website: [https://www.zhuatech.cn/](https://www.zhuatech.cn/)
- Email: [han@zhuatech.cn](mailto:han@zhuatech.cn)
- Email: [jack@zhuatech.cn](mailto:jack@zhuatech.cn)
- WhatsApp: [+86 17521234993](https://wa.me/8617521234993)
