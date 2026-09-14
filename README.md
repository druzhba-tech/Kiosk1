# 📱 Kiosk Browser для Android

Полнофункциональное нативное киоск-приложение (аналог Fully Kiosk Browser) с технологичным дизайном **Cyber-HUD** на Jetpack Compose Material 3.

[![Build APK](https://github.com/ТВОЙ_USERNAME/kiosk-browser/actions/workflows/build-apk.yml/badge.svg)](https://github.com/ТВОЙ_USERNAME/kiosk-browser/actions/workflows/build-apk.yml)

---

## 🚀 Быстрый старт — скачать готовый APK с GitHub

### Способ 1: Скачать из GitHub Releases (Рекомендуется)

1. Перейди во вкладку **[Releases](https://github.com/ТВОЙ_USERNAME/kiosk-browser/releases)**
2. Скачай последний `app-release-unsigned.apk`

### Способ 2: Скачать Debug APK из Actions

1. Перейди в **Actions → Build APK → последний запуск**
2. В секции **Artifacts** скачай `KioskBrowser-Debug-XXX`

### Способ 3: Запустить сборку вручную

1. Перейди в **Actions → 🚀 Build & Release APK**
2. Нажми кнопку **"Run workflow"**
3. Дождись завершения (5-10 минут)
4. Скачай APK из раздела **Artifacts**

---

## 🏷️ Создать новый релиз (автоматическая сборка Release APK)

```bash
# Перейди в папку проекта
cd "C:\Users\Mohchehra\.gemini\antigravity-ide\scratch\kiosk-browser"

# Создай и запушь тег версии
git tag v1.0.0
git push origin v1.0.0
```

GitHub Actions автоматически:
- Соберёт Release APK
- Создаст GitHub Release с описанием
- Прикрепит APK файл

---

## 🔧 Сборка локально (Android Studio / Gradle)

### 1. Переход в папку проекта:
```bash
cd "C:\Users\Mohchehra\.gemini\antigravity-ide\scratch\kiosk-browser"
```

### 2. Сборка Debug APK:
```bash
# Windows
gradlew.bat assembleDebug

# Linux / Mac
./gradlew assembleDebug
```

Готовый APK:
```
app/build/outputs/apk/debug/app-debug.apk
```

---

## 📲 Установка на планшет

### 3. Установка через ADB:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## ⚙️ Активация режима Device Owner

> **Важно:** На планшете не должно быть добавленных аккаунтов (Google/Telegram).

```bash
adb shell dpm set-device-owner com.kiosk.browser/.admin.KioskDeviceAdminReceiver
```

После этой команды активируются:
- Блокировка Safe Mode
- Запрет выхода без PIN
- Блокировка USB-передачи данных
- Полное отключение шторки статус-бара
- **Silent Install** — тихая установка обновлений без диалогов ✨

---

## 🔐 Управление и секретные функции

### Вход в меню настроек:
- Нажмите **5 раз** в правый верхний угол экрана
- PIN по умолчанию: **`1234`**

### Аварийный сброс через ADB:
```bash
adb shell am broadcast -a com.kiosk.RESET_LOCK --es key "SECRET_KIOSK_KEY_777"
```

### Удалённая перезагрузка страницы:
```bash
adb shell am broadcast -a com.kiosk.RELOAD_PAGE
```

### Удалённая установка URL:
```bash
adb shell am broadcast -a com.kiosk.SET_URL --es url "http://192.168.1.100:8123"
```

### Локальный Web REST Admin Panel:
Открой в браузере с компьютера: `http://<IP_ПЛАНШЕТА>:8080/`

---

## 🔄 OTA Обновления (автоматические)

### Настройка:
1. В настройках приложения включи **"OTA ОБНОВЛЕНИЯ"**
2. Укажи URL манифеста (например из GitHub Pages или своего сервера)

### Формат `update.json`:
```json
{
  "version_name": "1.2.0",
  "apk_url": "https://github.com/ТВОЙ_USERNAME/kiosk-browser/releases/download/v1.2.0/app-release-unsigned.apk"
}
```

### Хостинг update.json бесплатно через GitHub Pages:
1. Включи GitHub Pages для репозитория (Settings → Pages → main branch)
2. Скопируй `update.json` в корень репозитория
3. URL манифеста будет: `https://ТВОЙ_USERNAME.github.io/kiosk-browser/update.json`

> 💡 **Во время загрузки и установки APK — киоск продолжает работать!**
> Прогресс отображается в статус-баре (значок `↓ 47%`).

---

## 🏗️ Архитектура проекта

```
app/src/main/java/com/kiosk/browser/
├── KioskApp.kt               # Application класс
├── MainActivity.kt           # Главное Activity
├── admin/                    # Device Owner управление
├── core/
│   ├── nfc/                  # NFC поддержка
│   ├── power/                # Управление питанием
│   ├── security/             # Защита и ADB команды
│   ├── sensors/              # Акселерометр (антивор)
│   ├── update/               # OTA обновления ✨
│   └── webview/              # WebView клиент и JS Bridge
├── data/                     # Модели и репозитории конфигурации
├── network/
│   ├── mqtt/                 # MQTT / Home Assistant
│   └── server/               # REST Admin сервер (порт 8080)
├── service/                  # Foreground Service
└── ui/
    ├── components/           # HUD, StatusBar, Диалоги
    ├── screens/              # Экраны приложения
    └── theme/                # Cyber-HUD тема
```
