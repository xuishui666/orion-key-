# zr68 宝塔部署与重建

本指南只针对 `xuishui666/orion-key-` 的 `orion-zr68` 实例，不适用于上游仓库或同服务器的其他项目。最后一次已验证的 API/Web 镜像标签是 `d16092a`；重建时可以固定使用该版本，也可以先在 GitHub Actions 确认新提交的 **Project checks** 和 **Orion Key CI/CD** 均成功，再同时替换两个镜像标签。推送代码只构建镜像，不会自动更新这台宝塔服务器。

## 1. 先决定是新装还是恢复

- **全新站点**：无旧订单和卡密，只需全新数据库、上传卷和密钥。执行第 2 至 6 节。
- **迁移/灾难恢复**：需要原数据库、上传文件、BEpusdt 数据和原 `.env`；先读第 7 节，并在第 3 节启动 API 前恢复数据。**不要**在有数据的站点上运行 `prepare.sh`、重建空卷或执行 `docker compose down -v`。
- **原机升级**：只执行第 8 节，保留数据库和卷。不要用仓库模板覆盖服务器上已定制的 `compose.yaml`。

原站仍可用时，先保留它运行，在新服务器完成恢复和测试后再切换 DNS。以下示例域名是 `zr68.manxi.cc`（站点）和 `usdtt.manxi.cc`（BEpusdt）；换域名时需同步修改 Nginx、邮件站点 URL、支付回调和 Telegram Webhook URL。

## 2. 服务器与端口

安装宝塔、Docker Engine、Docker Compose 插件、Git、OpenSSL 和 Nginx。至少保证 Docker 数据盘有足够空间容纳数据库、上传文件、BEpusdt 数据及备份。服务器时间同步，容器时区使用 `Asia/Shanghai`。

本项目固定使用 Compose 项目名 `orion-zr68`，本机端口 `13000`（Web）、`18083`（API）。下面的 BEpusdt 示例使用本机端口 `18881`。先检查端口占用；若被其他项目使用，只修改本项目的端口及对应反向代理，不停止或重配其他项目。

```bash
docker ps --format '{{.Names}} | {{.Ports}} | {{.Status}}'
ss -ltnp | grep -E ':(13000|18083|18881)[[:space:]]' || true
df -h / /var/lib/docker
```

只在**全新安装目录**执行以下命令。已有 `.env` 或 `orion-zr68` 容器时，应先走恢复/升级流程。

```bash
git clone --branch work/payment-admin-fixes-20260906 --single-branch https://github.com/xuishui666/orion-key-.git /root/orion-key-source
install -d -m 700 /www/wwwroot/orion-zr68
cp /root/orion-key-source/deploy/zr68/compose.yaml /root/orion-key-source/deploy/zr68/prepare.sh /www/wwwroot/orion-zr68/
cd /www/wwwroot/orion-zr68
bash prepare.sh
```

`prepare.sh` 只在确认目录全新时生成 `.env` 中的随机 `DB_PASSWORD` 和 `JWT_SECRET`，不会启动服务。`.env` 是机密文件，权限应为 `600`；不要提交 Git、粘贴到聊天或放在网站可访问目录。模板固定 `PASSWORD_PLAIN=false`。新服务器或回滚时保留相同密钥；恢复旧数据库时优先恢复旧 `.env`，不要运行 `prepare.sh` 生成新密码。

## 3. 数据库与应用

模板 `deploy/zr68/compose.yaml` 内置 PostgreSQL 16，使用 `orion-zr68_db_data` 卷；上传文件使用 `orion-zr68_uploads` 卷。两个卷不会因 `up -d` 重建容器而删除。不要运行带 `-v` 的 `down` 或 Docker 全局清理命令。

```bash
cd /www/wwwroot/orion-zr68
docker compose -p orion-zr68 -f compose.yaml config --quiet
docker compose -p orion-zr68 -f compose.yaml up -d db
docker inspect -f '{{.State.Health.Status}}' orion-zr68-db-1
```

等数据库状态为 `healthy`。如需恢复旧数据，此时按第 7 节恢复数据库和上传卷，**然后**再启动 API/Web。全新安装可直接执行：

```bash
docker compose -p orion-zr68 -f compose.yaml up -d --no-deps api web
docker ps --filter 'label=com.docker.compose.project=orion-zr68' --format '{{.Names}} | {{.Image}} | {{.Status}}'
docker logs --tail 80 orion-zr68-api-1
```

