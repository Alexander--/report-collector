package net.sf.xfd.server;

import com.mysql.cj.jdbc.exceptions.MysqlDataTruncation;
import com.sun.mail.smtp.SMTPMessage;
import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.SqlJetTransactionMode;
import org.tmatesoft.sqljet.core.table.ISqlJetCursor;
import org.tmatesoft.sqljet.core.table.ISqlJetTable;
import org.tmatesoft.sqljet.core.table.SqlJetDb;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import java.io.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.sql.*;
import java.sql.PreparedStatement;
import java.util.*;

public final class Main {
    private static final String S2 =
            "INSERT INTO traces (hash, date, ipv4, uuid, blurb, trace) values (?, ?, ?, ?, ?, ?)";

    private static final String URL =
            "jdbc:mysql://mysql-x/x2440859_main?useServerPrepStmts=true&autoClosePStmtStreams=false&useLegacyDatetimeCode=false&noDatetimeStringSync=true";

    private static final String PROJECT_MAIL_CONFIG = "123";

    private static final int NEWLINE = 10;

    private static final byte[] ELLIPSIS = new byte[] { -30, -128, -90 };

    private static final int BLURB_CAP = 99;

    private static final int SHA_LENGTH = 40;

    private static final String LOCALHOST = "127.0.0.1";

    private static final long LOCALHOST_IP = 16777343L;

    private static final String PROJECT_NAME = "SF_USER";

    private static final String QUERY = "QUERY_STRING";

    private static final String LENGTH = "CONTENT_LENGTH";

    private static final String TYPE = "CONTENT_TYPE";

    private static final String METHOD = "REQUEST_METHOD";

    private static final String IP = "REMOTE_ADDR";

    private static final String TRUE_IP = "HTTP_X_REMOTE_ADDR";

    private static final String CLIENT_VERSION = "HTTP_X_APP_VERSION";

    private static final String UA = "HTTP_USER_AGENT";

    private static final String SCRIPT = "SCRIPT_FILENAME";

    private static final int MAX_BLOB_SIZE = 65535;

