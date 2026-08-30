# Дашборд управления проектами с интерфейсом в Telegram

Прототип: сотрудники ведут всю работу через Telegram-бота (создание проектов/спринтов/задач,
смена статусов, дедлайны, вложения), руководство и директор смотрят веб-дашборд. Дополнительно
доступен Telegram Mini App как более удобная визуальная надстройка над теми же данными.
Интерфейс на 3 языках: русский, узбекский, английский.

**Продакшен:** https://yangi-ishxona.onrender.com (бот: `@new_task_project_bot`)
**Репозиторий:** https://github.com/MyOrmonjonov/-yangi-ishxona

## Стек и почему

- **Java 17 + Spring Boot** — исходный скелет проекта уже был на Spring Boot, экосистема
  (Spring Data JPA, Spring MVC, Thymeleaf, `@Scheduled`) закрывает все нужды прототипа без
  дополнительных фреймворков.
- **PostgreSQL** (Spring Data JPA, `ddl-auto=update`) — реляционная модель (Проект → Спринт →
  Задача, статусы, история) естественно ложится на SQL; `ddl-auto=update` — осознанное упрощение
  для 5-дневного прототипа вместо Flyway/Liquibase.
- **TelegramBots (`org.telegram:telegrambots-*` 10.2.1)** — актуальная модульная версия
  официальной Java-библиотеки (long polling и webhook на выбор, `TelegramClient` для отправки).
- **Thymeleaf** (server-side rendering) — веб-дашборд не требует SPA-сложности, простой и быстрый
  способ выдать HTML с сессионной авторизацией через Telegram Login Widget.
- **Ванильный JS/CSS для Mini App** — без сборщика: одна HTML-страница + `fetch` к JSON API,
  этого достаточно для набора экранов «список → карточка».

## Как запустить локально

1. Скопируйте `.env.example` → `.env` и заполните `TELEGRAM_BOT_TOKEN` (получить у
   [@BotFather](https://t.me/BotFather)) и `TELEGRAM_BOT_USERNAME`. `.env` подхватывается
   автоматически при старте (`YangiIshxonaApplication.loadDotEnvIntoSystemProperties`).
2. Поднимите Postgres: `docker compose up -d`.
3. Запустите приложение: `./gradlew bootRun` (Windows: `gradlew.bat bootRun`).
4. Бот работает в режиме long polling по умолчанию (`TELEGRAM_BOT_MODE=polling`) — вебхук не
   нужен для локальной разработки. Дашборд: http://localhost:8080/login. Mini App:
   http://localhost:8080/miniapp (полноценно откроется только внутри Telegram, т.к. использует
   `Telegram.WebApp.initData`).

Тесты: `./gradlew test` (использует H2 в режиме совместимости с PostgreSQL, реального Postgres
не требует).

## Схема данных

```
AppUser (telegramUserId, fullName, position, role, language)
Project (name, description, customer, responsible→AppUser, deadline, status)
 └─ Sprint (name, responsible→AppUser, deadline, status)
     └─ Task (name, description, executor→AppUser, deadline, status)
         ├─ TaskComment (text, author→AppUser)
         ├─ TaskAttachment (telegramFileId, originalFileName, uploadedBy→AppUser)
         └─ DeadlineChangeRequest (oldDeadline, newDeadline, status, requestedBy, resolvedBy)
StatusHistory (entityType, entityId, oldStatus, newStatus, changedBy, comment)
DeadlineNotificationLog (task, notificationType, scheduledFor, sentAt)
```

Статус `Project`/`Sprint` **не хранится вручную** — пересчитывается `StatusRollupService` при
каждом изменении дочерней задачи: если все дети отменены → `CANCELLED`; если все (кроме
отменённых) выполнены → `DONE`; если хоть один начат → `IN_PROGRESS`; иначе → `NOT_STARTED`.

## Архитектура

```
domain/       JPA-сущности и enum'ы
repository/   Spring Data JPA репозитории
service/      бизнес-логика (Project/Sprint/Task/User/StatusRollup/DeadlineScheduler),
              i18n (Messages, DomainException с ключом+аргументами вместо текста)
bot/          Telegram-бот (YangiIshxonaBot — единая точка входа updates), пошаговые диалоги
              через ChatSession/ChatState, DeadlineParser (даты + "через N дней")
web/          Thymeleaf-контроллеры дашборда, Telegram Login, Mini App REST API,
              приём вебхука, обработка ошибок
config/       регистрация бота (polling или webhook), кнопка меню Mini App
resources/
  i18n/       messages_{uz,ru,en}.properties — один источник переводов для бота,
              дашборда (Spring MessageSource, th:text="#{...}") и Mini App API
  templates/  Thymeleaf-страницы дашборда + оболочка Mini App
  static/miniapp/  app.js (vanilla JS, без сборщика) + styles.css
```

Ключевое: `DomainException` несёт **ключ перевода + аргументы**, а не готовый текст — рендерится
в языке конкретного пользователя (`Messages.t(user.getLanguage(), key, args…)`) в месте перехвата
(бот / дашборд / Mini App API), поэтому один и тот же код ошибки одинаково звучит на всех
поверхностях и на всех 3 языках.

### Дедлайны и уведомления (ТЗ 3.6)

`DeadlineSchedulerService` каждые 10 минут (`app.scheduler.deadline-check-interval-ms`) в две
фазы: 1) помечает наступившие триггеры (T‑24ч / в момент дедлайна / +24ч просрочки) как
"поставлены в очередь" (`DeadlineNotificationLog`, идемпотентно — повторный проход не создаёт
дубликат); 2) если сейчас 09:00–20:00 по Asia/Tashkent — рассылает всё, что в очереди. Ночной
триггер просто ждёт следующего прохода после 09:00 — то же событие, тот же механизм, никакого
отдельного "утреннего" кода.