首次启动会自动建表。**不要运行旧版 `apps/api/src/main/resources/data.sql` 创建管理员。** 空用户库会自动创建初始管理员；登录后立即修改默认密码，见 [管理员初始化](../../ADMIN-BOOTSTRAP.md)。恢复旧数据库不会重置现有管理员密码。手动迁移旧版数据时，先核对 [软删除迁移](../../apps/api/migrations/20260906_soft_delete.sql) 是否已经应用；不要在生产库上盲目重复执行其他初始化 SQL。

## 4. 域名、HTTPS 与 BEpusdt

在 DNS 中把两个域名指向新服务器。宝塔分别创建站点并申请 HTTPS 证书：

| 域名 | 宝塔反向代理目标 | 用途 |
| --- | --- | --- |
| `zr68.manxi.cc` | `http://127.0.0.1:13000` | 商店、后台和 `/api/*`；Next.js 会转发 API |
| `usdtt.manxi.cc` | `http://127.0.0.1:18881` | BEpusdt 页面与接口 |

保留 Host、`X-Real-IP`、`X-Forwarded-For` 和 `X-Forwarded-Proto` 请求头，并开启 HTTP 到 HTTPS 跳转。API 的 `18083`、Web 的 `13000` 和 BEpusdt 的 `18881` 仅绑定 `127.0.0.1`，不要开放到公网。不要修改其他宝塔站点的 Nginx 配置。

BEpusdt 与 `orion-zr68` 分开运行，不能用根目录通用 `docker-compose.yml` 覆盖本项目。**仅全新安装**时，生成独立的私密配置；两个 Token 必须随机且互不相同，不要在截图、日志或 Git 中暴露真实值：

```bash
install -d -m 700 /www/wwwroot/orion-usdt
(
  umask 077
  printf 'TZ=Asia/Shanghai\nLOG=/var/lib/bepusdt/logs\nAPP_URI=https://usdtt.manxi.cc\nTRADE_IS_NOTIFY=1\nEXPIRE_TIME=900\n'
  printf 'AUTH_TOKEN=%s\n' "$(openssl rand -hex 32)"
  printf 'API_TOKEN=%s\n' "$(openssl rand -hex 32)"
) > /www/wwwroot/orion-usdt/.env
chmod 600 /www/wwwroot/orion-usdt/.env
```

确认代理端口未被占用，然后启动：

```bash
docker run -d --name orion-usdt-bepusdt --restart unless-stopped \
  -p 127.0.0.1:18881:8080 \
  -v orion_usdt_data:/var/lib/bepusdt \
  --env-file /www/wwwroot/orion-usdt/.env \
  v03413/bepusdt:latest
```

恢复旧 BEpusdt 时，不要创建空配置替代原 SQLite 数据；先还原原数据卷和凭据，再启动容器。原机现有 BEpusdt 卷名可能不同，用 `docker inspect orion-usdt-bepusdt` 查看真实挂载。将实际使用的镜像 digest、卷名和配置备份位置记入服务器私有运维记录。

登录 BEpusdt 后，在后台钱包管理分别配置自己的 TRC-20 与 BEP-20 **公开收款地址**；无需也绝不能录入私钥/助记词。区块节点配置中分别检查 Tron RPC 和 BSC RPC，BSC 需要能查询最新区块和历史 `eth_getLogs`；免费公共 RPC 可能返回 429/403，不能把“能打开网页”当成扫链可用。无 TronGrid API Key 时也要实测 TRC-20 扫链，不要假定必定成功。控制同步范围和频率，并监测 BEpusdt 的网络流量。

在发卡网后台的 **支付渠道** 分别配置 `usdt_trc20` 与 `usdt_bep20`：

- `api_url`：`https://usdtt.manxi.cc`；不要填 API 容器里的 `127.0.0.1`。
- `api_token`：必须与 BEpusdt 的 `API_TOKEN` 相同，值仅保存在私密配置中。
- `notify_url`：`https://zr68.manxi.cc/api/payments/webhook/usdt`。
- `trade_type`：分别为 `usdt.trc20`、`usdt.bep20`；法币、超时、汇率按实际业务配置。
- 支付宝/其他渠道使用其各自的接口与回调，不能复用 USDT 的 Token 或回调路径。

