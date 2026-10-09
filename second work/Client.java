import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/**
 * Multi-threaded download client (Java 21+).
 * Usage:
 *   java Client <host> <port> LIST
 *   java Client <host> <port> <file> <trad|nio> <workers> [outputFile]
 */
public class Client {
    static String host;
    static int port;

    public static void main(String[] a) throws Exception {
        if (a.length < 3) { usage(); return; }
        host = a[0];
        port = Integer.parseInt(a[1]);

        if (a[2].equalsIgnoreCase("LIST")) { list(); return; }
        if (a.length < 5) { usage(); return; }

        String name = a[2];
        boolean nio = a[3].equalsIgnoreCase("nio");
        int workers = Integer.parseInt(a[4]);
        Path out = Path.of(a.length > 5 ? a[5] : "downloaded_" + name);

        long size = info(name);

        // Pre-size the destination so positional writes / transferFrom never start beyond EOF.
        try (RandomAccessFile raf = new RandomAccessFile(out.toFile(), "rw")) {
            raf.setLength(size);
        }

        // Non-overlapping ranges; the last worker takes the remainder.
        long chunk = size / workers;
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        List<Future<?>> futures = new ArrayList<>();

        long t0 = System.nanoTime();
        for (int i = 0; i < workers; i++) {
            final long off = i * chunk;
            final long len = (i == workers - 1) ? size - off : chunk;
            final int id = i;
            if (len == 0) continue;
            futures.add(pool.submit(() -> {
                fetch(name, off, len, out, nio);
                return null;
            }));
        }
        try {
            for (Future<?> f : futures) f.get(); // propagates any worker failure
        } finally {
            pool.shutdown();
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;

        long actual = Files.size(out);
        double mbps = (size / 1048576.0) / Math.max(ms, 1) * 1000.0;
        String hash = sha256(out);
        System.out.printf("RESULT mode=%s workers=%d bytes=%d time_ms=%d mbps=%.2f size_ok=%b sha256=%s%n",
                nio ? "nio" : "trad", workers, size, ms, mbps, actual == size, hash);
    }

    static void usage() {
        System.err.println("Usage: java Client <host> <port> LIST");
        System.err.println("       java Client <host> <port> <file> <trad|nio> <workers> [outputFile]");
    }

    // ---------- control commands ----------

    static void list() throws IOException {
        try (Socket s = new Socket(host, port)) {
            s.getOutputStream().write("LIST\n".getBytes());
            InputStream in = s.getInputStream();
            String line;
            while ((line = readLine(in)) != null && !line.equals("END")) System.out.println(line);
        }
    }

    static long info(String name) throws IOException {
        try (Socket s = new Socket(host, port)) {
            s.getOutputStream().write(("INFO " + name + "\n").getBytes());
            String r = readLine(s.getInputStream());
            if (r == null || !r.startsWith("SIZE ")) throw new IOException("INFO failed: " + r);
            return Long.parseLong(r.substring(5).trim());
        }
    }

    // ---------- one worker = one connection, one range ----------

    static void fetch(String name, long off, long len, Path out, boolean nio) throws IOException {
        String cmd = "GET " + name + " " + off + " " + len + "\n";
        // each worker opens its own FileChannel -> independent handle, positional writes
        try (SocketChannel sc = SocketChannel.open(new InetSocketAddress(host, port));
             FileChannel fc = FileChannel.open(out, StandardOpenOption.WRITE)) {

            ByteBuffer c = ByteBuffer.wrap(cmd.getBytes());
            while (c.hasRemaining()) sc.write(c);

            String header = readLine(sc);
            if (header == null || !header.equals("OK " + len))
                throw new IOException("worker@" + off + " bad response: " + header);

            if (nio) fetchNio(sc, fc, off, len);
            else fetchTrad(sc, fc, off, len);
        }
    }

    /** Traditional I/O: InputStream.read -> FileChannel.write(buffer, position) */
    static void fetchTrad(SocketChannel sc, FileChannel fc, long off, long len) throws IOException {
        InputStream in = sc.socket().getInputStream();
        byte[] buf = new byte[64 * 1024];
        long pos = off, rem = len;
        while (rem > 0) {
            int n = in.read(buf, 0, (int) Math.min(buf.length, rem));
            if (n < 0) throw new EOFException("connection closed early at " + pos);
            ByteBuffer bb = ByteBuffer.wrap(buf, 0, n);
            while (bb.hasRemaining()) pos += fc.write(bb, pos);
            rem -= n;
        }
    }

    /** NIO native transfer: FileChannel.transferFrom(socketChannel, position, count) in a loop */
    static void fetchNio(SocketChannel sc, FileChannel fc, long off, long len) throws IOException {
        long pos = off, rem = len;
        while (rem > 0) {
            long n = fc.transferFrom(sc, pos, rem);
            if (n <= 0) throw new EOFException("transferFrom made no progress at " + pos);
            pos += n;
            rem -= n;
        }
    }

    // ---------- helpers ----------

    static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') return sb.toString();
            if (c != '\r') sb.append((char) c);
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /** Header line read byte-by-byte so no payload bytes are consumed. */
    static String readLine(ReadableByteChannel ch) throws IOException {
        StringBuilder sb = new StringBuilder();
        ByteBuffer b = ByteBuffer.allocate(1);
        while (true) {
            b.clear();
            if (ch.read(b) < 0) return sb.length() == 0 ? null : sb.toString();
            b.flip();
            char c = (char) b.get();
            if (c == '\n') return sb.toString();
            if (c != '\r') sb.append(c);
        }
    }

    static String sha256(Path p) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(p)) {
            byte[] buf = new byte[1 << 20];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        return HexFormat.of().formatHex(md.digest());
    }
}
