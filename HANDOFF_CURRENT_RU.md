# Актуальная передача проекта TZF Viewer

Дата: 2026-07-11  
Ветка: `main`  
Репозиторий: `https://github.com/macyok1/tzf-android-viewer`

## Цель проекта

Автономное Android-приложение для прямого чтения Trimble TZF без RealWorks и без предварительного экспорта в XYZ. Основные режимы:

- «Просмотр» одного облака;
- «Сшивка» опорного и перемещаемого облаков.

## Что уже работает

### TZF и рендеринг

- Нативный C++ decoder читает TZF и выдаёт XYZ через JNI.
- Поддерживаются используемые тестовыми файлами Snappy/Transpose/Derive/Snap2 pipelines.
- OpenGL ES 2.0 отображает до 150 000 preview-точек.
- Есть перспектива/ортографическая проекция, орбитальная камера, масштабирование, стандартные виды и измерение.
- Основной скан голубой, перемещаемый — янтарный.

### Интерфейс

- Приложение по умолчанию открывает режим «Просмотр».
- «Сшивка» использует отдельную нижнюю инструментальную панель.
- Можно загружать основной и второй TZF, переключать видимость, сбрасывать, сохранять и восстанавливать трансформацию.
- Интерфейс перенесён из однострочного программного layout в Android resources.
- Исправлен стартовый crash из-за создания `PointCloudView` через XML: теперь GL view создаётся явно и добавляется в `viewportContainer`.

### Ограниченная сшивка

В соответствии с геодезическим требованием второй скан имеет только четыре степени свободы:

- translation X;
- translation Y;
- translation Z;
- rotation Z.

RX и RY удалены из UI, состояния и renderer API. Нивелированный сканером реальный уклон нельзя изменить.

### CAD-навигация

- Вращается орбитальная камера, а не model matrix скана.
- Добавлена мировая XY-сетка на `Z = 0`.
- Добавлены мировые оси X/Y/Z.
- Добавлен CAD-gizmo второго скана: оси XYZ, XY drag и кольцо rotation Z.
- Числовые поля синхронизируются с интерактивной трансформацией.
- Исправлен `BufferOverflowException` кольца gizmo увеличением постоянного GL buffer.

### Preview

Основной скан теперь публикуется в три стадии:

1. быстрый preview;
2. промежуточное уточнение;
3. полный preview до 150 000 точек.

Второй скан публикуется в две стадии. Интерфейс остаётся отзывчивым между стадиями.

### Сборка

- Добавлен Gradle Wrapper 8.7.
- CI переведён на `./gradlew`.
- GitHub Actions собирает debug APK и запускает portable C++ tests.
- Последние сборки CI зелёные.

## Проверено на реальном устройстве

Устройство:

```text
Samsung SM-G998B (Galaxy S21 Ultra)
ADB serial: R5CR11SSLSZ
```

ADB установлен локально:

```text
C:\Users\PC\Android\Sdk\platform-tools\adb.exe
```

На устройстве проверены:

- cold start без AndroidRuntime crash;
- открытие реального TZF;
- многоступенчатый preview;
- отображение полного облака;
- мировая сетка и оси;
- переход в режим сшивки;
- одновременное отображение двух облаков;
- отображение CAD-gizmo;
- отсутствие прежнего GL buffer overflow после исправления.

Тестовый файл на ПК:

```text
C:\Users\PC\Desktop\ДГК\Отобразить.rwi\Scan_001.tzf
```

Копия была отправлена на телефон:

```text
/sdcard/Download/Scan_001.tzf
```

## Последние важные коммиты

```text
6611b71 Fix stitching gizmo buffer overflow
a9514c8 Add CAD grid and constrained stitching gizmo
77cb1af Design CAD navigation grid and stitching gizmo
1e379fc Fix point cloud view startup crash
ddf325b Add viewer and stitching workspace
1796e6c Document viewer and stitching workspace design
```

