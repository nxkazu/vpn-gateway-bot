# Tessera VPN — Telegram Bot

Профессиональный Telegram-бот для VPN-сервиса **Tessera**, построенный поверх
[Remnawave](https://docs.rw) API. Java 21, Gradle, Docker. Включает
пользовательский интерфейс (подписки, пробный период, тарифы), реферальную
систему и полноценную админ-панель прямо в Telegram.

## Возможности

### Для пользователей
- 🎁 Пробный период (настраиваемый) для новых пользователей
- 🛒 Покупка и продление тарифов
- 🛡 VPN Hub: статус подписки, срок, трафик, остаток, health-рекомендации
- 🧭 Гайды по настройке для iOS, Android, Windows, macOS и Android TV
- 🔗 Прямая ссылка на конфигурацию подписки
- 👥 Реферальная программа с бонусными днями за приглашённых друзей
- 🌐 Мультиязычный интерфейс: выбор языка в меню и при первом `/start`

### Локализация (i18n)

Интерфейс переведён на 6 языков: 🇬🇧 English, 🇷🇺 Русский, 🇪🇸 Español,
🇩🇪 Deutsch, 🇫🇷 Français, 🇨🇳 中文. Язык выбирается пользователем в меню
(«🌐 Язык») и сохраняется в БД; по умолчанию берётся из языка Telegram-клиента,
иначе — английский. Строки хранятся в `tessera-i18n/src/main/resources/i18n/messages[_xx].properties`
(английский — базовый бандл и фолбэк для любого отсутствующего ключа).
Как добавить язык — см. [CONTRIBUTING.md](CONTRIBUTING.md).

### Для администраторов (`/admin`)
- 📊 Статистика панели (пользователи по статусам, онлайн)
- ❤️ Проверка здоровья системы
- 🖥 Управление нодами (список, статус, перезапуск)
- 🔍 Поиск пользователя по username / Telegram ID / UUID
- 👤 Действия над пользователем: включить / отключить / отозвать подписку /
  сбросить трафик / удалить
- 📢 Массовая рассылка всем пользователям бота

## Архитектура

```
tessera-telegram  ─┐
tessera-ops       ─┤
                   ├─> tessera-services ─┬─> tessera-experience
tessera-modules   ─┘                     ├─> tessera-db
                                         ├─> tessera-remnawave
                                         ├─> tessera-core
                                         └─> tessera-common/i18n

tessera-app wires everything together and starts Telegram long polling.
tessera-payment and tessera-dashboard are runtime plugin JARs loaded from modules/.
```

Слои разделены по ответственности: **tessera-telegram** (I/O Telegram) →
**tessera-ops** (health, metrics, webhooks, backups, alerts) и
**tessera-services** (use-cases, бизнес-правила и продуктовая аналитика) →
**tessera-experience** (VPN Hub, setup-guides, health-инсайты) +
**tessera-remnawave** (внешний API) + **tessera-db** (собственное состояние:
рефералы, привязки, платежи, тарифы, Pulse-события). VPN-аккаунт «источника
истины» живёт в панели Remnawave; локальная БД хранит только то, чего в панели нет.

## Быстрый старт (Docker)

```bash
git clone https://github.com/nxkazu/vpn-gateway-bot.git
cd vpn-gateway-bot
cp .env.example .env
# отредактируйте .env: BOT_TOKEN, BOT_USERNAME, ADMIN_IDS,
# REMNAWAVE_BASE_URL, REMNAWAVE_TOKEN, DB_PASSWORD ...
docker compose up -d --build
```

Бот и PostgreSQL поднимутся в одной сети; миграции Flyway применяются
автоматически при старте.

## Локальный запуск

```bash
cp .env.example .env   # заполните значения
./scripts/run-local.sh
```

## Конфигурация

Все настройки — через переменные окружения (см. `.env.example`). Ключевые:

| Переменная | Назначение |
|---|---|
| `BOT_TOKEN` | токен бота от @BotFather |
| `BOT_USERNAME` | username бота (для реферальных ссылок) |
| `ADMIN_IDS` | Telegram ID администраторов через запятую |
| `REMNAWAVE_BASE_URL` | базовый URL панели Remnawave |
| `REMNAWAVE_TOKEN` | API-токен панели |
| `REMNAWAVE_DEFAULT_SQUAD_UUID` | сквад по умолчанию для новых юзеров |
| `TRIAL_DAYS` / `TRIAL_TRAFFIC_GB` | параметры пробного периода |
| `REFERRAL_REWARD_DAYS` | бонус рефереру за активацию приглашённого |

## Получение API-токена Remnawave

В панели: раздел **API Tokens** → создать токен, либо через API
`POST /api/tokens` (`{ "name": "...", "expiresInDays": 365, "scopes": [...] }`).
Полученный `token` положите в `REMNAWAVE_TOKEN`.

## Интеграция платежей

`tessera-payment` регистрирует провайдеры оплаты через SPI `PaymentProvider`.
Пользователь выбирает тариф → способ оплаты → получает счёт → после подтверждения
платежа активируется подписка в Remnawave и пишется ledger-запись в `payments`.
Для локального/dev-режима можно явно включить прямую активацию без оплаты через
`ALLOW_FREE_ACTIVATION=true`.

## Лицензия

MIT — см. [LICENSE](LICENSE).
## Модули (плагины)

Начиная с v1.1 бот собран как мульти-модульный Gradle-проект, а функциональность
подключается **плагинами** — отдельными JAR-файлами в каталоге `modules/`
(переменная `MODULES_DIR`). Каждый runtime-модуль реализует SPI `BotModule` из `tessera-core`
и обнаруживается через `ServiceLoader` в собственном `URLClassLoader`.

```
tessera-vpn-bot
├── tessera-core/        # SPI: BotModule, ModuleContext, PaymentProvider, ...
├── tessera-config/      # env-конфигурация приложения
├── tessera-i18n/        # Lang, Messages и i18n resource bundles
├── tessera-common/      # shared presentation helpers
├── tessera-experience/  # VPN Hub insights, traffic health, setup guides
├── tessera-db/          # HikariCP, Flyway, repositories, DB-backed plan catalogue
├── tessera-remnawave/   # typed Remnawave API client + DTO
├── tessera-services/    # business use-cases: subscriptions, payments, referrals, support
├── tessera-ops/         # health, metrics, payment webhooks, backups, alerts
├── tessera-modules/     # runtime module loader + module context bridge
├── tessera-telegram/    # Telegram adapter: bot router, handlers, keyboards
├── tessera-app/         # тонкий bootstrap (tessera-vpn-bot.jar)
├── tessera-payment/     # tessera-payment.jar — платёжные шлюзы CryptoBot и Platega
└── tessera-dashboard/   # tessera-dashboard.jar — веб-дашборд поддержки/тарифов
```

Внутри это **modular monolith**: модули общаются через Java-интерфейсы и явные
Gradle-зависимости, а внешняя расширяемость остаётся через runtime-плагины в `modules/`.
Такую структуру проще тестировать сейчас и при необходимости раскалывать на реальные
микросервисы позже.

### tessera-payment.jar

Платёжный модуль регистрирует два merchant-гейта:

| Гейт | Что это | Подтверждение |
|---|---|---|
| **CryptoBot** | Crypto Pay API (`pay.crypt.bot`), оплата криптой (USDT/TON/...) в счёте на фиатную сумму | поллинг `getInvoices` по кнопке «Я оплатил» |
| **Platega** | СБП / карты (`app.platega.io`, метод настраивается) | поллинг `GET /transaction/{id}` |

Флоу: тариф → выбор способа оплаты → счёт с кнопкой «Оплатить» → «Я оплатил» →
проверка статуса у провайдера → активация тарифа в Remnawave + запись в `payments`
+ реферальный бонус. Счета хранятся в таблице `payment_invoices` (Flyway `V4`).

Конфигурация — `config/payments.yml` (генерируется автоматически)
либо переменные окружения `CRYPTOBOT_TOKEN`, `PLATEGA_MERCHANT_ID`, `PLATEGA_SECRET`
(env переопределяет файл). Если ни один гейт не настроен, покупка тарифа блокируется;
активацию без оплаты можно включить только явно через `ALLOW_FREE_ACTIVATION=true`
для локального/dev-окружения.

### Свой модуль

1. Создайте Gradle-подпроект с `compileOnly(project(":tessera-core"))`.
2. Реализуйте `com.tessera.core.api.BotModule` и зарегистрируйте класс в
   `META-INF/services/com.tessera.core.api.BotModule`.
3. Соберите shadow-jar и положите его в `modules/` — ядро подхватит его при старте.
## Админ-дашборд (tessera-dashboard.jar)

Модуль `tessera-dashboard` поднимает встроенный веб-дашборд (JDK HttpServer, без внешних
зависимостей) на порту `8080` (наружу — `DASHBOARD_PORT`). Включается заданием
`DASHBOARD_TOKEN` в `.env` — этим же токеном выполняется вход в UI.

Возможности:
- **Поддержка**: пользователь жмёт «🆘 Поддержка» в боте и пишет сообщение — создаётся
  тикет (`support_tickets` / `support_messages`). Админ видит тикеты в дашборде,
  отвечает — ответ уходит клиенту в Telegram. Тикет можно закрыть.
- **Тарифы**: тарифы вынесены из кода в таблицу `plans` (Flyway `V5`, старые тарифы
  засеяны автоматически). В дашборде можно создавать/менять/выключать тарифы —
  бот подхватывает изменения сразу, без рестарта.
- **Статистика**: пользователи, открытые тикеты, оплаты и выручка за 30 дней.
- **Tessera Pulse**: продуктовая воронка и события за 30 дней: старты, VPN Hub,
  setup-гайды, выбор тарифов, счета, оплаты и обращения в поддержку.

⚠️ Дашборд защищён только bearer-токеном — не публикуйте порт в интернет без
reverse-proxy с TLS (например, за nginx/caddy) или ограничьте доступ фаерволом.
## Update 3 — promo codes, auto-payment checks, expiry notices, ticket attachments, charts

- **Platega provider verified against the official docs** (docs.platega.io): the transaction
  id is now taken from the API response (`transactionId`) instead of being generated
  client-side, anti-fraud `metadata` (`userId` = Telegram id) and the required `payload`
  field are sent, and the status mapping follows the documented enum
  (`PENDING / CANCELED / CONFIRMED / CHARGEBACKED`). Unknown statuses are treated as
  pending — a payment is never activated by guesswork.
- **Automatic payment confirmation**: a background watcher re-checks PENDING invoices
  every `PAYMENT_CHECK_SECONDS` (default 60 s) so subscriptions activate without the
  user pressing “I paid”. The user gets a localized confirmation message.
- **Expiry notifications**: subscriptions expiring within `EXPIRY_NOTIFY_DAYS`
  (default 3) trigger a one-time localized warning, re-armed automatically when the
  subscription is extended (dedup via the `expiry_notices` table).
- **Promo codes**: created/managed in the dashboard (🎟 tab). A code has a percent
  discount, optional max-use count and expiry date; one use per user, counted only when
  the discounted invoice is actually paid. In the bot the user taps “🎟 Promo code” on
  the payment-method screen and types the code.
- **Support upgrades**: photos and documents can be attached to tickets (rendered in
  the dashboard chat via a Telegram file proxy); while a ticket is open, any plain
  message from the user continues the conversation; admins are notified in Telegram
  about new tickets and new user messages.
- **Dashboard charts**: daily revenue and daily new users for the last 30 days on the
  stats tab.
- **i18n**: payment, support and promo strings translated for all six languages
  (en, ru, de, es, fr, zh).
- **Tests**: `PromoServiceTest` (validation + discount math) and `PlategaProviderTest`
  (status mapping).

> ⚠️ Security note: the dashboard file proxy accepts the access token as a `t=` query
> parameter for embedding attachment images. Keep the dashboard behind a reverse proxy
> with TLS and restrict access at the network level.

## Update 4 — Tessera Experience & Pulse

- **VPN Hub**: карточка подписки стала продуктовым центром: статус, срок, дни до
  окончания, использованный/оставшийся трафик, usage bar и рекомендация
  (всё хорошо, пора продлить, трафик почти закончился).
- **Setup guides**: отдельный пользовательский раздел с гайдами для iOS, Android,
  Windows, macOS и Android TV. Если подписка есть, рядом показывается кнопка
  открытия subscription URL.
- **Новый модуль `tessera-experience`**: расчёты health/usage и setup-каталог вынесены
  из Telegram-слоя в отдельную библиотеку с тестами.
- **Tessera Pulse**: добавлена таблица `user_events` (Flyway `V7`) и best-effort
  `AnalyticsService`; события не ломают пользовательские сценарии, даже если
  аналитика временно недоступна.
- **Dashboard Pulse**: на вкладке статистики появился блок продуктовой воронки
  за 30 дней и таблица top events.
- **Payment analytics**: создание счетов и подтверждённые оплаты пишутся из
  `PaymentService`, поэтому в Pulse попадают и ручные проверки, и фоновые
  auto-payment confirmations.
- **Tests**: `ExperienceServiceTest` покрывает расчёт дней, usage bar, high-traffic
  warning и unlimited-трафик.

## Update 5 — Tessera Ops

- **Monitoring & alerts**: встроенный ops API (`OPS_ENABLED=true`) на `OPS_PORT`:
  - `GET /health` — health report по DB, Remnawave и payment providers;
  - `GET /metrics` — простые Prometheus-style метрики, требует `OPS_TOKEN`;
  - при смене состояния health админы получают Telegram-alert.
- **Payment webhooks**: `POST /webhooks/payments/{provider}` или
  `POST /webhooks/payments/{provider}/{externalId}`. Endpoint требует
  `PAYMENT_WEBHOOK_SECRET` (`Authorization: Bearer ...`, `X-Tessera-Webhook-Secret`
  или `?secret=...`). Webhook не доверяет входящему статусу: он только находит invoice,
  затем вызывает `PaymentProvider.checkInvoice(...)`.
- **Postgres backups**: runtime Docker image теперь содержит `pg_dump`; ops API умеет
  `POST /ops/backups/run`, а `BACKUP_SCHEDULE_HOURS` включает регулярные `.dump`
  backups с retention через `BACKUP_RETENTION_DAYS`.
- **RBAC для админов**: кроме старого `ADMIN_IDS` появились роли:
  `ADMIN_SUPPORT_IDS`, `ADMIN_BILLING_IDS`, `ADMIN_OPS_IDS`,
  `ADMIN_SUPERADMIN_IDS` и матрица `ADMIN_ROLES=111:support,billing;222:ops`.
  `ADMIN_IDS` по-прежнему даёт полный superadmin-доступ.
- **Anti-abuse**: in-memory rate limits для сообщений, callback’ов, создания счетов
  и support-сообщений. Настройки: `RATE_LIMIT_MESSAGES_PER_MINUTE`,
  `RATE_LIMIT_CALLBACKS_PER_MINUTE`, `RATE_LIMIT_INVOICES_PER_HOUR`,
  `RATE_LIMIT_SUPPORT_PER_HOUR`.
- **Plugin API versioning**: `BotModule.API_VERSION = 2`, runtime-модули должны
  явно возвращать совместимую версию. Несовместимые jar пропускаются, escape hatch:
  `MODULE_ALLOW_UNSUPPORTED_API=true`.

### Ops / backup restore

Ручной backup:

```bash
curl -X POST http://127.0.0.1:9090/ops/backups/run \
  -H "Authorization: Bearer $OPS_TOKEN"
```

Restore `.dump`-файла:

```bash
pg_restore --clean --if-exists \
  --host "$DB_HOST" --port "$DB_PORT" --username "$DB_USER" \
  --dbname "$DB_NAME" /app/backups/tessera_YYYYMMDD_HHMMSS.dump
```

Ops port в `docker-compose.yml` привязан к `127.0.0.1`. Для публичных webhooks
выведите только `/webhooks/payments/...` через TLS reverse proxy и оставьте
`/metrics` / `/ops/backups/run` закрытыми.
