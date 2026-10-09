import java.io.*;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

/**
 * Multi-threaded file server (Java 21+).
 * Usage: java Server <port> <dir> [trad|nio]
 *
 * Protocol (text header lines ending in \n, then optional binary payload):
 *   LIST                       -> FILE <name> <size> ... then END
 *   INFO <name>                -> SIZE <bytes>  | ERROR <code> <msg>
 *   GET <name> <offset> <len>  -> OK <len>\n + <len> raw bytes | ERROR <code> <msg>
 * Note: file names must not contain spaces.
 */
public class Server {
    static Path root;
    static boolean nio;

    public static void main(String[] a) throws Exception {
        if (a.length < 2) {
            System.err.println("Usage: java Server <port> <dir> [trad|nio]");
            return;
        }
        int port = Integer.parseInt(a[0]);
        root = Path.of(a[1]).toAbsolutePath().normalize();
        nio = a.length > 2 && a[2].equalsIgnoreCase("nio");
        System.out.println("Serving " + root + " on port " + port + " mode=" + (nio ? "nio" : "trad"));

        try (ServerSocketChannel ssc = ServerSocketChannel.open();
             ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            ssc.bind(new InetSocketAddress(port));
            while (true) {
                SocketChannel ch = ssc.accept();
                pool.submit(() -> handle(ch));
            }
        }
    }

    static void handle(SocketChannel ch) {
        try (ch) {
            InputStream in = ch.socket().getInputStream();
            OutputStream out = ch.socket().getOutputStream();
            String line;
            while ((line = readLine(in)) != null) {
                String[] p = line.trim().split("\\s+");
                switch (p[0].toUpperCase()) {
                    case "LIST" -> list(out);
                    case "INFO" -> info(p, out);
                    case "GET" -> get(p, ch, out);
                    case "QUIT" -> { return; }
                    default -> send(out, "ERROR 400 unknown command\n");
                }
            }
        } catch (IOException e) {
            // client disconnected or transfer failed; connection is closed by try-with-resources
        }
    }

    static void list(OutputStream out) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (Stream<Path> s = Files.list(root)) {
            for (Path f : (Iterable<Path>) s.filter(Files::isRegularFile)::iterator) {
                sb.append("FILE ").append(f.getFileName()).append(' ').append(Files.size(f)).append('\n');
            }
        }
        sb.append("END\n");
        send(out, sb.toString());
    }

    static void info(String[] p, OutputStream out) throws IOException {
        if (p.length != 2) { send(out, "ERROR 400 usage: INFO <file>\n"); return; }
        Path f = resolve(p[1]);
        if (f == null) { send(out, "ERROR 404 file not found\n"); return; }
        send(out, "SIZE " + Files.size(f) + "\n");
    }

    static void get(String[] p, SocketChannel ch, OutputStream out) throws IOException {
        if (p.length != 4) { send(out, "ERROR 400 usage: GET <file> <offset> <length>\n"); return; }
        Path f = resolve(p[1]);
        if (f == null) { send(out, "ERROR 404 file not found\n"); return; }
        long off, len;
        try {
            off = Long.parseLong(p[2]);
            len = Long.parseLong(p[3]);
        } catch (NumberFormatException e) {
            send(out, "ERROR 400 offset/length must be numbers\n");
            return;
        }
        long size = Files.size(f);
        if (off < 0 || len < 0 || off + len > size) {
            send(out, "ERROR 416 range out of bounds\n");
            return;
        }
        send(out, "OK " + len + "\n");
        if (nio) sendNio(f, off, len, ch);
        else sendTrad(f, off, len, out);
    }

    /** Traditional I/O: RandomAccessFile.read -> OutputStream.write */
    static void sendTrad(Path f, long off, long len, OutputStream out) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(f.toFile(), "r")) {
            raf.seek(off);
            byte[] buf = new byte[64 * 1024];
            long rem = len;
            while (rem > 0) {
                int n = raf.read(buf, 0, (int) Math.min(buf.length, rem));
                if (n < 0) throw new EOFException("file shorter than expected");
                out.write(buf, 0, n);
                rem -= n;
            }
            out.flush();
        }
    }

    /** NIO native transfer: FileChannel.transferTo (loop, because it may send fewer bytes) */
    static void sendNio(Path f, long off, long len, SocketChannel ch) throws IOException {
        try (FileChannel fc = FileChannel.open(f, StandardOpenOption.READ)) {
            long pos = off, rem = len;
            while (rem > 0) {
                long n = fc.transferTo(pos, rem, ch);
                if (n <= 0) throw new EOFException("transferTo made no progress");
                pos += n;
                rem -= n;
            }
        }
    }

    /** Resolve a file name inside root; null if missing or escaping root (blocks ../). */
    static Path resolve(String name) {
        Path f = root.resolve(name).normalize();
        return (f.startsWith(root) && Files.isRegularFile(f)) ? f : null;
    }

    static void send(OutputStream out, String s) throws IOException {
        out.write(s.getBytes());
        out.flush();
    }

    /** Read one line without buffering ahead (so payload bytes are never consumed). */
    static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') return sb.toString();
            if (c != '\r') sb.append((char) c);
        }
        return sb.length() == 0 ? null : sb.toString();
    }
}
