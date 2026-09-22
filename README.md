# Kiosk Browser & Launcher для Android (Kiosk Gusar)

[![Build Android APK](https://github.com/druzhba-tech/Kiosk1/actions/workflows/build-apk.yml/badge.svg)](https://github.com/druzhba-tech/Kiosk1/actions/workflows/build-apk.yml)
[![Latest Release](https://img.shields.io/github/v/release/druzhba-tech/Kiosk1?color=brightgreen&label=Latest%20Release)](https://github.com/druzhba-tech/Kiosk1/releases/latest)

Полнофункциональное нативное киоск-приложение и системный лаунчер (аналог Fully Kiosk Browser) с технологичным дизайном Cyber-HUD на Jetpack Compose Material 3.

Разработано для бесперебойной работы **24/7** на планшетах в ресторанах, кухнях (экраны поваров KDS — Kitchen Display System), кассовых терминалах (POS), экранах электронной очереди и интерактивных киосках самообслуживания.

---

## 📥 Скачать актуальную версию

- **Последний официальный релиз:** [GitHub Releases](https://github.com/druzhba-tech/Kiosk1/releases)
- **Прямая ссылка на APK (v1.0.21):** [kiosk-browser-v1.0.21.apk](https://github.com/druzhba-tech/Kiosk1/releases/download/v1.0.21/kiosk-browser-v1.0.21.apk)
- **Цифровая подпись:** Постоянный ключ `kiosk.keystore` (V1 + V2 Scheme).

---

## ⚡ Ключевые возможности

1. **Режим Киоска и Лаунчера**:
   - Работа в качестве полноэкранного рабочего стола Android по умолчанию (`CATEGORY_HOME`).
   - Перманентное сохранение статуса лаунчера при перезагрузке устройства (устранен сброс на Samsung One UI).
   - Интерактивный диалог выбора лаунчера по умолчанию (`LauncherPickerDialog`) с поддержкой всех рабочих столов (One UI, MIUI, Pixel Launcher, Nova и др.).
   - Полная блокировка навигационных кнопок («Назад», «Домой», «Недавние» `KEYCODE_APP_SWITCH`).
   - Изоляция экрана через аппаратный `LockTask` (Android Enterprise).

2. **Отказоустойчивость 24/7**:
   - **Crash Watchdog**: При непредвиденном сбое процесс мгновенно и бесшовно перезапускается через `AlarmManager` без показа системных ошибок.
   - **Оффлайн-экран потери связи**: Киберпанк-оверлей с обратным отсчетом (5 сек), автоперезагрузкой и быстрым переходом в настройки Wi-Fi.
   - **Тихая ночная перезагрузка (04:00 утра)**: Очистка памяти Chromium WebKit при круглосуточной эксплуатации.

3. **Защита от несанкционированного доступа**:
   - Блокировка вызовов (`CallBlockReceiver`) и входящих SMS (`SmsBlockReceiver`) на планшетах с SIM-картами.
   - Запрет раздачи Wi-Fi сотрудниками (`DISALLOW_CONFIG_TETHERING`).
   - Защита настроек мастер-PIN кодом (по умолчанию `1234`).
   - Секретный жест открытия настроек: 5 быстрых тапов в правый верхний угол экрана.

4. **Мультимедиа и аппаратный контроль**:
   - Плавающая панель **Cyber HUD** (Wi-Fi, 4G, громкость, яркость, батарея, статус OTA).
   - Защита от выключения звука: автоматическое удержание громкости заказов `STREAM_MUSIC` не ниже заданного порога (60%).
   - Защита от выгорания OLED/AMOLED экранов и скринсейвер.
   - Встроенный NFC-ридер (`window.onNfcScanned`) и REST API (порт 8080).

---

## 🛠️ Настройка режима владельца устройства (Device Owner)

Для активации полного аппаратного киоска (блокировка шторки, Safe Mode, USB и навигации):

```bash
adb shell dpm set-device-owner com.kiosk.browser/.admin.KioskDeviceAdminReceiver
```
*(Перед вводом команды на планшете не должно быть привязано личных Google-аккаунтов).*

Для снятия прав владельца (при необходимости):
```bash
adb shell dpm remove-active-admin com.kiosk.browser/.admin.KioskDeviceAdminReceiver
```

---

## 🚨 Аварийное управление через ADB

Если экран заблокирован или забыт PIN-код:

1. **Аварийный выход из киоска (Бэкдор):**
   ```bash
   adb shell am broadcast -a com.kiosk.RESET_LOCK --es key "SECRET_KIOSK_KEY_777"
   ```

2. **Принудительная перезагрузка страницы:**
   ```bash
   adb shell am broadcast -a com.kiosk.RELOAD_PAGE
   ```

3. **Смена стартового URL:**
   ```bash
   adb shell am broadcast -a com.kiosk.SET_URL --es url "https://my-pos-system.ru"
   ```

---

## 📚 Документация и полная история

Полное описание архитектуры, хронологии всех версий (v1.0.1 — v1.0.21), деталей реализации и решений проблем находится в документе:
👉 **[PROJECT_HISTORY_AND_ARCHITECTURE.md](PROJECT_HISTORY_AND_ARCHITECTURE.md)**
