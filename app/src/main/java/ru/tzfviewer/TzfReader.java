package ru.tzfviewer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Начальная часть самостоятельного TZF-ридера. Все числа TZF — little-endian. */
final class TzfReader {
    static TzfHeader readHeader(InputStream stream) throws IOException {
        byte[] buffer = stream.readNBytes(0x58);
        if (buffer.length != 0x58) throw new IOException("Файл слишком мал для TZF");
        ByteBuffer bytes = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);
        long headerSize = Integer.toUnsignedLong(bytes.getInt(0x0c));
        long fileEnd = bytes.getLong(0x28);
        if (headerSize < 0x4c || headerSize > 1024 * 1024) throw new IOException("Неверный заголовок TZF");
        if (fileEnd <= headerSize) throw new IOException("Неверные смещения TZF");
        return new TzfHeader(headerSize, fileEnd);
    }
}
