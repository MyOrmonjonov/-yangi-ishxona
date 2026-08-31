# Task App

Telegram Mini App asosidagi jamoaviy vazifalar tizimining backend poydevori.

## Hozir ishlaydigan qismlar

- PostgreSQL uchun Flyway boshqaradigan boshlang‘ich sxema (rejadagi barcha asosiy jadvallar).
- Telegram Mini App `initData` HMAC-SHA256 tekshiruvi va eskirish nazorati.
- Taklifga asoslangan kirish: faol workspace a’zoligi bo‘lmasa sessiya berilmaydi.
- Birinchi egani `BOOTSTRAP_OWNER_TELEGRAM_ID` orqali xavfsiz yaratish.
- `/start` komandasi, uch tildagi salomlashuv va Mini App tugmasi.
- Workspace chegarasida vazifa yaratish, filtrlash va statusni o‘zgartirish.
- 1:1 vazifalar maxfiyligi va vazifa o‘zgarish tarixining boshlang‘ich auditi.
- Bir xil API xato formati, validation va bearer sessiya himoyasi.

## Lokal ishga tushirish

1. `.env.example` faylidan `.env` yarating va maxfiy qiymatlarni almashtiring.
2. BotFather’da Mini App domenini sozlang. Telegram production Mini App uchun HTTPS talab qiladi.
3. Xizmatlarni ishga tushiring:

```powershell
docker compose --env-file .env up --build
```

Tekshiruv: `GET http://localhost:8080/api/health`.

Docker ishlatmasdan ishga tushirish uchun PostgreSQL yarating, kerakli environment qiymatlarini bering va:

```powershell
.\mvnw.cmd spring-boot:run
```

## Autentifikatsiya oqimi

Frontend `Telegram.WebApp.initData` qiymatini o‘zgartirmasdan yuboradi:

```http
POST /api/auth/telegram
Content-Type: application/json

{"initData":"query_id=...&user=...&auth_date=...&hash=..."}
```

Javobdagi `accessToken` keyingi API so‘rovlarida `Authorization: Bearer <token>` sifatida yuboriladi.

## Bazaviy vazifa API

- `GET /api/tasks?workspaceId=1&scope=ACTIVE`
- `POST /api/tasks`
- `PATCH /api/tasks/{taskId}/status`

Qo‘llab-quvvatlangan `scope`: `ALL`, `MINE`, `ACTIVE`, `COMPLETED`, `OVERDUE`, `TODAY`, `UPCOMING`.

## Muhit sozlamalari

| O‘zgaruvchi | Maqsadi |
|---|---|
| `DATABASE_URL` | PostgreSQL JDBC URL |
| `DATABASE_USERNAME`, `DATABASE_PASSWORD` | DB kirish ma’lumoti |
| `TELEGRAM_BOT_TOKEN` | BotFather tokeni |
| `TELEGRAM_MINI_APP_URL` | HTTPS frontend manzili |
| `APP_AUTH_SECRET` | Ichki access token imzolash kaliti |
| `BOOTSTRAP_OWNER_TELEGRAM_ID` | Dastlabki workspace egasining Telegram ID’si |
| `UPLOAD_DIRECTORY` | Yopiq fayllar papkasi |

`APP_AUTH_SECRET` production’da tasodifiy va kamida 32 belgili bo‘lishi kerak. `.env` faylini repozitoriyga qo‘shmang.

## Keyingi ishlab chiqish chegarasi

Bu commit 1-bosqich backend poydevori va 2-bosqich vazifa API’sining boshlang‘ich qismini beradi. React Mini App, invitation API, checklist/fayl/izohlar, Telegram guruh sinxronlash, scheduler bildirishnomalari va WebSocket hodisalari keyingi modullarda qo‘shiladi.
