# Task App — loyiha holati

## Loyiha haqida

Task App — Telegram Mini App orqali shaxsiy va jamoaviy vazifalarni yaratish, guruhlarga bog‘lash, mas’ullarni belgilash, muddat va ustuvorliklarni boshqarish uchun ishlab chiqilayotgan tizim.

Texnologiyalar:

- Backend: Java 21, Spring Boot, Spring Data JPA, PostgreSQL va Flyway
- Frontend: React 19, TypeScript va Vite
- Integratsiya: Telegram Mini App va Telegram Bot API
- Infratuzilma: Docker, Render va Neon PostgreSQL

## Hozirgi bosqich

**2-bosqich — mobil Telegram Mini App interfeysi, asosiy vazifalar oqimi va doimiy hostingga deploy.**

Oxirgi yangilanish: **2026-07-18**

Cloudflare Quick Tunnel beqaror bo‘lgani sabab undan voz kechildi. Loyiha Render va Neon PostgreSQL’ga muvaffaqiyatli joylandi. Jonli manzil: `https://taskapp-mini-app-9n4e.onrender.com`.

## Bajarilgan ishlar

- PostgreSQL sxemasi va Flyway migratsiyalari
- Telegram `initData` tekshiruvi va bearer sessiya autentifikatsiyasi
- Workspace a’zoligi va dastlabki owner yaratish
- Telegram bot `/start` komandasi
- Vazifalarni ro‘yxatlash, yaratish va scope bo‘yicha filtrlash
- Mobil bosh ekran va alohida vazifa, muddat, sozlamalar ekranlari
- Vazifa joyi va ko‘rinadigan vazifalarni tanlash bottom-sheet oynalari
- Prioritet, status, tavsif, checklist va fayl maydonlari
- Telegram foydalanuvchisidan olinadigan dinamik profil nomi
- Telegram `WebApp.requestChat` native guruh tanlash oqimi
- Bot API `savePreparedKeyboardButton` va `chat_shared` hodisasini qayta ishlash
- Tanlangan guruhni workspace bazasiga saqlash va frontend ro‘yxatini yangilash
- React frontend va Spring Boot backendni bitta Docker image ichida build qilish
- Render `PORT`, health-check va forwarded-header sozlamalari
- `render.yaml` Blueprint va `DEPLOY_RENDER.md` qo‘llanmasi
- Maxfiy qiymatlarni Render panelida kiritish va `APP_AUTH_SECRET` ni avtomatik yaratish
- `.env` va boshqa maxfiy fayllarni Git/Docker build contextidan chiqarish
- Frontend production build va backenddagi 3 ta avtomatik test
- Neon PostgreSQL production bazasini ulash va Flyway migratsiyalarini ishga tushirish
- Render environment qiymatlaridagi xatoni tuzatish va production deployni `live` holatiga chiqarish
- `TELEGRAM_MINI_APP_URL` ni doimiy Render URL’iga sozlash
- Jonli bosh sahifa va `/api/health` endpointini HTTP `200` javobi bilan tekshirish
- Guruh tanlash uchun Telegram WebApp skriptini joriy `?63` versiyasiga yangilash
- Java Telegram bot kutubxonasini Bot API `9.6.0` versiyasiga yangilash
- Telegram `savePreparedKeyboardButton` so‘rovini production bot bilan muvaffaqiyatli tekshirish
- Polling to‘qnashuvini kamaytirish uchun eski takroriy Render servisni suspend qilish
- Native `requestChat` javob bermasa, bot chatidagi `request_chat` tugmasiga avtomatik fallback qilish
- Guruh tanlangach bot orqali tasdiq yuborish va vaqtinchalik klaviaturani yopish
- Guruh javobiga Task App’dagi guruh a’zolari profillarini qo‘shish
- Guruh vazifasida bir yoki bir nechta mas’ulni avatar orqali tanlash
- Guruh vazifasini haqiqiy `groupId` va tanlangan `assigneeIds` bilan saqlash hamda backendda tekshirish
- Muddat oynasiga tayyor vaqtlar bilan birga istalgan soat-daqiqani tanlash maydonini qo‘shish
- Taqvimni joriy sanadan boshlash va o‘tgan muddatni frontendda bloklash
- Faylsiz vazifalarni iOS uchun barqaror JSON so‘rovi orqali saqlash va backend xatosini foydalanuvchiga ko‘rsatish
- Guruh uchun bot a’zoligini tekshirish va botni qo‘shish tugmasini Mini App ichida ko‘rsatish
- Guruh vazifasini Telegram chatiga mas’ullar, muallif va muddat bilan yuborish
- Telegram vazifa xabariga `Boshladim` tugmasini qo‘shish
- `Boshladim` bosgan guruh a’zosini workspace, guruh va vazifaga avtomatik biriktirish
- Birinchi boshlagan a’zo orqali vazifa statusini `Jarayonda` holatiga o‘tkazish va tarixga yozish
- Mini App’dan Telegram guruhiga a’zolik taklif xabarini yuborish
- Guruh a’zosi `Task App’ga a’zo bo‘lish` tugmasini bosganda uni workspace va guruhga avtomatik qo‘shish
- A’zolik taklifidan keyin mas’ullar ro‘yxatini bir daqiqa davomida avtomatik yangilash
- Telegram guruhidagi har bir vazifaga `Boshladim`, `Bajarildi`, `Muammo bor` va `Tekshiruv` tugmalarini chiqarish
- Telegram tugmalarini vazifaning `IN_PROGRESS`, `COMPLETED`, `BLOCKED` va `REVIEW` statuslariga bog‘lash
- Muvaffaqiyatli bosilgan tugmani xabardan olib tashlab, qolgan tugmalarni saqlab qolish
- Telegramdagi har bir vazifa amalini foydalanuvchi va chat ma’lumoti bilan tarixga yozish
- Bosh sahifadagi vazifa kartasini bosib alohida `Tahrirlash` sahifasini ochish
- Vazifa nomi, tavsifi va statusini `PUT /api/tasks/{id}` orqali Neon bazaga saqlash
- Statuslarni `Yangi`, `Jarayonda`, `Kutilmoqda`, `Tekshiruvda` va `Bajarildi` bottom-sheet oynasidan tanlash
- Statusga qarab `Boshlash`, `Yakunlash` yoki `Qayta ochish` tezkor amalini ko‘rsatish
- Telegramda `Muammo bor` bosilganda `Boshladim` tugmasini qayta chiqarish
- Guruh vazifalarida umumiy nom o‘rniga bazadagi haqiqiy Telegram guruh nomini ko‘rsatish
- Telegramda `Muammo bor` yoki `Tekshiruv` bosilganda vazifani Mini App ichida `Jarayonda` holatida qoldirish
- Mini App ichidagi `Yakunlash` tezkor amalini darhol Neon bazaga saqlash va guruhga to‘rtta amal tugmali yangi xabar yuborish
- Bajarilgan vazifani Telegramdagi yangi `Boshladim` tugmasi orqali qayta ochish
- Vazifa tahrirlash oynasida joy, yaratuvchi, mas’ullar, muddat va checklist tafsilotlarini ko‘rsatish
- Biriktirilgan rasmlar va fayllarni kichik galereya ko‘rinishida ko‘rsatish
- Yangi biriktirilgan fayl kontentini Neon PostgreSQL bazasida doimiy saqlash
- Galereya, kamera yoki fayldan tanlangan rasmni vazifa yaratilishidan oldin preview sifatida ko‘rsatish
- Tanlangan rasmni preview kartasidan olib tashlash imkonini qo‘shish
- Vazifa tahrirlash oynasiga yaratish oynasi bilan bir xil Galereya, Kamera va Fayl yuklash blokini qo‘shish
- Mavjud vazifaga keyinroq biriktirilgan rasmlarni Neon bazaga saqlash va darhol galereyada ko‘rsatish
- Vazifa tahrirlash oynasida checklist matnini o‘zgartirish, belgilash, o‘chirish va yangi band qo‘shish
- Mavjud muddatni alohida taqvim oynasida o‘zgartirish yoki `Muddatsiz` qilish
- Vazifa darajasini mavjud qiymati bilan ko‘rsatish va tahrirlash
- Eski Render diskida yo‘qolgan faylni aniqlab, `Qayta yuklang` holatini ko‘rsatish
- Rasm MIME turi yetishmasa JPG, PNG, GIF, WebP va HEIC kengaytmalaridan preview turini aniqlash
- Muddat taqvimi ostiga `Eslatma` qatori va bir tanlovli bottom-sheet oynasini qo‘shish
- Belgilangan vaqt, 5/15/30 daqiqa, 1 soat yoki 1 kun oldin eslatish variantlarini qo‘shish
- Tanlangan eslatmani `task_reminders` jadvalida saqlash va tahrirlashda qayta ko‘rsatish
- Guruh eslatmasini guruh chatiga, shaxsiy va 1:1 eslatmalarni mas’ul foydalanuvchilarga Telegram orqali yuborish
- Har 30 soniyada muddati kelgan yuborilmagan eslatmalarni tekshiruvchi scheduler qo‘shish

