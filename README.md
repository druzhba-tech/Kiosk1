# Kiosk Browser & Launcher для Android

Полнофункциональное нативное киоск-приложение (аналог Fully Kiosk Browser) с технологичным дизайном Cyber-HUD на Jetpack Compose Material 3.

---

## 🚀 Команды для сборки и установки (Терминал)

### 1. Переход в папку проекта:
```bash
cd "C:\Users\Mohchehra\.gemini\antigravity-ide\scratch\kiosk-browser"
```

### 2. Сборка Debug APK:
*(Если установлен Gradle или Android Studio)*
```bash
gradle assembleDebug
```
*Либо откройте папку проекта напрямую в Android Studio и нажмите **Run / Debug**.*

Готовый APK файл будет расположен по пути:
`app/build/outputs/apk/debug/app-debug.apk`

---

### 3. Установка на подключенный планшет через ADB:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

### 4. Активация режима владельца устройства (Device Owner):
> **Важно:** На планшете не должно быть добавленных аккаунтов (Google/Telegram).
```bash
adb shell dpm set-device-owner com.kiosk.browser/.admin.KioskDeviceAdminReceiver
```
*После этой команды становятся активны:*
- Блокировка безопасного режима (`Safe Mode`)
- Полный запрет отключения и выхода без PIN
- Блокировка USB-передачи данных
- Полное отключение шторки статус-бара

---

## ⚙️ Управление и секретные функции

1. **Вход в меню настроек:**
   - Быстро нажмите **5 раз** в правый верхний угол экрана.
   - Появится диалог ввода PIN-кода.
   - PIN по умолчанию: **`1234`**

2. **Аварийный сброс блокировки через ADB (Бэкдор):**
   Если вы забыли PIN или заблокировали экран:
   ```bash
   adb shell am broadcast -a com.kiosk.RESET_LOCK --es key "SECRET_KIOSK_KEY_777"
   ```

3. **Удаленная перезагрузка страницы через ADB:**
   ```bash
   adb shell am broadcast -a com.kiosk.RELOAD_PAGE
   ```

4. **Удаленная установка URL через ADB:**
   ```bash
   adb shell am broadcast -a com.kiosk.SET_URL --es url "http://192.168.1.100:8123"
   ```

5. **Локальный Web REST Server:**
   Откройте в браузере с компьютера: `http://<IP_ПЛАНШЕТА>:8080/`
