package ru.tzfviewer;

final class TzfHeader {
    final long headerSize;
    final long fileEndOffset;
    TzfHeader(long headerSize, long fileEndOffset) { this.headerSize = headerSize; this.fileEndOffset = fileEndOffset; }
    String toDisplayString() { return "TZF распознан\nРазмер заголовка: " + headerSize + " байт\nКонец контейнера: " + fileEndOffset; }
}
