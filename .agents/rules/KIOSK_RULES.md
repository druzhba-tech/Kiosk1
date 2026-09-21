# Kiosk Browser Android — Правила и контекст проекта

## О проекте
- **Приложение:** Kiosk Browser Android (`com.kiosk.browser`)
- **Репозиторий:** `druzhba-tech/Kiosk1` (ветка `main`)
- **Основная папка:** `c:\Users\Mohchehra\Desktop\kiosk-browser-android`
- **Файл полной истории и архитектуры:** [PROJECT_HISTORY_AND_ARCHITECTURE.md](file:///c:/Users/Mohchehra/Desktop/kiosk-browser-android/PROJECT_HISTORY_AND_ARCHITECTURE.md) и на рабочем столе: `C:\Users\Mohchehra\Desktop\KIOSK_PROJECT_DOCUMENTATION.md`.

## Критические требования при доработках:
1. **Keystore и цифровая подпись**:
   - Ключ цифровой подписи находится в `app/kiosk.keystore` (пароль `kioskpassword`, алиас `kiosk`).
   - НИКОГДА не менять и не удалять данный keystore, иначе у пользователей возникнет конфликт подписей при OTA-обновлении.
   - В `app/build.gradle.kts` всегда должны быть активны `enableV1Signing = true` и `enableV2Signing = true`.
2. **Нумерация версий**:
   - При каждой новой сборке повышать `versionCode` на +1 и инкрементировать `versionName` в `app/build.gradle.kts`.
3. **Выкатка на GitHub**:
   - Поскольку git CLI может отсутствовать в Windows PATH, пуш осуществляется через скрипт `scratch/push_to_github.ps1`, использующий GitHub REST API.
   - Сборка и релиз происходят автоматически через GitHub Actions workflow `.github/workflows/build-apk.yml`.
4. **Ключевые модули**:
   - `DeviceOwnerManager.kt`: системные политики Kiosk, лаунчер, блокировка вызовов, SMS, Hotspot, Safe Mode.
   - `KioskUpdateManager.kt`: атомарная загрузка обновлений через `.apk.tmp` с проверкой целостности Zip (`isValidApk`) перед установкой.
   - `WifiControlDialog.kt`: определение статуса Wi-Fi и 4G через `ConnectivityManager` и `LinkProperties`, вызов системных панелей Android.
   - `KioskWebScreen.kt`: WebView с защитой от зума и умным `SwipeRefreshLayout` (срабатывает только на самом верху страницы).
