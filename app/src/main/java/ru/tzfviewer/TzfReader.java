package ru.tzfviewer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Начальная часть самостоятельного TZF-ридера. Все числа TZF — little-endian. */
final class TzfReader {
    static TzfHeader readHeader(InputStream stream) throws IOException {
        byte[] buffer = new byte[0x58];
        int offset=0,count;
        while(offset<buffer.length&&(count=stream.read(buffer,offset,buffer.length-offset))!=-1)offset+=count;
        if (offset != buffer.length) throw new IOException("Файл слишком мал для TZF");
        ByteBuffer bytes = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);
        long headerSize = Integer.toUnsignedLong(bytes.getInt(0x0c));
        long fileEnd = bytes.getLong(0x28);
        if (headerSize < 0x4c || headerSize > 1024 * 1024) throw new IOException("Неверный заголовок TZF");
        if (fileEnd <= headerSize) throw new IOException("Неверные смещения TZF");
        return new TzfHeader(headerSize, fileEnd);
    }
}
