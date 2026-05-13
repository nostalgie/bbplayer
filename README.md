# 🎬 Детский Видеоплеер (KidsVideoPlayer / BBPlayer)

Android-приложение — видеоплеер для детей с родительским контролем и режимом киоска.

## 📋 Обзор

Приложение работает в **двух режимах**:

| Режим | Описание | Доступ |
|-------|----------|--------|
| **Детский режим** (`KidPlayerScreen`) | Полноэкранный видеоплеер. Кнопки навигации между видео. Невозможно выйти. | По умолчанию при запуске |
| **Родительский режим** (`ParentDashboardScreen`) | Выбор видео через системный пикер, управление списком. | Секретная дверь: long press «v1.0» → ПИН `1234` |

## 🛠 Технологический стек

| Компонент | Технология | Версия |
|-----------|-----------|--------|
| Язык | Kotlin | 1.9.22 |
| UI | Jetpack Compose (только Compose, без XML) | BOM 2024.01.00 |
| Видео | ExoPlayer (Media3) | 1.2.1 |
| Навигация | Compose Navigation | 2.7.6 |
| Хранение | DataStore Preferences | 1.0.0 |
| Файлы | SAF (`ActivityResultContracts.OpenDocument`) | — |
| Кiosk | Lock Task Mode (`startLockTask`/`stopLockTask`) | — |
| Min SDK | 26 (Android 8.0) | — |
| Target SDK | 34 (Android 14) | — |
| AGP | 8.2.2 | — |
| Gradle | 8.5 | — |

## 📁 Структура проекта

```
app/src/main/java/com/dima/kidsvideoplayer/
├── MainActivity.kt              # Точка входа: Compose, immersive, lock task lifecycle
├── KidsVideoApp.kt              # Application класс
│
├── admin/
│   ├── MyDeviceAdminReceiver.kt # Device Admin Receiver (обязателен для Lock Task)
│   └── LockTaskManager.kt       # Утилита: startLockTask/stopLockTask, Device Owner check
│
├── data/
│   └── VideoRepository.kt       # DataStore Preferences: хранение URI видео (pipe-separated)
│
├── navigation/
│   └── AppNavHost.kt            # NavHost: kid_player ↔ parent_dashboard
│
├── player/
│   └── VideoPlayerManager.kt    # ExoPlayer обёртка: playlist, next/prev, repeat mode
│
└── ui/
    ├── components/
    │   ├── BounceButton.kt      # Кастомная кнопка: spring bounce + пульсация
    │   └── PinDialog.kt         # PIN-код диалог: numpad 3x4, ПИН 1234
    │
    ├── screens/
    │   ├── KidPlayerScreen.kt   # Детский режим: ExoPlayer + навигация + секретная дверь
    │   └── ParentDashboardScreen.kt  # Родительский режим: SAF пикер + список видео
    │
    └── theme/
        ├── Color.kt             # Цвета: зелёный/оранжевый/синий/красный
        ├── Theme.kt             # Material3 тёмная тема
        └── Type.kt              # Типографика
```

## 🏗 Архитектура

### Навигация
```
AppNavHost (NavHost)
├── "kid_player"        → KidPlayerScreen (startDestination)
└── "parent_dashboard"  → ParentDashboardScreen
```

### Поток данных
```
ParentDashboardScreen
  │
  ├── SAF (OpenDocument) → выбирает видео
  ├── takePersistableUriPermission → сохраняет доступ
  └── VideoRepository.addVideoUri() → DataStore
                                       │
KidPlayerScreen ◄── VideoRepository.videoUris (Flow)
  │
  ├── VideoPlayerManager.setVideoList(uris) → ExoPlayer
  └── PlayerView (AndroidView) → отображение
```

### Секретная дверь (Kid → Parent)
```
Long press "v1.0" (3 сек)
  → PinDialog показан
  → ПИН "1234" введён
  → onExitKidMode() (stopLockTask)
  → navController.navigate("parent_dashboard")
```

### Возврат (Parent → Kid)
```
Кнопка "Назад 👶"
  → navController.popBackStack("kid_player")
  → onEnterKidMode() (startLockTask)
```

