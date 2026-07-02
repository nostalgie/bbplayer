# Детский Видеоплеер (KidsVideoPlayer)

Android-приложение — видеоплеер для детей с родительским контролем и режимом киоска.

**Package:** `com.dima.kidsvideoplayer` · **Min SDK:** 26 (Android 8.0) · **Target SDK:** 34

## Обзор

| Режим | Экран | Описание | Доступ |
|-------|-------|----------|--------|
| Детский | `KidPlayerScreen` | Полноэкранный плеер, навигация между видео, киоск | По умолчанию |
| Родительский | `ParentDashboardScreen` | Управление плейлистом, запуск видео | Long press «v1.0» → PIN `1234` |
| Выбор файлов | `FilePickerScreen` | Браузер файлов на устройстве | Из родительского экрана |

## Структура проекта

```
video-game/
├── app/                          # Основное приложение
├── decoder_ffmpeg/               # FFmpeg-декодер (Media3 extension)
├── plans/                        # Внутренние заметки по разработке
└── README.md
```

```
app/src/main/java/com/dima/kidsvideoplayer/
├── MainActivity.kt               # Точка входа: Compose, киоск lifecycle, immersive UI
├── KidsVideoApp.kt               # Application
├── AppState.kt                   # Общее состояние (менеджеры, lock-task flag)
│
├── admin/
│   ├── LockTaskManager.kt        # Киоск: start/stop Lock Task, Device Owner политики
│   └── MyDeviceAdminReceiver.kt  # Device Admin Receiver
│
├── data/
│   ├── VideoRepository.kt        # DataStore: список URI видео, кэш метаданных
│   └── PlaybackStateRepository.kt  # SharedPreferences: позиция воспроизведения
│
├── navigation/
│   └── AppNavHost.kt             # kid_player ↔ parent_dashboard ↔ file_picker
│
├── player/
│   ├── VideoPlayerManager.kt     # ExoPlayer: плейлист, next/prev, repeat
│   ├── VideoCompatibilityChecker.kt  # Проверка кодеков перед воспроизведением
│   ├── CompatibilityResult.kt
│   └── SeekAccelerator.kt        # Ускорение перемотки при long press
│
├── ui/
│   ├── components/
│   │   ├── BounceButton.kt       # Анимированная кнопка
│   │   ├── PinDialog.kt          # PIN-диалог (по умолчанию 1234)
│   │   ├── SeekButton.kt
│   │   └── VerticalScrollbar.kt
│   ├── screens/
│   │   ├── KidPlayerScreen.kt    # Детский режим + секретная дверь
│   │   ├── ParentDashboardScreen.kt
│   │   ├── FilePickerScreen.kt
│   │   └── filepicker/
│   │       ├── FilePickerComponents.kt
│   │       └── FileSystemService.kt
│   └── theme/
│
└── utils/
    ├── HuaweiStorageHelper.kt    # SD-карта на Huawei/Honor
    └── PerformanceMonitor.kt
```

### Где что искать (шпаргалка)

| Задача | Файл |
|--------|------|
| Киоск: старт/стоп, политики | `admin/LockTaskManager.kt` |
| Автозапуск киоска при старте | `MainActivity.kt` |
| Device Admin + HOME launcher | `AndroidManifest.xml`, `res/xml/device_admin_policies.xml` |
| Родительский PIN | `ui/components/PinDialog.kt` |
| Секретная дверь | `ui/screens/KidPlayerScreen.kt` |
| Выбор видео | `ui/screens/FilePickerScreen.kt` |
| SD-карта Honor/Huawei | `utils/HuaweiStorageHelper.kt` |
| Список видео | `data/VideoRepository.kt` |
| ExoPlayer | `player/VideoPlayerManager.kt` |
| Навигация | `navigation/AppNavHost.kt` |
| Сборка | `app/build.gradle.kts` |

## Архитектура

### Навигация

```
AppNavHost
├── kid_player         → KidPlayerScreen        (startDestination)
├── parent_dashboard   → ParentDashboardScreen
└── file_picker        → FilePickerScreen
```

### Поток данных

