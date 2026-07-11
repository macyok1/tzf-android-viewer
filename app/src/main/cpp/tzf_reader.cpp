// Нативный модуль для потоковой распаковки патчей TZF.
// Он намеренно отделён от Android UI: декодер сможет тестироваться на ПК
// на тех же файлах и затем собираться NDK без изменения алгоритма.
#include <cstdint>
#include <vector>

namespace tzf {
struct Point { float x, y, z; };

// Контракт следующего этапа: открыть TZF, прочитать таблицу патчей и выдавать
// декодированные столбцы через callback. Реализация будет включать LZMA/Snappy
// и byte-transpose, используемые контейнером TZF.
class Reader {
public:
    bool open(int /*fileDescriptor*/) { return false; }
};
} // namespace tzf