## 🔑 Ключевые классы

### `MainActivity`
- `setContent` с `KidsVideoPlayerTheme`
- `enterKidMode()` → `lockTaskManager.startKioskMode(this)`
- `exitKidMode()` → `lockTaskManager.stopKioskMode(this)`
- `hideSystemUI()` → immersive sticky mode
- `videoPlayerManager.release()` в `onDestroy()`

### `LockTaskManager`
- `isDeviceOwner()` → проверка Device Owner
- `startKioskMode(activity)` → whitelist + `startLockTask()`
- `stopKioskMode(activity)` → `stopLockTask()`
- **Важно:** Lock Task Mode пока НЕ активируется в приложении. Инфраструктура заложена для будущей реализации.

### `VideoRepository`
- DataStore Preferences с ключом `video_uris`
- URI хранятся как pipe-separated строка (`|`)
- `videoUris: Flow<List<String>>` — реактивный поток

### `VideoPlayerManager`
- ExoPlayer с `REPEAT_MODE_ALL`
- `setVideoList(uris, startIndex)` — загрузка плейлиста
- `next()` / `previous()` — навигация

### `BounceButton`
- `spring(dampingRatio = HighBouncy, stiffness = Medium)` при нажатии
- `infiniteRepeatable(tween(800))` idle пульсация
- `RoundedCornerShape(20.dp)`, BorderStroke, shadowElevation

### `PinDialog`
- Numpad 3×4 (1-9, 0, ⌫)
- PIN-индикатор (зелёные точки по количеству введённых цифр)
- ПИН по умолчанию: `1234` (параметр `correctPin`)

## 🔧 Сборка и запуск

```bash
# Сборка
./gradlew assembleDebug

# Установка на устройство
adb install app/build/outputs/apk/debug/app-debug.apk

# Device Owner (для Lock Task Mode — опционально)
adb shell dpm set-device-owner com.dima.kidsvideoplayer/.admin.MyDeviceAdminReceiver
```

**Требование:** Java 17 (JBR от Android Studio подходит).

## ⚠️ Текущее состояние реализации

### ✅ Реализовано
- [x] Проект собирается (BUILD SUCCESSFUL)
- [x] Навигация между экранами
- [x] ExoPlayer воспроизведение видео из URI
- [x] SAF пикер видео с persistable permissions
- [x] DataStore хранение списка URI
- [x] BounceButton с анимацией
- [x] PinDialog с numpad
- [x] Секретная дверь (long press v1.0 → PIN → parent)
- [x] Тёмная тема

### 🔲 Не реализовано (будущие шаги)
- [ ] Lock Task Mode активация (инфраструктура есть, вызовы закомментированы/не подключены)
- [ ] Device Owner provisioning
- [ ] Тестирование на реальном устройстве
- [ ] Обработка ошибок ExoPlayer (файл не найден, нет кодека и т.д.)
- [ ] Прогресс-бар видео
- [ ] Автовоспроизведение при добавлении первого видео
- [ ] Иконки приложений для разных плотностей
- [ ] Landscape/portrait обработка
- [ ] ProGuard правила для release

## 📝 Примечания для разработчиков

1. **Lock Task Mode** требует Device Owner или Device Admin + whitelist. Без этого `startLockTask()` покажет системный диалог. Для полноценного kiosk нужен Device Owner через `adb shell dpm set-device-owner`.

2. **SAF URI** — URI от SAF могут инвалидироваться если файл удалён/перемещён. Нет проверки валидности URI при загрузке.

3. **DataStore** — URI хранятся как строка с `|` разделителем. Если URI содержит `|`, будет проблема. Лучше заменить на JSON массив в будущем.

4. **ExoPlayer** — используется `PlayerView` через `AndroidView` в Compose. Контроллер скрыт (`useController = false`), навигация через кастомные кнопки.

5. **Compose** — `remember { videoPlayerManager.initialize() }` создаёт один экземпляр ExoPlayer на время жизни KidPlayerScreen. При пересоздании Composition (например, поворот) игрок пересоздаётся.