两条链都完成小额真实支付、回调、订单变为已支付及发货检查后再对用户开放。未验证成功前不要依赖人工把订单标为已支付来掩盖回调问题。

## 5. Telegram 客服与站点设置

在 Telegram 创建 Bot，先给它私聊发送 `/start`，取得**你自己的私聊 Chat ID**，不是 Bot ID。设置 Webhook 前可通过 Telegram `getUpdates` 查询；Webhook 启用后不要再用它判断历史消息是否存在。

在 `/www/wwwroot/orion-zr68/.env` 中加入以下四项（均为占位符）：

```dotenv
SUPPORT_TELEGRAM_BOT_TOKEN=<BotFather token>
SUPPORT_TELEGRAM_CHAT_ID=<your private chat id>
SUPPORT_TELEGRAM_WEBHOOK_SECRET=<openssl rand -hex 32>
SUPPORT_TELEGRAM_WEBHOOK_URL=https://zr68.manxi.cc/api/support/telegram/webhook
```

保存后保持文件 `600` 权限，只重建本项目 API：

```bash
cd /www/wwwroot/orion-zr68
chmod 600 .env
docker compose -p orion-zr68 -f compose.yaml config --quiet
docker compose -p orion-zr68 -f compose.yaml up -d --no-deps api
curl -fsS https://zr68.manxi.cc/api/support/config
```

返回的 `data.enabled` 应为 `true`。从前台发一条文字和一张测试图片，确认 TG 通知都带同一会话号；在 TG **直接回复对应通知**，检查消息回到正确访客会话。后台「站点设置 → 联系方式」可编辑客服欢迎语，留空关闭；欢迎语只在前台显示，不推送 TG。客服消息与图片默认在北京时间每天 03:15 清理 30 天前的数据，备份策略不要依赖它保存长期聊天历史。

## 6. 上线验证

```bash
curl -fsS --max-time 15 -o /dev/null -w 'SITE HTTP %{http_code}\n' https://zr68.manxi.cc/
curl -fsS --max-time 15 -o /dev/null -w 'API HTTP %{http_code}\n' https://zr68.manxi.cc/api/site/config
curl -fsS --max-time 15 https://zr68.manxi.cc/api/support/config
docker ps --filter 'label=com.docker.compose.project=orion-zr68' --format '{{.Names}} | {{.Image}} | {{.Status}}'
```

检查商品、登录后台、库存和已有订单。分别测试电脑/手机的支付页、刷新恢复、回调和发货；正式域名切换前，在预发布环境完成支付渠道小额实测。检查 Web、API、DB 与 BEpusdt 日志是否有启动失败、数据库连接失败或回调错误。`HTTP 200` 只证明基础可访问，不能代替真实支付测试。

## 7. 备份与恢复旧数据

在旧服务器**安排维护窗口**，记下当前 API/Web 镜像标签、BEpusdt 镜像 digest、Compose 配置和各卷名。为避免支付回调写入备份后的数据库，在最终备份前停止接单并确认待支付订单处理完毕。将备份复制到服务器外的加密存储，验证能读取；不要只留下同一磁盘上的一份。

数据库在线导出（备份目录不要位于网站根目录，文件权限由 `umask 077` 控制）：

```bash
umask 077
install -d -m 700 /root/orion-zr68-backups
docker exec orion-zr68-db-1 sh -lc 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > /root/orion-zr68-backups/orion-db.dump
test -s /root/orion-zr68-backups/orion-db.dump
cp -p /www/wwwroot/orion-zr68/compose.yaml /www/wwwroot/orion-zr68/.env /root/orion-zr68-backups/
```

备份上传卷；确认已在维护窗口停止新增上传。BEpusdt 的 SQLite 卷要在**停掉 BEpusdt 容器本身**后备份，备份后立即启动它；不要停止其他项目。先拉取 `alpine:3.20`，避免支付服务停止期间等待下载镜像：

