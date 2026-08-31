# Render + Neon deploy

Bu konfiguratsiya React frontend va Spring Boot backendni bitta Docker image ichida yig‘adi. Render bitta doimiy `*.onrender.com` HTTPS manzil beradi; tunnel yoki alohida domen kerak emas.

## 1. Neon PostgreSQL

1. [Neon](https://console.neon.tech/) akkauntiga GitHub orqali kiring.
2. `taskapp` nomli loyiha va PostgreSQL bazasini yarating.
3. Dashboarddagi connection ma’lumotlaridan quyidagilarni saqlab oling:
   - host;
   - database;
   - user;
   - password.
4. Render uchun JDBC manzilni quyidagi ko‘rinishda tayyorlang:

```text
jdbc:postgresql://NEON_HOST/NEON_DATABASE?sslmode=require
```

## 2. Render Blueprint

1. [Render](https://dashboard.render.com/) akkauntiga GitHub orqali kiring.
2. `New` → `Blueprint` ni tanlang.
3. `MyOrmonjonov/TaskApp` reposini ulang.
4. Render repodagi `render.yaml` faylini avtomatik topadi.
5. So‘ralgan qiymatlarni kiriting:

| Render key | Qiymat |
|---|---|
| `DATABASE_URL` | Yuqorida tayyorlangan `jdbc:postgresql://...` manzil |
| `DATABASE_USERNAME` | Neon user |
| `DATABASE_PASSWORD` | Neon password |
| `TELEGRAM_BOT_TOKEN` | BotFather tokeni |
| `TELEGRAM_BOT_USERNAME` | `@` belgisiz bot username |
| `TELEGRAM_MINI_APP_URL` | Birinchi deployda vaqtincha `https://example.com` |
| `BOOTSTRAP_OWNER_TELEGRAM_ID` | Owner Telegram raqamli ID |

`APP_AUTH_SECRET` Render tomonidan avtomatik generatsiya qilinadi.

## 3. Doimiy URL’ni ulash

1. Birinchi deploy tugagach Render bergan `https://...onrender.com` URL’ni nusxalang.
2. Render service → `Environment` ichida `TELEGRAM_MINI_APP_URL` ni shu URL’ga almashtiring.
3. BotFather’dagi Mini App/Menu Button URL’ini ham aynan shu URL’ga almashtiring.
4. Render servisni qayta deploy qiling va `/api/health` manzilini tekshiring.

## Xavfsizlik

- `.env`, bot tokeni va database parolini GitHub’ga commit qilmang.
- Maxfiy qiymatlarni faqat Render Environment bo‘limida saqlang.
- Free Render web service 15 daqiqa so‘rov kelmasa uxlaydi va keyingi so‘rovda qayta uyg‘onadi; URL o‘zgarmaydi.
- Free servisda lokal fayllar doimiy emas. Vazifa fayllari uchun keyinchalik object storage ulash kerak.