    public static void main(String... args) {
        System.out.print("Content-Type:text/plain\n");

        final byte[] hash;

        final long dataLength;

        try {
            final String method = System.getenv(METHOD);
            if (!"PUT".equalsIgnoreCase(method)) {
                wrongMethod();
                return;
            }

            final String query = System.getenv(QUERY);
            if (query == null || query.length() != SHA_LENGTH) {
                bail();
                return;
            }

            final String length = System.getenv(LENGTH);
            if (length == null || length.length() == 0 || length.length() > 5) {
                needLength();
                return;
            }

            dataLength = parseUnsigned(length, 10);

            if (dataLength >= MAX_BLOB_SIZE) {
                hugePayloadSize();
                return;
            }

            final String ua;
            final byte[] uuid;
            if ((ua = System.getenv(UA)) == null || ua.length() != 36 || (uuid = parseUUID(ua)) == null) {
                bail("Invalid User-Agent");
                return;
            }

            final String type = System.getenv(TYPE);
            if (!"application/octet-stream".equalsIgnoreCase(type)) {
                unsupportedType();
                return;
            }

            if (dataLength <= 0) {
                bail("Invalid content size");
                return;
            }

            hash = hex2bin(query);
            if (hash == null) {
                bail("Invalid query");
                return;
            }

            long versionCode = -1;
            final String clientVerCode = System.getenv(CLIENT_VERSION);
            if (clientVerCode != null && clientVerCode.length() != 0 && length.length() <= 10) {
                versionCode = parseUnsigned(clientVerCode, 10);
            }

            final String scriptName = System.getenv(SCRIPT);
            if (scriptName == null || scriptName.isEmpty()) {
                bail("Unable to locate self");
                return;
            }

            final File cgiDir = new File(scriptName).getParentFile();

            final File dbDir = new File(cgiDir.getParentFile(), "db");

            final File dbLoc = new File(dbDir, "reports.db");

            final SqlJetDb localDb = SqlJetDb.open(dbLoc, true);

            localDb.beginTransaction(SqlJetTransactionMode.READ_ONLY);

            final ISqlJetTable reportTable = localDb.getTable("reports");

            final ISqlJetCursor result = reportTable.lookup("idx_hash", new Object[]{ hash });

            if (!result.eof()) {
                localDb.beginTransaction(SqlJetTransactionMode.WRITE);
                try {
                    final HashMap<String, Object> map = new HashMap<>(2, 1.0f);

                    map.put("count", result.getInteger("count") + 1);

                    map.put("last_seen", System.currentTimeMillis());

                    result.updateByFieldNames(map);

                    localDb.commit();
                } finally {
                    localDb.close();
                }

                enough();

                return;
            }

            final String user = "x2440859_main";
            final String password = "123";

            try (Connection conn = DriverManager.getConnection(URL, user, password)) {
                conn.setAutoCommit(false);
                conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

                System.out.print(
                        "Pragma: no-cache\n" +
                        "Status: 100 Proceed\n" +
                        "\n");

                final byte[] arr = new byte[64 * 1024];

                final ByteBuffer header = ByteBuffer.wrap(arr);

                final FileChannel fc = new FileInputStream(FileDescriptor.in).getChannel();

                int lastRead = -1;

                do {
                    lastRead = fc.read(header);
                }
                while (lastRead != -1 && header.hasRemaining());

                int totalRead = header.position();

                if (totalRead == 0) {
                    System.exit(0);
                }

                InputStream blurb;

                int blurbEnd = Utf8.findAscii(header, NEWLINE, 0, BLURB_CAP);

                if (blurbEnd == -1) {
                    if (header.limit() >= BLURB_CAP) {
                        int charBound = Utf8.previousCodePoint(header, BLURB_CAP - 2);

                        if (charBound < 4) {
                            blurb = Buffers.emptyStream();
                        } else {
                            byte[] arrCopy = Arrays.copyOf(arr, charBound);

                            System.arraycopy(ELLIPSIS, 0, arrCopy, arrCopy.length - 3, ELLIPSIS.length);

                            blurb = Buffers.toInputStream(ByteBuffer.wrap(arrCopy));
                        }
                    } else {
                        blurb = Buffers.toInputStream(header);
                    }
                } else {
                    blurb = Buffers.toInputStream(header, 0, blurbEnd);
                }

                Integer created = null;

                try (PreparedStatement s = conn.prepareStatement(S2, Statement.RETURN_GENERATED_KEYS)) {
                    s.setBytes(1, hash);

                    s.setLong(2, System.currentTimeMillis());

                    s.setLong(3, getIp());

                    s.setBytes(4, uuid);

                    s.setBinaryStream(5, blurb);

                    s.setBinaryStream(6, Buffers.toInputStream(header, 0, totalRead), totalRead);

                    s.executeUpdate();

                    try (ResultSet createdId = s.getGeneratedKeys()) {
                        if (createdId.next()) {
                            created = createdId.getInt(1);
                        }
                    }

                    conn.commit();
                } finally {
                    conn.rollback();
                }

                if (created == null) {
                    throw new IllegalStateException("Failed to retrieve created entry id");
                }

                localDb.beginTransaction(SqlJetTransactionMode.WRITE);
                try {
                    final HashMap<String, Object> map = new HashMap<>(2, 1.0f);

                    map.put("_id", created);

                    map.put("hash", hash);

                    map.put("count", 1);

                    map.put("last_seen", System.currentTimeMillis());

                    reportTable.insertByFieldNames(map);

                    localDb.commit();
                } finally {
                    localDb.close();
                }

                final String projectName = System.getenv(PROJECT_NAME);
                if (projectName == null || projectName.isEmpty()) {
                    return;
                }

                final Properties props = new Properties();

                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.host", "prwebmail");
                props.put("mail.smtp.ssl.trust", "prwebmail");
                props.put("mail.smtp.ssl.enable", "true");
                props.put("mail.smtp.port", "465");

                final Session session = Session.getInstance(props);

                final SMTPMessage msg = new SMTPMessage(session);

                msg.setReturnOption(0);
                msg.setAllow8bitMIME(true);
                msg.setSubject("New error reports");
                msg.setFrom("\"Report Processing\" <fakenames@users.sourceforge.net>");
                msg.setNotifyOptions(SMTPMessage.NOTIFY_NEVER);
                msg.setText("You have new crash reports.", "UTF-8");

                Transport.send(msg, InternetAddress.parse("fakenames@users.sourceforge.net"), projectName, PROJECT_MAIL_CONFIG);
            }
        } catch (MessagingException mse) {
            fail(mse);
        } catch (MysqlDataTruncation crap) {
            fail(crap);
        } catch (SQLException | SqlJetException sqle) {
            fail(sqle);
        } catch (Throwable t) {
            fail(t);

            for (Map.Entry<?, ?> e : System.getenv().entrySet()) {
                System.out.println(e.getKey() + ":" + e.getValue());
            }
        }
    }

