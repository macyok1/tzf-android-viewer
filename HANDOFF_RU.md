# Передача задачи: Android TZF Viewer

## Цель

Создать автономный APK, который напрямую открывает Trimble `.tzf` и отображает облако точек в 3D. Не подменять задачу просмотром предварительно экспортированного XYZ. Общение с пользователем — только по-русски.

Пользователь раздражён из-за отчётов без результата: не называть APK готовой, пока она реально не декодирует и не показывает точки.

## Пути

```text
Проект: C:\Users\PC\tzf-android-viewer
RealWorks: C:\Program Files\Trimble\Trimble RealWorks 12.0
API: C:\Program Files\Trimble\Trimble RealWorks 12.0\RealColor\TZF_API_x64.dll
Тест: C:\Users\PC\Desktop\ДГК\Отобразить.rwi\Scan_001.tzf
GitHub: https://github.com/macyok1/tzf-android-viewer
```

## Проверенные данные

Для `Scan_001.tzf`:

- размер 54 546 415 байт;
- сетка 1742 × 4123;
- 5 524 662 валидные точки;
- `TZF_GetXYZ` возвращает один столбец, `pointCount = 1742`;
- одна точка — три `float32`: `x,y,z`, 12 байт;
- столбец 1000, строки 1 и 3:

```text
-21.2216148, 15.4833479, 16861.0801
-69.6867065, 50.9067535, 16852.0801
```

## Что есть в проекте

`tools/export_oracle_column.ps1` — эталонный экспорт одного столбца через установленный Windows `TZF_API_x64.dll`. Это только oracle для сравнения независимого декодера.

```powershell
& 'C:\Users\PC\tzf-android-viewer\tools\export_oracle_column.ps1' `
  -InputFile 'C:\Users\PC\Desktop\ДГК\Отобразить.rwi\Scan_001.tzf' `
  -Column 1000 `
  -OutputFile 'C:\Users\PC\tzf-android-viewer\testdata\scan001-column1000.csv'
```

`tools/inspect_tzf_dll.py` — дизассемблирование экспортов DLL. Зависимости уже установлены:

```powershell
python -m pip install --user capstone pefile
python tools/inspect_tzf_dll.py 'C:\Program Files\Trimble\Trimble RealWorks 12.0\RealColor\ZZIO_x64.dll' GetPatch
```

Android-каркас и GitHub Actions уже есть. Текущая APK открывает только заголовок TZF — это заглушка, не результат.

## Реверс: установленное

Зависимости: `TZF_API_x64.dll -> ZZIO_x64.dll -> ZZCodecs_x64.dll -> ZZBase_x64.dll`.

Найдены функции:

```text
TZFScanReader::InitFile
TZFScanReader::FillupScanProperties
TZFScanReader::ReadTailHeaders
TZFScanReader::AnalyseCodecs
TZFScanReader::SetupComponentMap
TZFScanReader::GetPatch
TZFScanReader::GetPatchCompressed
TZFScanReader::GetPatchVertices
```

Последовательность:

```text
InitFile -> FillupScanProperties -> ReadTailHeaders -> AnalyseCodecs
-> SetupComponentMap -> GetPatchCompressed -> виртуальный вызов кодека
```

Подтверждено:

- TZF хранит сжатые патчи, не простой массив XYZ;
- `GetPatchCompressed` берёт пару `pointer + size` из карты и передаёт её объекту кодека;
- карта компонента около `TZFScanReader + 0x4B8`;
- интерфейс файлового/кодек-объекта около `TZFScanReader + 0xC18`;
- `ReadTailHeaders` читает хвостовые заголовки с 64-битными идентификаторами;
- в `ZZCodecs_x64.dll` есть LZMA, Snappy, Zlib, JPEG и варианты `*_Transpose_Derive`.

Не восстановлено: формат таблицы `патч -> offset,size`, сопоставление идентификатор → кодек, преобразование после распаковки.

В offset `0x0C` тестовых файлов лежит `uint32 LE = 76` — размер первого заголовка. Файл не является обычным ZIP.

## ETW-трасса

Успешно снята File I/O трасса штатного ридера во время чтения столбца 1000:

```text
C:\Users\PC\tzf-android-viewer\trace-fileio.etl
```

Размер около 97 МБ. `tracerpt` выводит только header, kernel File I/O он не декодирует.

Для разбора устанавливается Windows Performance Toolkit (WPT) из Windows ADK. Последняя проверка: процессы `adksetup.exe` ещё активны, WPT пока не появился.

Проверить:

```powershell
Get-ChildItem 'C:\Program Files (x86)\Windows Kits\10\Windows Performance Toolkit'
Get-Process adksetup -ErrorAction SilentlyContinue
```

После появления `wpaexporter.exe` или `xperf.exe` экспортировать File I/O, отфильтровать путь `Scan_001.tzf`, получить offset/length чтений. Это должно привязать патчи к реальным байтам файла.

Админ-скрипты повторной трассы:

```text
tools\trace_tzf_fileio_admin.ps1
tools\run_trace_admin.cmd
```

Пользователь готов подтверждать UAC.

## Следующие шаги

1. Дождаться WPT и распарсить ETL.
2. Сопоставить реальные offsets/length с `ReadTailHeaders` и `SetupComponentMap`.
3. Восстановить таблицу патчей и GUID/кодек.
4. Написать независимый ПК-декодер одного патча.
5. Сравнить с oracle для столбца 1000.
6. Расширить на весь файл, затем перенести C++ в Android NDK и добавить OpenGL ES viewer.

## GitHub

GitHub CLI уже авторизован как `macyok1`. Полезные коммиты:

```text
e131e18 Add TZF DLL inspection tool
1f25016 Add Windows TZF point oracle for decoder tests
```

Перед push использовать обычные `git add`, `git commit`, `git push` из `C:\Users\PC\tzf-android-viewer`.

## Ограничение

Windows `TZF_API_x64.dll` нельзя использовать в APK: это x64 DLL с Windows-зависимостями. Она допустима только как эталон для тестов.
