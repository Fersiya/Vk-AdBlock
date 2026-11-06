# 🛡️ VK Ad Blocker (Browser Extension + Android App)

Комплексное решение для блокировки рекламы ВКонтакте:
1. 🌐 **Browser Extension** — расширение для браузеров на базе Chromium (Brave, Chrome, Edge, Яндекс.Браузер)
2. 📱 **Android App** — мобильное приложение на Android с локальным VPN и DNS-фильтрацией

---

## 📁 Структура репозитория

```
├── browser-extension/          # Расширение для браузера Brave / Chrome
│   ├── manifest.json           # Manifest V3
│   ├── content.js              # Контент-скрипт + MutationObserver
│   ├── blocker.css             # Скрытие рекламных блоков по CSS
│   ├── background.js           # Service Worker и бейджи
│   ├── rules.json              # Сетевые правила declarativeNetRequest
│   ├── popup.html / popup.js   # Всплывающее окно со статистикой
│   └── vk-adblock-dns.txt      # Список доменов для DNS-фильтров
│
├── android-app/                # Приложение для Android (Kotlin)
│   ├── app/src/main/
│   │   ├── kotlin/com/vkadblock/
│   │   │   ├── MainActivity.kt # Пользовательский интерфейс и переключатель
│   │   │   ├── vpn/            # VpnService + DNS парсер (0.0.0.0 sinkhole)
│   │   │   └── data/           # Список рекламных доменов и статистика
│   │   └── res/                # Разметка, стили, иконки
│   └── build.gradle.kts
│
└── .github/workflows/
    └── build-apk.yml           # Автоматическая сборка APK на GitHub Actions
```

---

## 🌐 1. Расширение для браузера (Brave / Chrome)

### Установка:
1. Откройте в браузере: `brave://extensions/` (или `chrome://extensions/`)
2. Включите **«Режим разработчика»** (Developer Mode) в правом верхнем углу
3. Нажмите кнопку **«Загрузить распакованное расширение»** (Load unpacked)
4. Выберите папку `browser-extension` из этого репозитория

### Что блокируется:
- Рекламные и промо-посты в ленте
- Баннеры в боковой панели
- Рекламные Stories и Клипы
- Реклама перед/внутри видео
- Рекомендации сообществ и промо-блоки

---

## 📱 2. Приложение для Android

Приложение поднимает **локальный виртуальный VPN** на устройстве, перехватывает DNS-запросы и блокирует рекламные домены (`ads.vk.com`, `ad.mail.ru`, `target.my.com` и др.), отвечая `0.0.0.0`.

> 🔒 **Безопасность:** трафик **не** уходит на сторонние серверы, всё обрабатывается прямо на вашем телефоне. Root-права не требуются.

### Как установить:

#### Способ A: Скачать готовый APK из GitHub Actions
1. Перейдите во вкладку **Actions** в репозитории
2. Выберите последний успешный запуск **Build Android APK**
3. Внизу в блоке **Artifacts** скачайте `vk-ad-blocker-apk.zip`
4. Распакуйте и установите `.apk` на смартфон

#### Способ B: Сборка через Android Studio
1. Откройте Android Studio → `File` → `Open` → выберите папку `android-app`
2. Дождитесь завершения Gradle Sync
3. Нажмите кнопку **Run ▶** (при подключенном по USB телефоне с отладкой)

---

## 📄 Лицензия
MIT License
