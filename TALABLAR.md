# ТЗ talablari — to'liq ro'yxat va bajarilish holati

Manba: BIZNES BOMOND ТЗ v1.0 (27.08.2026), 1-qism — «Дашборд управления проектами с интерфейсом
в Telegram». Har bir band amalda qayerda bajarilgani ko'rsatilgan.

## 1. Foydalanuvchilar va huquqlar

| № | Talab | Holat |
|---|---|---|
| 1.1 | Faqat Telegram orqali kirish (login/parolsiz), user_id orqali identifikatsiya | ✅ |
| 1.2 | Birinchi kirganda F.I.Sh. va lavozim so'raladi | ✅ |
| 1.3 | 3 rol: Xodim / Loyiha rahbari / Direktor | ✅ |
| 1.4 | Xodim — faqat o'z vazifalarini ko'radi | ✅ |
| 1.5 | Loyiha rahbari — o'z loyihalaridagi barcha vazifalarni ko'radi | ✅ |
| 1.6 | Direktor — hammasini ko'radi | ✅ |
| 1.7 | Dashboardga Telegram orqali kirish (Login Widget yoki bir martalik havola) | ✅ (Login Widget) |

## 2. Ma'lumotlar tuzilishi

| № | Talab | Holat |
|---|---|---|
| 2.1 | 3 daraja: Loyiha → Sprint/Bo'lim → Vazifa | ✅ |
| 2.2 | Har darajada: nomi, mas'ul, muddat (majburiy) | ✅ |
| 2.3 | Muddatsiz obyekt yaratib bo'lmaydi — bloklovchi validatsiya | ✅ |
| 2.4 | Vazifa muddati ≤ sprint muddati ≤ loyiha muddati | ✅ |

## 3. Telegram-bot funksiyalari

| № | Talab | Holat |
|---|---|---|
| 3.1 | Loyiha yaratish — bosqichma-bosqich dialog (nomi→mas'ul→muddat→tavsif) | ✅ |
| 3.2 | Sprint yaratish | ✅ |
| 3.3 | Vazifa yaratish + ijrochi tayinlash | ✅ |
| 3.4 | `/mytasks` — muddat bo'yicha saralab ko'rsatish | ✅ |
| 3.5 | Statusni tugmalar orqali o'zgartirish (qo'lda buyruq emas) | ✅ |
| 3.6 | Vazifaga izoh yozish | ✅ |
| 3.7 | Muddat: KK.OO.YYYY yoki "bugun"/"ertaga"/"N kundan keyin" | ✅ |

## 4. Statuslar

| № | Talab | Holat |
|---|---|---|
| 4.1 | Yangi → Ishda → Tekshiruvda (fayl majburiy) → Bajarildi (rahbar tasdiqlaydi) → Bekor qilindi (sabab majburiy) | ✅ |
| 4.2 | Sprint/loyiha statusi ichidagi elementlardan avtomatik hisoblanadi | ✅ |

## 5. Muddatlar va bildirishnomalar (asosiy stsenariy, baholashda 30%)

| № | Talab | Holat |
|---|---|---|
| 5.1 | Muddatga 24 soat qolganda — dashboardda qizil rang | ✅ |
| 5.2 | Shu vaqtda ijrochiga Telegram xabari (⚠️ + 3 tugma: Hisobot/Tayyor/Ko'chirish) | ✅ |
| 5.3 | Xodim javobi vazifa tarixiga izoh sifatida saqlanadi | ✅ |
| 5.4 | Muddat kelganda, yopilmagan bo'lsa — qayta eslatma | ✅ |
| 5.5 | +24 soat kechikishdan keyin — rahbarga va direktor xulosasiga bildirishnoma | ✅ |
| 5.6 | Muddat ko'chirish so'rovi avtomatik o'zgartirmaydi — rahbar tasdig'i kerak | ✅ |
| 5.7 | Toshkent vaqt zonasi, faqat 09:00–20:00 oralig'ida yuboriladi | ✅ |

## 6. Veb-dashboard

| № | Talab | Holat |
|---|---|---|
| 6.1 | Loyihalar ro'yxati: % bajarilish, yaqin muddat, kechikkan vazifalar soni | ✅ |
| 6.2 | Loyiha → sprint → vazifa ichiga kirish | ✅ |
| 6.3 | Rang ko'rsatkichi: yashil/sariq/qizil | ✅ |
| 6.4 | Filtrlar: ijrochi, status, loyiha, faqat kechikkanlar | ✅ |
| 6.5 | Vazifa kartochkasi: tavsif, status tarixi, izohlar, fayllar | ✅ |

## 7. Texnik talablar

| № | Talab | Holat |
|---|---|---|
| 7.1 | Stek erkin, lekin README'da asoslangan bo'lishi kerak | ✅ (Java+Spring+Postgres, README'da tushuntirilgan) |
| 7.2 | Ma'lumotlar bazada (xotirada emas), restartda yo'qolmaydi | ✅ (PostgreSQL) |
| 7.3 | Deploy qilingan, bepul hosting, tekshirish uchun ochiq | ✅ (Render, https://yangi-ishxona.onrender.com) |
| 7.4 | 100 tagacha foydalanuvchi, 50 tagacha faol loyiha | ✅ (README'da masshtablash tahlili) |
| 7.5 | Interfeys tili — rus (o'zbek — plyus) | ✅ (+ ingliz tili ham, qo'shimcha) |

## 8. Topshiriladigan narsalar

| № | Talab | Holat |
|---|---|---|
| 8.1 | Kod repozitoriysi havola | ✅ https://github.com/MyOrmonjonov/-yangi-ishxona |
| 8.2 | Ishlaydigan test bot + dashboard havola | ✅ @new_task_project_bot, https://yangi-ishxona.onrender.com |
| 8.3 | README.md (ishga tushirish, sxema, arxitektura, допущения, nima ulgurilmadi) | ✅ |
| 8.4 | 3–7 daqiqalik video (to'liq stsenariy namoyishi) | ⬜ **faqat siz yozishingiz kerak** — bot/dashboard/ekranni yozib olish talab qilinadi, buni men bajara olmayman |

## Xulosa

Majburiy hajm (1–7 bo'limlar) **to'liq bajarilgan**. Qolgan yagona band — 8.4: siz namoyish videosini
o'zingiz yozib olishingiz kerak (Telegram'da botni ochib, loyiha→sprint→vazifa→dedlayn→bildirishnoma→
hisobot stsenariysini ekranga yozib olish).

TaskApp'dan hech narsa ko'chirilmagan — yuqoridagi hamma band shu loyihaning o'z kodi bilan yopilgan.
