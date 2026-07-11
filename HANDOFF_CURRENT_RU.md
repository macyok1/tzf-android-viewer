# Актуальная передача проекта TZF Viewer

## Обновление за 2026-07-12

### Интерактивный ViewCube

Плоская заглушка `OrientationCubeView` заменена на программно проецируемый 3D-куб
ориентации в стиле AutoCAD:

- куб синхронно вращается вместе с камерой;
- поддерживается выбор 6 граней, 12 рёбер и 8 углов;
- drag по кубу свободно изменяет yaw/pitch камеры;
- выбранный preset применяется с короткой плавной анимацией по кратчайшей дуге;
- видимая зона под пальцем подсвечивается;
- мультитач и отмена корректно завершают взаимодействие;
- ViewCube меняет только камеру и не затрагивает XYZ+RZ второго скана;
- чистая математика направлений и углов вынесена в `ViewCubeMath.java` и покрыта
  JVM-тестами.

Локально проходят `testDebugUnitTest` и `assembleDebug`, включая NDK-сборку для
четырёх ABI. Проверка на Samsung и GitHub Actions ещё не выполнена: устройство не
было подключено, а изменения пока не отправлены в GitHub.

Спецификация:

```text
docs/superpowers/specs/2026-07-12-interactive-viewcube-design.md
```

### Точный CAD-gizmo

Экранные эвристики picking/drag заменены на мировую ray/plane-математику:

- луч строится через inverse view-projection;
- поддерживаются perspective и ortho;
- X/Y/Z изменяют только выбранную мировую ось;
- центральная рукоятка изменяет только XY;
- кольцо изменяет только RZ;
- ось, направленная почти в камеру, не выбирается;
- размер gizmo остаётся примерно постоянным на экране;
- активная рукоятка подсвечивается;
- при мультитаче активный drag завершается;
- RX/RY не возвращались.

Математика вынесена в `GizmoMath.java`, состояние взаимодействия — в `TransformGizmo.java`. Добавлены JVM-тесты. Спецификация:

```text
docs/superpowers/specs/2026-07-12-precise-transform-gizmo-design.md
```

### Первая рабочая точная сшивка

Прежняя кнопка «Вписать» не выполняла регистрацию: она только устанавливала `zoom = 1`. Теперь:

- команда камеры переименована в «Показать всё»;
- в режиме сшивки добавлена отдельная кнопка «Точная сшивка»;
- текущая ручная XYZ+RZ-трансформация gizmo используется как начальное приближение;
- native C++ выполняет многоуровневый constrained point-to-plane ICP;
- решатель оптимизирует только X, Y, Z и RZ;
- используются spatial hash, локальные нормали, robust loss и несколько уровней voxel-разрешения;
- результат проверяется на отдельной выборке по RMS, P95 и доле перекрытия;
- ненадёжный результат не применяется;
- расчёт запускается в фоновом executor;
- загрузка нового файла, отмена или новый запуск блокируют публикацию устаревшего результата;
- UI показывает итоговые RMS, P95, overlap и число итераций.

Добавлены:

```text
app/src/main/cpp/tzf_registration.h
app/src/main/cpp/tzf_registration.cpp
app/src/main/java/ru/tzfviewer/RegistrationResult.java
```

Synthetic portable C++ test восстанавливает известную XYZ+RZ-трансформацию с заданного грубого приближения и проверяет отказ при недостатке данных.

Спецификация:

```text
docs/superpowers/specs/2026-07-12-constrained-precise-registration-design.md
```

### Очистка меню сшивки

Удалены ручные числовые поля X/Y/Z/RZ, поля шага и кнопки `+/-`. Предварительное совмещение выполняется CAD-gizmo. Оставлены read-only summary трансформации, видимость облаков, сброс, сохранение и восстановление.

В меню добавлены пороги RMS/P95. Они пока подписаны как единицы скана, а не миллиметры.

### CI и устройство

GitHub Actions теперь обязательно запускает:

- `testDebugUnitTest`;
- сборку debug APK;
- portable C++ tests, включая constrained registration;
- загрузку APK artifact.

Последний зелёный workflow:

```text
Run: 29156091882
Commit: 979534b
```

APK из этого workflow установлен на Samsung SM-G998B (`R5CR11SSLSZ`). Cold start успешен за 546 мс, ошибок `AndroidRuntime` после запуска нет.

### Последние коммиты 2026-07-12

```text
979534b Adapt registration normal radius to sparse levels
afaeacb Validate registration on original point samples
0c343c3 Report registration test diagnostics
8f930d3 Fix registration test pivot semantics
d464902 Add constrained precise scan registration
164e720 Design constrained precise scan registration
0912058 Run Android unit tests in CI
b02df33 Implement precise transform gizmo interaction
60fbdf1 Design precise transform gizmo interaction
```

### Важные ограничения текущей сшивки

- Реальная точность ещё не проверена на двух перекрывающихся TZF с эталоном из RealWorks/FARO или по контрольным маркам.
- Нельзя заявлять миллиметровую точность только по внутреннему ICP residual.
- Коэффициент единиц TZF ещё не подтверждён. Тестовые координаты имеют порядок десятков тысяч, поэтому нельзя автоматически считать их метрами.
- Значения RMS/P95 по умолчанию сейчас равны 3/8 единицам скана. После подтверждения масштаба их нужно связать с миллиметрами.
- Native-расчёт пока декодирует до 400 000 точек каждого TZF через существующий preview decoder. Это уже отдельная выборка для регистрации, но ещё не настоящий streaming/full-resolution session по всем тайлам.
- Кнопка отмены немедленно запрещает применение результата, но текущий native solver продолжает вычисление до возврата. Нужен нативный cancellation flag между итерациями.
- ProgressBar пока показывает неопределённый прогресс. Нет native callback с этапом, процентом и количеством соответствий.
- Алгоритм требует достаточно хорошего предварительного совмещения gizmo и не является глобальной регистрацией с нуля.

### Что делать следующим

1. Проверить «Точную сшивку» на двух разных перекрывающихся TZF и записать результат/время/метрики/logcat.
2. Подтвердить единицы TZF контрольным расстоянием и определить коэффициент перевода в миллиметры.
3. Сравнить XYZ+RZ с эталонной трансформацией RealWorks/FARO или контрольными марками.
4. После реального теста исправить sampling, normal estimation, thresholds и память, не подгоняя тест под желаемый ответ.
5. Сделать интерактивный AutoCAD-подобный куб вида, который вращается вместе с камерой и принимает клики по граням.
6. Добавить экспорт одного или двух сшитых облаков в ASC.
7. Позже отдельно проектировать Wi-Fi-передачу; в текущий этап она не входит.

---

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