## Hozir ishlanayotgan qism

- Muddat eslatmasini tanlash, saqlash va Telegramga yuborish oqimini productionda tekshirish

## Ma’lum cheklovlar

- Render Free web service 15 daqiqa so‘rov kelmasa uxlaydi; keyingi so‘rovda uyg‘onadi, URL o‘zgarmaydi.
- Yangi fayllar Neon bazasida saqlanadi; katta hajm va ko‘p foydalanuvchi bosqichida alohida object storage kerak bo‘ladi.
- Render’dagi eski, ishlatilmayotgan takroriy servis suspend qilingan; kerak bo‘lmasa keyin butunlay o‘chirish mumkin.
- Native guruh tanlash Telegram WebApp Bot API 9.6 yoki undan yangi versiyasini talab qiladi.
- Ayrim Telegram iOS klientlarida native `requestChat` callback qaytarmasligi mumkin; bunday holatda bot-chat fallback ishlatiladi.
- Telegram Bot API chatdagi barcha oddiy a’zolar ro‘yxatini bermaydi; mas’ul tanlashda Task App’ga kirgan va guruhga bog‘langan a’zolar ko‘rinadi.
- Guruhning boshqa a’zolari birinchi marta vazifa xabaridagi `Boshladim` tugmasini bosgach Task App guruh a’zolari ro‘yxatida paydo bo‘ladi.
- Bot guruhga qo‘shilmagan bo‘lsa vazifa bazaga saqlanadi, ammo Telegram xabari yuborilmaydi; Mini App bu holatni oldindan ogohlantiradi.

## Keyingi ishlar

1. Mini App’dan guruhga a’zolik xabarini yuborish
2. Guruh a’zolariga `Task App’ga a’zo bo‘lish` tugmasini bir marta bostirish
3. Guruh vazifasini saqlash, Telegram xabari va `Boshladim` callbackini productionda tekshirish
4. Workspace invitation va vazifa izohlari modullarini qo‘shish

## Yangilash qoidasi

Har bir ish sessiyasi yakunida ushbu faylda oxirgi sana, joriy bosqich, bajarilgan ishlar, ayni paytda ishlanayotgan qism, keyingi qadamlar va ma’lum muammolar yangilanadi.
