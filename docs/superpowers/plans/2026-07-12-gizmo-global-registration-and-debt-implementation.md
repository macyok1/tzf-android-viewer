# План реализации gizmo, глобальной регистрации и технического долга

Основание: `docs/superpowers/specs/2026-07-12-gizmo-global-registration-and-technical-debt-design.md`.

## Фаза 1. Стабильный gizmo M

1. Добавить unit-тесты преобразования мировой pose в локальную pose вложенного узла и жёсткого движения группы в `ProjectModelTest`.
2. Добавить тестируемую модель стабильного scene frame, которая не пересчитывает центр камеры при каждом изменении pose.
3. Изменить `PointCloudView.CloudRenderer`: обновление pose облака не должно автоматически менять `cx/cy/cz/span`; frame пересчитывается при добавлении, удалении, видимости и явном `fitView`.
4. Разделить синхронизацию scene pose и явную команду fit, не меняя camera gestures.
5. Проверить `TransformGizmo`: результат drag остаётся мировой `XYZ+RZ` pose выбранного узла; `MainActivity` единожды переводит её в пространство родителя.
6. Запустить `testDebugUnitTest`, `assembleDebug` и lint.

## Фаза 2. Компактная панель и состояния

1. Добавить `RegistrationUiState` и unit-тесты переходов idle/running/preview/error/cancelled.
2. Переделать `activity_main.xml`: заменить `pairCard` одной нижней строкой A, добавить быстрый и глубокий режимы, подробности оставить во взаимоисключающей overlay-панели.
3. Вынести orchestration расчёта из `MainActivity` в `RegistrationController` с generation id.
4. Добавить отдельный candidate transform, кнопки применения и отмены; не записывать candidate в `ProjectModel` до подтверждения.
5. Проверить узкие экраны, portrait/landscape, Back и accessibility labels.

## Фаза 3. Надёжная быстрая регистрация

1. Расширить portable C++ тесты: большие ошибки начального `XYZ`, `RZ`, выбросы, малое перекрытие и группы разной плотности.
2. Разделить optimization sample и validation sample.
3. Улучшить robust correspondence filtering и многоуровневый capture range, не добавляя `RX/RY`.
4. Возвращать структурированную причину отказа, метрики и диагностические счётчики.
5. Не применять результат при отказе; для успешного быстрого режима сохранить текущий прямой apply.

## Фаза 4. Глубокий поиск

1. Добавить C++ API coarse hypothesis search по `XYZ+RZ` и детерминированные synthetic tests.
2. Построить равномерные coarse samples и несколько геометрически различных гипотез.
3. Ранжировать hypotheses robust overlap, уточнять shortlist coarse ICP и валидировать лучших точным ICP.
4. Вычислять ambiguity по разнице первого и второго кандидатов.
5. Подключить глубокий режим через `RegistrationController` и показывать только preview candidate.
6. Добавить apply/cancel и восстановление исходного изображения.

## Фаза 5. Потоковая registration session

1. Расширить native preview/session API чтением пространственно перемешанных тайлов для регистрации.
2. Убрать фиксированное декодирование `400 000` точек из `MainActivity`.
3. Добавить cancellation flag и progress callback между тайлами, гипотезами, уровнями и итерациями.
4. Балансировать выборки потомков группы.
5. Добавить тесты освобождения session, отмены и потерянного URI.

## Фаза 6. Viewer debt

1. Извлечь из `PointCloudView` `CameraState`, `SceneTransformResolver`, `GestureController`, `CloudRenderer` и `WorldGridRenderer`, сохраняя поведение тестами.
2. Сделать preview действительно добавочным: новые чанки без повторного декодирования и пересоздания старых GPU buffers.
3. Добавить измерение сглаженного FPS/frame time и подключить `AUTO` budget с гистерезисом.
4. Скрывать хвост чанков при уменьшении бюджета без уничтожения полезного кэша.
5. Улучшить сетку и исправить оставшиеся mojibake-строки.

## Фаза 7. Приёмка

1. Полный набор JVM и portable C++ тестов.
2. `assembleDebug`, release build и Android lint для четырёх ABI.
3. Samsung: одиночный и групповой `M`, все ручки, любой ракурс, быстрый/глубокий режим, preview/apply/cancel, 3+ TZF.
4. Проверка восстановления проекта, смены ориентации, Back, принудительного закрытия, памяти, FPS и logcat.
5. Обновить `HANDOFF_CURRENT_RU.md` фактическими результатами и ограничениями.

Каждая фаза завершается отдельным проверенным коммитом. Исходники следующей фазы не смешиваются с предыдущей до прохождения её тестов.
