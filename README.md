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
cp .env.example .env   # заполните значения и экспортируйте в окружение
./gradlew shadowJar
java -jar build/libs/tessera-vpn-bot.jar
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
