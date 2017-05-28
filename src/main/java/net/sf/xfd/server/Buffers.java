package net.sf.xfd.server;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public final class Buffers {
    private Buffers() {}

    private static final InputStream EOF = new InputStream() {
        @Override
        public int read() {
            return -1;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            return -1;
        }

        @Override
        public int read(byte[] b) {
            return -1;
        }

        @Override
        public int available() {
            return 0;
        }
    };

    public static InputStream emptyStream() {
        return EOF;
    }

    public static InputStream toInputStream(ByteBuffer buffer) {
        return toInputStream(buffer, 0, buffer.limit());
    }

    public static InputStream toInputStream(ByteBuffer buffer, int start, final int limit) {
        return new InputStream() {
            int offset = start;

            @Override
            public int available() throws IOException {
                return limit - offset;
            }

            @Override
            public long skip(long n) throws IOException {
                int oldOffset = offset;

                offset = Math.min(limit, (int) (offset + n));

                return offset - oldOffset;
            }

            @Override
            public int read() throws IOException {
                if (offset == limit) {
                    return -1;
                }

                return Byte.toUnsignedInt(buffer.get(offset++));
            }

            @Override
            public int read(byte[] b) throws IOException {
                if (offset == limit) {
                    return -1;
                }

                if (buffer.hasArray()) {
                    int bytes = Math.min(available(), b.length);

                    System.arraycopy(buffer.array(), buffer.arrayOffset() + offset, b, 0, bytes);

                    offset += bytes;

                    return bytes;
                } else {
                    return uglyRead(b, 0, b.length);
                }
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                if (offset == limit) {
                    return -1;
                }

                if (buffer.hasArray()) {
                    int bytes = Math.min(available(), len);

                    System.arraycopy(buffer.array(), buffer.arrayOffset() + offset, b, off, bytes);

                    offset += bytes;

                    return bytes;
                } else {
                    return uglyRead(b, off, len);
                }
            }

            private int uglyRead(byte[] b, int off, int length) {
                final int oldPos = buffer.position();
                final int oldLimit = buffer.limit();

                final int max = Math.min(offset + length, buffer.capacity());

                buffer.limit(max);
                buffer.position(offset);

                buffer.get(b, off, length);

                buffer.limit(oldLimit);
                buffer.position(oldPos);

                final int read = max - offset;

                offset += read;

                return read;
            }
        };
    }
}