    private static byte[] parseUUID(String uuid) {
        final String[] components = uuid.split("-");

        if (components.length != 5)
            return null;

        return hex2bin(
                components[0] +
                components[1] +
                components[2] +
                components[3] +
                components[4]);
    }

    private static long getIp() throws UnknownHostException {
        byte[] ip = parseIp(System.getenv(TRUE_IP));

        if (ip == null) {
            ip = parseIp(System.getenv(IP));
        }

        if (ip == null || ip.length != 4) {
            return LOCALHOST_IP;
        }

        int result = 0;
        for (byte b: ip)
        {
            result = result << 8 | (b & 0xFF);
        }

        return Integer.toUnsignedLong(result);
    }

    private static byte[] parseIp(String ip) throws UnknownHostException {
        if (ip == null) {
            return null;
        }

        if (LOCALHOST.equals(ip)) {
            return null;
        }

        String[] parts = ip.split("\\.");

        final InetAddress address;

        if (parts.length != 4) {
            return null;
        }

        try {
            byte[] ipBits = new byte[4];
            for (int i = 0; i < 4; i++) {
                ipBits[i] = (byte) (Integer.parseInt(parts[i]) & 0xFF);
            }

            address = InetAddress.getByAddress(ipBits);
        } catch (NumberFormatException nfe) {
            // should not normally happen
            return null;
        }

        if (address.isSiteLocalAddress()) {
            return null;
        }

        return address.getAddress();
    }

    private static long parseUnsigned(String s, int radix) {
        long result = 0;
        int i = 0, len = s.length();
        long limit = -Long.MAX_VALUE;
        long multmin;
        int digit;

        multmin = limit / radix;

        while (i < len) {
            digit = Character.digit(s.charAt(i++), radix);

            if (digit < 0 || result < multmin) {
                return -1;
            }

            result *= radix;

            if (result < limit + digit) {
                return -1;
            }

            result -= digit;
        }

        return -result;
    }

    private static byte[] hex2bin(String hex) {
        if (hex.length() % 2 > 0) {
            return null;
        }

        byte[] r = new byte[hex.length() / 2];

        int len = hex.length();

        for (int i = 0; i < len; i += 2) {
            final int d2 = Character.digit((hex.charAt(i)), 16);

            if (d2 < 0) return null;

            final int d1 = Character.digit((hex.charAt(i + 1)), 16);

            if (d1 < 0) return null;

            r[i / 2] = (byte) (d1 | (d2 << 4));
        }

        return r;
    }

    private static void bail() {
        bail("Bad Request");
    }

    private static void bail(String s) {
        System.out.print(
                "Connection: close\n" +
                "Status: 400 " + s + "\n" +
                "\n");

        for (Map.Entry<?, ?> e : System.getenv().entrySet()) {
            System.out.println(e.getKey() + ":" + e.getValue());
        }
    }

    private static void wrongMethod() {
        System.out.print(
                "Connection: close\n" +
                "Status: 405 Method not allowed\n" +
                "\n");
    }

    private static void needLength() {
        System.out.print(
                "Connection: close\n" +
                "Status: 411 Length required\n" +
                "\n");
    }

    private static void hugePayloadSize() {
        System.out.print(
                "Connection: close\n" +
                "Status: 413 Payload Too Large\n" +
                "\n");
    }

    private static void unsupportedType() {
        System.out.print(
                "Connection: close\n" +
                "Status: 415 Unsupported media type\n" +
                "\n");
    }

    private static void enough() {
        System.out.print(
                "Connection: close\n" +
                "Status: 417 OK\n" +
                "\n");
    }

    private static void fail(Throwable error) {
        final Class<?> err = error.getClass();

        System.out.print(
                "Connection: close\n" +
                "Status: 500 " + err.getSimpleName() + "\n" +
                "\n");

        error.printStackTrace(System.out);
    }
}
