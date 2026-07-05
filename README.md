# Детский Видеоплеер (KidsVideoPlayer)

Android-приложение — видеоплеер для детей с родительским контролем и режимом киоска.

**Package:** `com.dima.kidsvideoplayer` · **Min SDK:** 26 (Android 8.0) · **Target SDK:** 34

## Обзор

| Режим | Экран | Описание | Доступ |
|-------|-------|----------|--------|
| Детский | `KidPlayerScreen` | Полноэкранный libVLC плеер, навигация между видео, киоск | По умолчанию |
| Родительский | `ParentDashboardScreen` | Управление плейлистом, запуск видео | Long press шестерёнки 3 сек → PIN `1111` |
| Выбор файлов | `FilePickerScreen` | Браузер файлов на устройстве | Из родительского экрана |

## Структура проекта

```
video-game/
├── app/                          # Основное приложение
├── docs/                         # Инструкции по установке
├── scripts/                      # deploy-kiosk.sh
└── README.md
```

```
app/src/main/java/com/dima/kidsvideoplayer/
├── MainActivity.kt               # Точка входа: Compose, киоск lifecycle, immersive UI
├── KidsVideoApp.kt               # Application (singleton VideoPlayerManager)
├── AppState.kt                   # Общее состояние (менеджеры, lock-task flag)
│
├── admin/
│   ├── LockTaskManager.kt        # Киоск: start/stop Lock Task, Device Owner политики
│   └── MyDeviceAdminReceiver.kt  # Device Admin Receiver
│
├── data/
│   ├── VideoRepository.kt        # DataStore: список URI, выбор, развёрнутые папки
│   └── PlaybackStateRepository.kt  # SharedPreferences: позиция воспроизведения
│
├── navigation/
│   └── AppNavHost.kt             # kid_player ↔ parent_dashboard ↔ file_picker
│
├── player/
│   ├── VideoPlayerManager.kt     # libVLC: плейлист, next/prev, seek
│   └── SeekAccelerator.kt        # Ускорение перемотки при long press
│
├── ui/
│   ├── components/               # BounceButton, SeekButton, PinDialog, PinValidator
│   ├── screens/
│   │   ├── KidPlayerScreen.kt
│   │   ├── ParentDashboardScreen.kt
│   │   ├── FilePickerScreen.kt
│   │   ├── dashboard/VideoListGrouping.kt
│   │   ├── kidplayer/            # PlayerControlsOverlay, SecretDoorGesture
│   │   └── filepicker/
│   └── theme/
│
└── utils/
    ├── HuaweiStorageHelper.kt
    ├── VideoPathUtils.kt
    └── StoragePermissionHelper.kt
```

### Где что искать

| Задача | Файл |
|--------|------|
| Киоск | `admin/LockTaskManager.kt` |
| Автозапуск киоска | `navigation/AppNavHost.kt` |
| Родительский PIN | `ui/components/PinDialog.kt` |
| Секретная дверь | `ui/screens/kidplayer/SecretDoorGesture.kt` |
| Выбор видео | `ui/screens/FilePickerScreen.kt` |
| SD-карта Honor/Huawei | `utils/HuaweiStorageHelper.kt` |
| Список видео | `data/VideoRepository.kt` |
| Воспроизведение | `player/VideoPlayerManager.kt` (libVLC) |

## Архитектура

```
FilePickerScreen / ParentDashboardScreen
  → VideoRepository (DataStore)
  → KidPlayerScreen → VideoPlayerManager (libVLC)
  → PlaybackStateRepository (возобновление с позиции)
```

Подробнее: [docs/PHONE_SETUP.md](docs/PHONE_SETUP.md)

## Сборка и тесты

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest          # unit-тесты (Robolectric)
./gradlew connectedDebugAndroidTest  # Compose UI-тесты (нужен эмулятор)
```

## Технологический стек

| Компонент | Версия |
|-----------|--------|
| Kotlin | 1.9.22 |
| Jetpack Compose BOM | 2024.01.00 |
| libVLC | 3.6.2 |
| Navigation Compose | 2.7.6 |
| DataStore Preferences | 1.0.0 |
| AGP | 8.2.2 |

## Реализовано

- [x] Киоск (Lock Task Mode) с Device Owner политиками
- [x] libVLC для воспроизведения локальных видео
- [x] Файловый пикер с batch-добавлением
- [x] Сохранение позиции воспроизведения
- [x] PIN-защита добавления видео и выхода из приложения
- [x] Unit-тесты для repository, path utils, PIN, file system

## В планах

- [ ] Настраиваемый PIN
- [ ] UI для Device Admin provisioning