```bash
docker pull alpine:3.20
docker run --rm -v orion-zr68_uploads:/data:ro -v /root/orion-zr68-backups:/backup alpine:3.20 tar -C /data -czf /backup/uploads.tgz .
BE_VOLUME=$(docker inspect -f '{{range .Mounts}}{{if eq .Destination "/var/lib/bepusdt"}}{{.Name}}{{end}}{{end}}' orion-usdt-bepusdt)
test -n "$BE_VOLUME"
(
  set -e
  docker stop orion-usdt-bepusdt
  trap 'docker start orion-usdt-bepusdt' EXIT
  docker run --rm -v "$BE_VOLUME:/data:ro" -v /root/orion-zr68-backups:/backup alpine:3.20 tar -C /data -czf /backup/bepusdt.tgz .
)
if [ -f /www/wwwroot/orion-usdt/.env ]; then cp -p /www/wwwroot/orion-usdt/.env /root/orion-zr68-backups/bepusdt.env; fi
```

确认数据库 dump 和两个归档文件非空。旧部署若没有 BEpusdt `.env`，先从原机私密配置中找回 `API_TOKEN` 等凭据，再继续迁移；不要通过公开日志或聊天传输。若使用宝塔/云主机快照，也要确认快照包含 Docker 卷和私密 `.env`。交易中的订单需要在新站恢复后复核，不要仅凭文件备份推断已到账。

恢复到**新建的空数据库卷**时，先复制原 `.env` 与 `compose.yaml` 到新目录，确认数据库密码和镜像标签，再只启动 DB、等待 `healthy`。原 `.env` 中的域名和 Webhook URL 需要随新域名调整。将 dump 放在当前目录后执行：

```bash
cd /www/wwwroot/orion-zr68
docker compose -p orion-zr68 -f compose.yaml up -d db
docker inspect -f '{{.State.Health.Status}}' orion-zr68-db-1
docker compose -p orion-zr68 -f compose.yaml exec -T db sh -lc 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --no-acl' < /root/orion-zr68-backups/orion-db.dump
```

确认数据库已健康且**目标库为空**再执行恢复；如 `pg_restore` 报错，停止，不要继续启动 API 覆盖现场。上传卷和 BEpusdt 卷还原到各自的**新空卷**，在启动 API/BEpusdt 前执行：

```bash
docker volume create orion-zr68_uploads
docker volume create orion_usdt_data
docker run --rm -v orion-zr68_uploads:/data -v /root/orion-zr68-backups:/backup:ro alpine:3.20 tar -C /data -xzf /backup/uploads.tgz
docker run --rm -v orion_usdt_data:/data -v /root/orion-zr68-backups:/backup:ro alpine:3.20 tar -C /data -xzf /backup/bepusdt.tgz
```

将 BEpusdt 原 `.env` 恢复到 `/www/wwwroot/orion-usdt/.env` 并保持 `600` 权限；若更换域名，仅调整 `APP_URI`，不要随意更换原 `API_TOKEN`。核对文件数和挂载目标后，按第 3、4 节启动应用与 BEpusdt。若只迁移数据库而遗漏上传卷，商品图片等文件会丢失；若遗漏 BEpusdt 卷或 Token，历史链上订单、回调关联可能失效。

## 8. 原机升级与回滚

原机升级只替换 `orion-zr68` 的 API/Web 标签，**不要**在服务器运行 `docker system prune`、`docker compose down -v` 或清理其他项目的镜像/卷。先备份第 7 节所列数据与现用 `compose.yaml`，在 GitHub Actions 确认目标提交的测试和镜像构建成功，再拉取目标固定标签。

```bash
cd /www/wwwroot/orion-zr68
docker compose -p orion-zr68 -f compose.yaml config --images
docker pull ghcr.io/xuishui666/orion-key-api:<NEW_TAG>
docker pull ghcr.io/xuishui666/orion-key-web:<NEW_TAG>
```

把**当前服务器**的 `compose.yaml` 中两处镜像标签精确改为同一个 `<NEW_TAG>`，不要覆盖整份配置或 `.env`。然后：

```bash
docker compose -p orion-zr68 -f compose.yaml config --quiet
docker compose -p orion-zr68 -f compose.yaml up -d --no-deps api web
```

等 API 完全启动再执行第 6 节验证。若新版本异常，从升级前的 `compose.yaml` 备份恢复旧标签，再用同一条 `up -d --no-deps api web` 重建；**不要回滚或删除数据库卷**。数据库结构变更可能不可逆，涉及迁移时必须先验证旧镜像兼容性，并保留可恢复的数据库备份。升级本身不应改变管理员密码、商品、订单、卡密或支付渠道配置。
