# Tessera VPN — Telegram Bot

Профессиональный Telegram-бот для VPN-сервиса **Tessera**, построенный поверх
[Remnawave](https://docs.rw) API. Java 21, Gradle, Docker. Включает
пользовательский интерфейс (подписки, пробный период, тарифы), реферальную
систему и полноценную админ-панель прямо в Telegram.

## Возможности

### Для пользователей
- 🎁 Пробный период (настраиваемый) для новых пользователей
- 🛒 Покупка и продление тарифов
- 📱 Просмотр статуса подписки, срока действия и трафика
- 🔗 Прямая ссылка на конфигурацию подписки
- 👥 Реферальная программа с бонусными днями за приглашённых друзей
- 🌐 Мультиязычный интерфейс: выбор языка в меню и при первом `/start`

### Локализация (i18n)

Интерфейс переведён на 6 языков: 🇬🇧 English, 🇷🇺 Русский, 🇪🇸 Español,
🇩🇪 Deutsch, 🇫🇷 Français, 🇨🇳 中文. Язык выбирается пользователем в меню
(«🌐 Язык») и сохраняется в БД; по умолчанию берётся из языка Telegram-клиента,
иначе — английский. Строки хранятся в `src/main/resources/i18n/messages[_xx].properties`
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
com.tessera.bot
├── TesseraBotApplication      # точка входа, DI-проводка
├── config/AppConfig           # конфигурация из переменных окружения
├── remnawave/                 # типизированный клиент Remnawave API
│   ├── RemnawaveClient
│   ├── RemnawaveException
│   └── dto/                   # DTO запросов/ответов
├── db/                        # HikariCP + Flyway + репозитории
│   ├── Database
│   ├── UserRepository
│   ├── ReferralRepository
│   └── model/
├── service/                   # бизнес-логика
│   ├── SubscriptionService    # жизненный цикл VPN-аккаунта
│   ├── ReferralService        # реферальная программа
│   └── Plans                  # каталог тарифов
├── bot/                       # слой Telegram
│   ├── TesseraBot             # long-polling роутер
│   ├── UserHandler            # пользовательские сценарии
│   ├── AdminHandler           # админ-сценарии
│   ├── Keyboards / Callbacks  # inline-клавиатуры
│   └── UserState / Reply
└── util/Formatting            # форматирование сообщений
```

Слои разделены по ответственности: **bot** (I/O Telegram) → **service**
(бизнес-правила) → **remnawave** (внешний API) + **db** (собственное состояние:
рефералы, привязки, платежи). VPN-аккаунт «источника истины» живёт в панели
Remnawave; локальная БД хранит только то, чего в панели нет.

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

`UserHandler.buyPlan(...)` активирует тариф напрямую. Чтобы подключить реальную
оплату (ЮKassa, Telegram Payments, Crypto Pay и т.п.), проведите вызов через
провайдера и вызывайте активацию только после успешного платежа. Записи
платежей уже моделируются таблицей `payments`.

## Лицензия

MIT — см. [LICENSE](LICENSE).
## Модули (плагины)

Начиная с v1.1 бот собран как мульти-модульный Gradle-проект, а функциональность
подключается **плагинами** — отдельными JAR-файлами в каталоге `modules/`
(переменная `MODULES_DIR`). Каждый модуль реализует SPI `BotModule` из `core-api`
и обнаруживается через `ServiceLoader` в собственном `URLClassLoader`.

```
tessera-vpn-bot
├── core-api/          # SPI: BotModule, ModuleContext, PaymentProvider, ...
├── app/               # ядро бота (tessera-vpn-bot.jar) + загрузчик модулей
└── payment-module/    # PaymentModule.jar — платёжные шлюзы CryptoBot и Platega
```

### PaymentModule.jar

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

1. Создайте Gradle-подпроект с `compileOnly(project(":core-api"))`.
2. Реализуйте `com.tessera.core.api.BotModule` и зарегистрируйте класс в
   `META-INF/services/com.tessera.core.api.BotModule`.
3. Соберите shadow-jar и положите его в `modules/` — ядро подхватит его при старте.
## Админ-дашборд (AdminDashboard.jar)

Модуль `admin-dashboard` поднимает встроенный веб-дашборд (JDK HttpServer, без внешних
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