## Спецификации

```text
docs/superpowers/specs/2026-07-11-viewer-stitching-workspace-design.md
docs/superpowers/specs/2026-07-11-cad-navigation-grid-gizmo-design.md
TZF_FORMAT.md
```

## Что нужно сделать следующим

### 1. Довести CAD-gizmo

Текущий picking и drag являются первой рабочей реализацией на экранных расстояниях. Нужно заменить их математически точным ray/plane interaction:

- построение луча из inverse view-projection;
- axis drag через проекцию пересечения на выбранную ось;
- XY drag через пересечение с плоскостью `Z = const`;
- Z drag через camera-facing plane с проекцией на Z;
- rotation Z через два вектора на мировой XY-плоскости;
- постоянный экранный размер gizmo;
- подсветка активной рукоятки;
- корректный выбор рукоятки независимо от ориентации камеры.

Не возвращать RX/RY.

### 2. Улучшить сетку

Текущая сетка рабочая, но визуально слишком плотная и может перекрывать облако. Нужно:

- adaptive major/minor spacing по масштабу;
- более тонкие и прозрачные minor lines;
- ограничение видимого extent камеры;
- отдельные яркие X/Y axes;
- подпись текущего шага;
- опциональное скрытие сетки;
- убрать наложение сетки поверх полезной геометрии за счёт цветов/depth/blending.

Сетка обязана оставаться строго на `Z = 0`.

### 3. Настоящий потоковый preview

Сейчас добавлена промежуточная стадия, но каждый JNI-вызов заново декодирует набор тайлов. Нужен настоящий `PreviewSession`:

- native API декодирует тайлы один раз;
- Java получает порции точек callback-ом или через последовательный `decodeNextChunk`;
- порядок тайлов пространственно равномерный по всему скану;
- renderer дополняет буфер без замены уже показанных точек;
- прогресс показывает обработанные тайлы/точки;
- generation cancellation останавливает старую сессию.

Это главный оставшийся performance debt.

### 4. Разделить крупные классы

`PointCloudView.java` сейчас намеренно собран быстро и слишком плотный. Разнести на:

- `CameraState`;
- `StitchTransform`;
- `CloudRenderer`;
- `WorldGridRenderer`;
- `TransformGizmo`;
- `GestureController`.

Добавить JVM-тесты для матриц и ограничений XYZ+RZ.

### 5. Проверка поведения

- Убедиться, что camera orbit никогда не меняет `StitchTransform`.
- Проверить gizmo каждой оси на телефоне.
- Проверить portrait/landscape.
- Проверить два разных TZF, а не один файл дважды.
- Проверить сохранение/восстановление только четырёх значений.
- Снять `adb logcat` после длительной работы и нескольких загрузок.

## Команды проверки

```powershell
$adb = 'C:\Users\PC\Android\Sdk\platform-tools\adb.exe'
& $adb devices -l
& $adb logcat -c
& $adb shell am start -W -n ru.tzfviewer/.MainActivity
& $adb logcat -d -v threadtime AndroidRuntime:E '*:S'
```

CI:

```powershell
gh run list --workflow build-apk.yml --limit 3
gh run watch <run-id> --exit-status
gh run download <run-id> -n tzf-viewer-debug-apk -D build-artifact\run-<run-id>
```

Из-за разных ephemeral debug keystore GitHub Actions обновление APK иногда возвращает `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Для тестовой сборки:

```powershell
& $adb uninstall ru.tzfviewer
& $adb install build-artifact\run-<run-id>\app-debug.apk
```

## Важные ограничения

- Windows Trimble DLL нельзя включать в APK; она используется только как oracle.
- Не утверждать, что координаты в метрах, пока единицы не подтверждены метаданными TZF.
- Не разрешать rotation X/Y второго скана.
- Не смешивать camera matrix с model transform скана.
- Перед передачей APK обязательно проверять GitHub Actions и запуск на телефоне через ADB.
