"""Извлечение экспортов и дизассемблирование функций TZF из DLL RealWorks.

Использование:
  python tools/inspect_tzf_dll.py "C:\\Program Files\\Trimble\\...\\ZZIO_x64.dll" Open

Скрипт не изменяет DLL и нужен для переноса алгоритма в Android NDK.
"""
import sys
from pathlib import Path

import pefile
from capstone import Cs, CS_ARCH_X86, CS_MODE_64


def main() -> int:
    if len(sys.argv) < 2:
        print("Нужен путь к DLL")
        return 2
    dll = Path(sys.argv[1])
    needle = sys.argv[2].lower() if len(sys.argv) > 2 else "tzf"
    pe = pefile.PE(str(dll))
    exports = getattr(pe, "DIRECTORY_ENTRY_EXPORT", None)
    if exports is None:
        print("Экспорты не найдены")
        return 1

    md = Cs(CS_ARCH_X86, CS_MODE_64)
    found = []
    for symbol in exports.symbols:
        name = symbol.name.decode("ascii", "replace") if symbol.name else f"ordinal_{symbol.ordinal}"
        if needle not in name.lower():
            continue
        rva = symbol.address
        offset = pe.get_offset_from_rva(rva)
        code = pe.__data__[offset:offset + 192]
        found.append(name)
        print(f"\n{name}\nRVA 0x{rva:X}")
        for instruction in md.disasm(code, pe.OPTIONAL_HEADER.ImageBase + rva):
            print(f"  {instruction.address:016X}  {instruction.mnemonic:7} {instruction.op_str}")
            if instruction.mnemonic == "ret":
                break
    print(f"\nНайдено функций: {len(found)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
