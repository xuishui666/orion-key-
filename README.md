# Orion Key：游客下单与 Telegram 客服版

基于 [RivenLau/orion-key](https://github.com/RivenLau/orion-key) 二次开发的数字商品自动发卡系统。本仓库当前面向游客购买，不提供普通用户注册或登录；保留管理员登录、订单管理、支付、公告和双向 Telegram 客服。

## 从这里开始

**实际开发与构建分支是 [`work/payment-admin-fixes-20260906`](https://github.com/xuishui666/orion-key-/tree/work/payment-admin-fixes-20260906)。仓库默认 `main` 分支尚未同步业务代码，首页 README 已同步当前说明；不要直接使用 `main` 的源码或部署模板重建当前站点。**

| 项目 | 当前说明 |
| --- | --- |
| 本 fork | `xuishui666/orion-key-`，仓库名末尾有 `-` |
| 后端镜像 | `ghcr.io/xuishui666/orion-key-api:<固定标签>` |
| 前端镜像 | `ghcr.io/xuishui666/orion-key-web:<固定标签>` |
| 宝塔 Compose 项目名 | `orion-zr68` |
| 部署目录 | `/www/wwwroot/orion-zr68` |
| 完整安装、恢复和升级 | [宝塔部署与重建指南](https://github.com/xuishui666/orion-key-/blob/work/payment-admin-fixes-20260906/deploy/zr68/README.md) |
| 管理员初始化 | [ADMIN-BOOTSTRAP.md](https://github.com/xuishui666/orion-key-/blob/work/payment-admin-fixes-20260906/ADMIN-BOOTSTRAP.md) |

### 版本与上线状态

以下为 **2026-11-07 文档更新时**已核对的状态，不是自动读取的在线状态：

| 状态 | 版本 | 内容 |
| --- | --- | --- |
| 已确认部署的 API | `38c3413` | 订单凭证校验、库存事务回滚、并发状态保护、客服会话删除 |
| 已确认部署的 Web | `38c3413` | 查询密码、凭证传递、客服会话删除确认 |
| 自动检查 | `38c3413` | 63 项后端测试、7 项 PostgreSQL 专项测试、前端构建及镜像发布通过 |

**站点维护者已提供宝塔部署结果：API/Web 均为 `38c3413`，API 就绪检查通过，客服配置返回 `enabled=true`，数据库 healthy。** 此记录仅确认部署与基础可用性，尚未完成本次线上真实付款及页面操作验收。仅后端修复通常可以单独升级 API，但本次订单凭证升级必须同时更新 API/Web。重建此版本可固定使用两端 `38c3413` 镜像。

## 已实现功能

- 游客直接购买、购物车结算、订单查询和付款后自动发卡。
- 支付宝/微信通过易支付接入；USDT TRC-20/BEP-20 通过独立 BEpusdt 网关接入。实际可用方式取决于后台已配置并启用的渠道。
- 分类、商品、库存和卡密管理；订单删除/批量删除；按日期、支付渠道和商品查看销售统计。
- 管理员专用登录与个人资料/密码修改；普通用户注册、登录、会员订单页、用户管理入口及积分设置已移除或停用。历史数据库结构并不因此全部删除。
- 首页公告栏：后台编辑公告标题、正文和样式，支持换行；不再使用旧网站标语和描述作为首页内容。
- 前台“联系客服”入口；客服欢迎语可以在后台网站设置中修改。
- 客户端与 Telegram 双向文字、图片通信；图片支持 JPG、PNG、WebP，单张不超过 3 MB。
- Telegram 通知包含会话编号，并关联同一会话的上一条通知。客服在 TG 中使用“回复”指定通知，系统根据消息关联找到客户；单独给机器人发送新消息不会自动指定客户。
- 管理后台客服会话查看及回复、风控管理、TXID 审核、操作日志。

### 订单安全与会话删除（本次升级）

新订单必须设置查询密码。同一浏览器可使用自动保存的随机订单凭证；跨浏览器需提供订单号或邮箱及匹配的查询密码，不能只凭邮箱读取卡密。查询密码以 BCrypt 保存，随机凭证只保存 SHA-256 摘要。旧订单只能凭原浏览器的非空游客会话查询，丢失原会话需联系客服核验。

多商品发货中任一商品缺货会回滚本次全部分配，补货后可重试；订单版本检查阻止旧支付请求覆盖更新后的发货状态。后台客服会话新增单会话删除与二次确认，同时删除站内消息和图片，保留 TG 历史。具体兼容性、数据库字段及验证方法见 [订单安全升级说明](https://github.com/xuishui666/orion-key-/blob/work/payment-admin-fixes-20260906/ORDER-SECURITY-UPGRADE.md)。

**这次升级必须同时更新 API 和 Web**，旧前端不能创建带新凭证的订单。本次已确认上线的固定标签为 `38c3413`。

### 客服安全规则（从 `f396f3f` 起）

客户首条消息、后续文字及会话附带的联系字段会检查常见 JNDI/LDAP/RMI、脚本注入、模板注入、SQL 注入、路径穿越和命令执行载荷。命中后拒绝提交，不写入客服记录、不转发 TG；首条命中时不创建会话。

服务端日志只记录拦截类别、会话标识和长度，不打印被拦截的正文。正常中文、英文、订单号、邮箱、网址及图片不按语言直接拒绝；管理员回复不受客户文本过滤规则约束。

创建会话与后续发送分别使用设备限流桶：每小时最多 10 次创建；后续文字和图片合计每小时最多 10 次发送。没有有效设备标识时使用来源 IP；另保留 IP 级保护。限流为进程内计数，API 重启后重置。这些规则用于减少常见探测和骚扰，不能替代依赖更新、权限控制及安全审计。

### 自动清理与历史数据

| 数据 | 执行时间（北京时间） | 实际规则 |
| --- | --- | --- |
| 客服消息和图片 | 每日 03:15 | 彻底删除超过 30 天的消息及其图片；删除已无消息且超过 30 天未活动的会话 |
| 已完成订单 | 每日 03:30 | 清理 `DELIVERED` 且付款时间超过 30 天的订单；仅 `PAID` 尚未发货的订单不在此规则内 |
| 未支付订单 | 每日 03:30 | 清理当天零点之前创建、已经超时的 `PENDING` / `EXPIRED` 订单，包含更早的历史积压 |

订单清理会处理关联明细及卡密等关系；待人工审核的相关订单有保留条件。已用 USDT TXID 会保留最小防重放标记，不是清除所有交易标识。彻底删除后不能再查询原订单，历史销售统计也会受到影响；需要长期留存的报表应另行保存。清理站内客服记录不会删除 Telegram 里的历史消息。

## 技术与运行环境

| 组件 | 当前代码/部署 |
| --- | --- |
| 后端 | Java 22 构建、Spring Boot 3.4.1、Maven、Spring Data JPA/Security |
| 前端 | Next.js 16、React 19、TypeScript、Tailwind CSS、pnpm |
| 数据库 | 当前宝塔模板为 PostgreSQL 16 Alpine；不要套用上游 PostgreSQL 18+ 的旧说明升级现有数据卷 |
| 认证 | 管理员 JWT + BCrypt；生产设置 `PASSWORD_PLAIN=false` |
| 支付网关 | BEpusdt 独立容器与独立数据卷 |
| 构建发布 | GitHub Actions → GHCR；宝塔按固定标签更新 |

低配置服务器应使用 `deploy/zr68/compose.yaml` 的资源限制作为起点，并按实际内存和并发监测。该模板配置约束了 API、Web、数据库的内存与线程；容器能启动不等于适合任何并发量。购买商品时不需要单独运行 Chatwoot 等重型客服服务。

## 全新搭建步骤

详细命令见[重建指南](https://github.com/xuishui666/orion-key-/blob/work/payment-admin-fixes-20260906/deploy/zr68/README.md)。下面是当前版本必须满足的顺序。

1. 安装 Docker Engine、Docker Compose 插件和 Nginx/宝塔；准备域名与 HTTPS。检查端口、磁盘和内存。
2. 从实际开发分支获取源码：

   ```bash
   git clone --branch work/payment-admin-fixes-20260906 --single-branch https://github.com/xuishui666/orion-key-.git /root/orion-key-source
   ```

3. 在全新部署目录复制 `deploy/zr68/compose.yaml` 和 `prepare.sh`。仅全新安装运行 `prepare.sh` 生成数据库密码和 JWT 密钥。已有站点或恢复数据时使用原 `.env` 和数据卷。
4. 模板仍有历史固定标签，启动前将 API/Web 标签分别改为已经验证的目标版本，本次订单权限升级要求 API/Web 使用本次检查通过的同一标签。不要拉取 `rivenlau` 的上游镜像替代本 fork。
5. 新库需要在私密 `.env` 设置 `ADMIN_BOOTSTRAP_PASSWORD`；同时在 API 的 `environment` 显式增加：

   ```yaml
   ADMIN_BOOTSTRAP_PASSWORD: ${ADMIN_BOOTSTRAP_PASSWORD:-}
   ```

   模板已设置 `PASSWORD_PLAIN: "false"`。只把变量写入 `.env` 不会自动把它传给这种显式环境变量形式的 API 服务。首次启动创建用户名 `admin`，密码是自行设置的值，没有通用默认密码。已有任何用户记录时不会重建或提升账户权限。
6. 先启动数据库并等待 `healthy`，再启动 API/Web。首次启动后移除 `.env` 中的初始化密码并重建 API；保留管理员登录密码在自己的密码管理器中。不要通过旧 `data.sql` 创建管理员。
7. 配置 HTTPS 反向代理：商城 → `127.0.0.1:13000`；API 由 Web 转发，API 本机端口为 `18083`。BEpusdt 独立部署，示例端口 `18881`。这些服务端口不直接向公网开放。
8. 打开 `/login` 登录管理员，后台位于 `/admin/dashboard`。配置商品、卡密、公告、支付渠道及客服，完成验证后再开放购买。

根目录 `docker-compose.yml` 是通用编排，需自行提供数据库并设置镜像等参数；它不是已有 `orion-zr68/compose.yaml` 的替代文件。

## Telegram 客服配置

通过 BotFather 创建机器人，先给机器人私聊发送 `/start`。需要的是**管理员本人私聊的 Chat ID**，不是机器人 ID。Token 只保存在服务器私密配置中。

```dotenv
SUPPORT_TELEGRAM_BOT_TOKEN=<BotFather 提供的 Token>
SUPPORT_TELEGRAM_CHAT_ID=<管理员私聊 Chat ID>
SUPPORT_TELEGRAM_WEBHOOK_SECRET=<自行生成的随机字符串>
SUPPORT_TELEGRAM_WEBHOOK_URL=https://你的商城域名/api/support/telegram/webhook
```

API Compose 环境中必须传递上述四个变量；当前宝塔模板已包含这些映射。修改 `.env` 后通过 `up -d --no-deps api` 重建容器使环境生效，仅 `docker restart` 不会重新读取 `.env`。应用会注册 Webhook；HTTPS 地址必须能被 Telegram 访问。

启用后检查 `/api/support/config` 返回 `data.enabled=true`。它仅表示配置齐全，还应检查 Telegram `getWebhookInfo`，并实测网页发消息、TG 收到通知、回复指定通知后网页收到文字/图片。不要把 Token 或完整 `.env` 输出到公开日志。Webhook 已启用时不要再通过 `getUpdates` 排查私聊消息。

## 支付配置

- 易支付渠道：在后台填写对应服务商地址、商户与签名配置；分别验证创建支付和签名回调。
- USDT：独立部署 BEpusdt，配置自己的 TRC-20/BEP-20 公开收款地址，保存原 SQLite 数据卷及 API 凭据。
- Orion 的 USDT 渠道 Token 必须与 BEpusdt 一致，回调地址为 `https://你的商城域名/api/payments/webhook/usdt`。
- RPC 节点要实测扫链所需请求；BSC 需支持最新区块和历史 `eth_getLogs`。限额、429/403、超时会影响识别和流量，换一个 RPC 地址并不保证降低流量。
- 两条链分别实测到账、回调、订单状态和发货；网页“成功”与钱包到账应独立核对。无需向系统提供钱包私钥或助记词。

## 更新、恢复与排查

更新顺序：提交实际开发分支 → 等待 **Project checks** 与 **Orion Key CI/CD** 成功 → 拉取目标固定标签 → 修改本项目现有 Compose 对应服务的标签 → 只重建有变化的服务 → 验证。API 启动可能需要约一分钟，启动期间的 502/500 不应立即当作最终结果；持续失败要检查 API 日志。

构建镜像不会自动部署到宝塔。文档提交也可能触发现有构建工作流，不表示业务代码变化或线上已切换。仅更新 API 时不必一起重建 Web 或数据库。

日常核验命令（只读）：

```bash
docker compose -p orion-zr68 -f /www/wwwroot/orion-zr68/compose.yaml config --images
docker ps --filter 'label=com.docker.compose.project=orion-zr68' --format '{{.Names}} | {{.Image}} | {{.Status}}'
curl -fsS --max-time 15 https://你的商城域名/api/site/config
curl -fsS --max-time 15 https://你的商城域名/api/support/config
```

恢复需要原 PostgreSQL 数据库备份、商品上传卷、私密 `.env` / Compose 和独立 BEpusdt 数据/凭据。缺少这些数据时，重新拉镜像不会恢复原商品、卡密、订单或钱包配置。已被定时任务彻底清理的数据只能从仍保留这些记录的备份恢复。

升级后只按精确标签清理本项目已停用、且没有容器引用的旧 API/Web 镜像。不要执行 Docker 全局清理或删除数据库/上传卷。删除旧镜像后，如需回滚必须重新拉取对应固定标签；数据库迁移兼容性需要单独核对。

## 开发与检查

源码使用上面的实际开发分支。开发机准备 Java 22、Maven 3.9+、Node.js 22、pnpm 9+ 和 PostgreSQL。数据库、JWT、邮件、上传目录等配置见 `application.yml` 及 `.env.example`；生产密码与密钥应独立设置。

```bash
# 仓库根目录：安装前端依赖并检查构建
pnpm install --frozen-lockfile
pnpm build:web

# 后端完整测试
cd apps/api
mvn -B test
```

本地运行后端用 `mvn spring-boot:run`，另开终端在仓库根目录运行 `pnpm dev:web`。后端端口为 `8083`，API 前缀 `/api`；前端通过代理访问后端。使用空数据库开发也必须按管理员初始化步骤设置密码。

## License

[MIT](https://github.com/xuishui666/orion-key-/blob/work/payment-admin-fixes-20260906/LICENSE)。原项目版权及许可声明见 LICENSE。
