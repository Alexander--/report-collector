package net.sf.xfd.server;

import java.nio.ByteBuffer;

public final class Utf8 {
    // In UTF-8 ASCII characters exclusively start from 0, e.g. 0xxxxxxx
    private static final int ASCII_PATTERN = 0b01111111;

    private static boolean isAsciiByte(int b) {
        return (b | ASCII_PATTERN) == ASCII_PATTERN;
    }

    // In UTF-8 multibyte sequences exclusively start from two 1s, e.g. 11xxxxxx
    private static final int START_PATTERN = 0b11000000;

    private static boolean isStartByte(int b) {
        return (b & START_PATTERN) == START_PATTERN;
    }

    public static int previousCodePoint(ByteBuffer b, int start) {
        for (int i = start; i >= 0; --i) {
            int o = Byte.toUnsignedInt(b.get(i));

            if (isAsciiByte(o) || isStartByte(o)) {
                return i;
            }
        }

        return -1;
    }

    public static int nextCodePoint(ByteBuffer b, int start) {
        final int limit = b.limit();

        for (int i = start; i < limit; ++i) {
            int o = Byte.toUnsignedInt(b.get(i));

            if (isAsciiByte(o) || isStartByte(o)) {
                return i;
            }
        }

        return -1;
    }

    public static int findAscii(ByteBuffer utf8, byte[] bytes) {
        int limit = utf8.limit();
outer:
        for (int i = 0; i < limit; ++i) {
            for (int j = 0; j < bytes.length; ++j) {
                if (utf8.get(i) != bytes[j]) {
                    continue outer;
                }
            }

            return i;
        }

        return -1;
    }

    public static int findAscii(ByteBuffer utf8, int character, int start, int end) {
        int limit = Math.min(end, utf8.limit());

        for (int i = start; i < limit; ++i) {
            if (utf8.get(i) == character) {
                return i;
            }
        }

        return -1;
    }

    public static int findAscii(ByteBuffer utf8, int character) {
        int limit = utf8.limit();

        for (int i = 0; i < limit; ++i) {
            if (utf8.get(i) == character) {
                return i;
            }
        }

        return -1;
    }

    public static void append(ByteBuffer utf8, int start, String suffix) {

    }
}