### Telegram Mini App

Бот и дашборд — обязательная часть ТЗ (3.1–3.7). Mini App добавлен по отдельному запросу поверх
готового решения как визуальная надстройка: просмотр проектов/спринтов/задач, смена статуса,
комментарии, запрос переноса срока. Аутентификация — не сессия, а `Telegram.WebApp.initData`
(заголовок `X-Telegram-Init-Data`), проверяется на бэкенде отдельным алгоритмом
(`TelegramWebAppAuthService`, HMAC-SHA256 с ключом `"WebAppData"` — не путать с алгоритмом Login
Widget, который использует SHA256(token) напрямую). REST-слой (`MiniAppController`) не содержит
своей бизнес-логики — вызывает те же `ProjectService`/`SprintService`/`TaskService`, что бот и
дашборд.

**Ограничение:** перевод задачи в статус «На проверке» требует прикреплённого файла (ТЗ 3.5) —
загрузка файлов в Mini App не реализована (усложнение, не входящее в обязательный объём), поэтому
это действие остаётся в чат-боте; Mini App показывает остальные доступные действия по задаче.

## Принятые допущения

- **Роль "Руководитель проекта" — не глобальная, а по факту создания.** Сотрудник, создавший
  проект, становится его `responsible` и получает роль `PROJECT_MANAGER` (если был `EMPLOYEE`).
  ТЗ не описывает механизм назначения этой роли — решено так, чтобы не требовать отдельного
  админ-интерфейса для базового сценария.
- **Первый директор — через `BOOTSTRAP_DIRECTOR_TELEGRAM_ID`.** Если переменная не задана, им
  становится первый когда-либо зарегистрированный пользователь. Последующих директоров назначает
  действующий директор командой `/setrole`.
- **Создавать проект/спринт/задачу может любой зарегистрированный сотрудник** — ТЗ 3.1 описывает
  разграничение по *просмотру* (кто что видит), не по созданию; раздел 2 явно перечисляет
  создание проекта/задачи как действие "всех сотрудников".
- **Дедлайн — календарная дата, без времени.** Для расчёта "T-24 часа" момент дедлайна берётся
  как конец дня (23:59:59) по Asia/Tashkent.
- **Отчёт о просрочке директору — это сама сводка на дашборде** (директор видит все просроченные
  задачи через цветовую индикацию), а не отдельная push-рассылка — ТЗ говорит "в сводку
  директору", не "директору лично".
- **Прикреплённые файлы отдаются через собственный сервер** (`/attachments/{id}/open`), а не
  прямой ссылкой на `api.telegram.org/file/bot<token>/...` — иначе токен бота попадал бы в
  адресную строку браузера.

## Что не успел / известные ограничения

- **Render free tier "засыпает"** после 15 минут простоя; первый вебхук после паузы будит сервис
  ~30–60 сек (Telegram в это время повторяет доставку — сообщения не теряются, но приходят с
  задержкой). Смягчено GitHub Actions-пингом каждые 10 минут (`.github/workflows/keepalive.yml`);
  полностью решается переходом на платный план или другой хостинг без сна.
- **Бесплатный Postgres на Render истекает через 30 дней** — ограничение самого бесплатного
  тарифа Render, не относится к архитектуре решения.
- Фильтр в Mini App упрощён до «только просроченные» (без выбора исполнителя/статуса, в отличие
  от дашборда) — сознательное сокращение объёма второстепенного (Mini App не входит в ТЗ)
  интерфейса при ограничении по времени.
- Экспорт в Excel и отдельный отчёт «загрузка сотрудников» (оба — «плюсом» по ТЗ 5) не сделаны.

## Использование ИИ

Проект написан с активным использованием Claude (Anthropic) как ассистента в Claude Code — от
проектирования архитектуры до самого кода, шаблонов и деплоя. Каждое архитектурное решение
(модель данных, разграничение доступа, алгоритм пересчёта статусов, обработка дедлайнов,
i18n-механизм, выбор между polling/webhook) осознанно проверялось и объясняется в этом README —
готов разобрать и обосновать любую часть кода на защите.

## Деплой

Продакшен — **Render** (`render.yaml`, Blueprint: веб-сервис из `Dockerfile` + бесплатный
Postgres). Изначально пробовали Railway, но триал аккаунта оказался исчерпан и требовал
привязки платного плана — переключились на Render. Автодеплой включён (push в `main` →
пересборка). Обязательные переменные окружения на сервисе: `TELEGRAM_BOT_TOKEN`,
`TELEGRAM_BOT_USERNAME`, `BOOTSTRAP_DIRECTOR_TELEGRAM_ID` — заданы вручную в Render Dashboard
(отмечены `sync: false` в `render.yaml`, т.к. это секреты). `PGHOST/PGPORT/...` подставляются
автоматически из связанной базы, `PUBLIC_BASE_URL`/`MINIAPP_BASE_URL` — из `RENDER_EXTERNAL_URL`.