```
FilePickerScreen / ParentDashboardScreen
  → VideoRepository (DataStore) + VideoCompatibilityChecker
  → KidPlayerScreen → VideoPlayerManager (ExoPlayer + decoder_ffmpeg)
  → PlaybackStateRepository (возобновление с позиции)
```

### Секретная дверь

```
Long press "v1.0" (1 сек) → PinDialog → PIN "1234"
  → ParentDashboardScreen (киоск остаётся активным)
```

Возврат: «Назад» или выбор видео → `enterKidMode()` (перезапуск Lock Task).

## Режим киоска

Киоск реализован через **Lock Task Mode** (`startLockTask` / `stopLockTask`).

### Два уровня

| Уровень | Условие | Возможности |
|---------|---------|-------------|
| Screen pinning | Без Device Owner | Закрепление экрана, нужно включить в настройках |
| Device Owner | `adb shell dpm set-device-owner` | Тихий киоск, HOME=приложение, нет шторки/блокировки |

### Что делает `LockTaskManager`

- `startKioskMode()` — whitelist (Device Owner) + `startLockTask()`
- `stopKioskMode()` — `stopLockTask()`
- `applyKioskPolicies()` — отключить статус-бар, HOME launcher, keyguard, stay-awake
- `removeKioskPolicies()` — снять политики при выходе

`MainActivity` автоматически запускает киоск при каждом старте.

## Сборка и установка

**Требование:** Java 17

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Или одной командой (см. `scripts/deploy-kiosk.sh`):

```bash
./scripts/deploy-kiosk.sh
```

## Запуск киоска на Honor 50 Lite (MagicOS 7.1, Android 13)

### 1. Подготовка телефона

1. **Настройки → О телефоне** → 7× нажать «Номер сборки»
2. **Для разработчиков → Отладка по USB** — включить
3. Подключить USB, подтвердить «Разрешить отладку»

### 2. Device Owner (рекомендуется для выделенного детского телефона)

**Важно:** на устройстве не должно быть аккаунтов (Google, Honor ID). Иначе — сброс к заводским настройкам.

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell dpm set-device-owner com.dima.kidsvideoplayer/.admin.MyDeviceAdminReceiver
adb shell dpm list-owners   # проверка
adb shell am start -n com.dima.kidsvideoplayer/.MainActivity
```

### 3. Добавить видео

1. Long press «v1.0» → PIN `1234`
2. Родительский экран → файловый пикер
3. Выдать **«Доступ ко всем файлам»**: Настройки → Приложения → Детский Видеоплеер → Разрешения → Файлы и медиа
4. Выбрать папку/видео → вернуться в детский режим

### 4. Выход

- **Родительский режим:** PIN → родительский экран (киоск остаётся)
- **Полный выход:** Parent Dashboard → Exit App (снимает политики киоска)

### Быстрый тест без Device Owner

1. Установить APK
2. **Настройки → Безопасность → Закрепление экрана** — включить
3. Запустить приложение, подтвердить закрепление

## Особенности Honor / MagicOS

- `HuaweiStorageHelper` распознаёт производителей `huawei` и `honor` для SD-карты
- На Android 13 нужен `MANAGE_EXTERNAL_STORAGE` для файлового браузера
- Приложение работает только в **landscape** (задано в манифесте)

## Технологический стек

| Компонент | Версия |
|-----------|--------|
| Kotlin | 1.9.22 |
| Jetpack Compose BOM | 2024.01.00 |
| Media3 ExoPlayer | 1.2.1 |
| Navigation Compose | 2.7.6 |
| DataStore Preferences | 1.0.0 |
| AGP | 8.2.2 |
| Gradle | 8.5 |

## Текущее состояние

### Реализовано

- [x] Киоск (Lock Task Mode) с автозапуском и Device Owner политиками
- [x] Три экрана: детский, родительский, файловый пикер
- [x] ExoPlayer + FFmpeg-декодер
- [x] Проверка совместимости видео
- [x] Сохранение позиции воспроизведения
- [x] PIN-защита родительского режима
- [x] Поддержка Huawei/Honor SD-карты

### В планах

- [ ] UI для Device Admin provisioning
- [ ] Настраиваемый PIN (сейчас захардкожен `1234`)
- [ ] Прогресс-бар видео
- [ ] Обработка ошибок ExoPlayer
