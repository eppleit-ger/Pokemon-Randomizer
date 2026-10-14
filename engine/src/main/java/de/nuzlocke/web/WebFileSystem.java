package de.nuzlocke.web;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.teavm.runtime.fs.VirtualFile;
import org.teavm.runtime.fs.VirtualFileAccessor;
import org.teavm.runtime.fs.VirtualFileSystem;

/**
 * Minimal flat in-memory file system for the browser build.
 *
 * Unlike TeaVM's default one it lets us mount an existing byte[] as a file
 * (no copy of the source ROM) and hand the backing array of a written file
 * back to JavaScript (no copy of the output ROM). That matters for the
 * 256-512 MB Gen 5 ROMs on phones.
 */
public final class WebFileSystem implements VirtualFileSystem {

    static final class Node {
        final boolean directory;
        byte[] data;
        int size;
        long modified = System.currentTimeMillis();

        Node(boolean directory) {
            this.directory = directory;
            this.data = directory ? null : new byte[0];
        }

        void ensureCapacity(int needed) {
            if (needed <= data.length) {
                return;
            }
            // Grow geometrically for small files, but only in 32 MB steps for
            // big ones: a 1.5x copy of a 512 MB NDS image would not fit on a phone.
            long step = data.length >= (64 << 20) ? (32L << 20) : data.length + 4096L;
            long grown = Math.max((long) needed, data.length + step);
            byte[] bigger = new byte[(int) Math.min(grown, Integer.MAX_VALUE - 8)];
            System.arraycopy(data, 0, bigger, 0, size);
            data = bigger;
        }
    }

    private final Map<String, Node> nodes = new HashMap<>();

    public WebFileSystem() {
        nodes.put("/", new Node(true));
    }

    static String normalize(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        String[] parts = path.replace('\\', '/').split("/");
        List<String> out = new ArrayList<>();
        for (String part : parts) {
            if (part.isEmpty() || part.equals(".")) {
                continue;
            }
            if (part.equals("..")) {
                if (!out.isEmpty()) {
                    out.remove(out.size() - 1);
                }
                continue;
            }
            out.add(part);
        }
        return "/" + String.join("/", out);
    }

    static String parentOf(String path) {
        int idx = path.lastIndexOf('/');
        return idx <= 0 ? "/" : path.substring(0, idx);
    }

    static String child(String dir, String name) {
        return dir.equals("/") ? "/" + name : dir + "/" + name;
    }

    /** Mounts {@code data[0..size)} as a file without copying it. */
    public void mount(String path, byte[] data, int size) {
        String p = normalize(path);
        Node node = new Node(false);
        node.data = data;
        node.size = size;
        nodes.put(p, node);
    }

    /** Pre-sizes a file's buffer so large sequential writes don't reallocate. */
    public void reserve(String path, int capacity) {
        String p = normalize(path);
        Node node = nodes.get(p);
        if (node == null) {
            node = new Node(false);
            nodes.put(p, node);
        }
        if (!node.directory) {
            node.ensureCapacity(capacity);
        }
    }

    public byte[] data(String path) {
        Node node = nodes.get(normalize(path));
        return node == null || node.directory ? null : node.data;
    }

    public int size(String path) {
        Node node = nodes.get(normalize(path));
        return node == null || node.directory ? -1 : node.size;
    }

    /** Drops everything except the root directory. */
    public void clear() {
        nodes.clear();
        nodes.put("/", new Node(true));
    }

    @Override
    public String getUserDir() {
        return "/";
    }

    @Override
    public VirtualFile getFile(String path) {
        return new FileRef(normalize(path));
    }

    @Override
    public boolean isWindows() {
        return false;
    }

    @Override
    public String canonicalize(String path) {
        return normalize(path);
    }

    @Override
    public String[] getRoots() {
        return new String[] { "/" };
    }

    private final class FileRef implements VirtualFile {
        private final String path;

        FileRef(String path) {
            this.path = path;
        }

        private Node node() {
            return nodes.get(path);
        }

        @Override
        public String getName() {
            return path.substring(path.lastIndexOf('/') + 1);
        }

        @Override
        public boolean isDirectory() {
            Node n = node();
            return n != null && n.directory;
        }

        @Override
        public boolean isFile() {
            Node n = node();
            return n != null && !n.directory;
        }

        @Override
        public String[] listFiles() {
            if (!isDirectory()) {
                return null;
            }
            List<String> names = new ArrayList<>();
            for (String key : nodes.keySet()) {
                if (!key.equals(path) && parentOf(key).equals(path)) {
                    names.add(key.substring(key.lastIndexOf('/') + 1));
                }
            }
            return names.toArray(new String[0]);
        }

        @Override
        public VirtualFileAccessor createAccessor(boolean readable, boolean writable, boolean append) {
            Node n = node();
            if (n == null || n.directory) {
                return null;
            }
            if (writable && !readable && !append) {
                n.size = 0;
            }
            return new Accessor(n, append ? n.size : 0);
        }

        @Override
        public boolean createFile(String fileName) throws IOException {
            if (!isDirectory()) {
                throw new IOException("Not a directory: " + path);
            }
            String p = child(path, fileName);
            if (nodes.containsKey(p)) {
                return false;
            }
            nodes.put(p, new Node(false));
            return true;
        }

        @Override
        public boolean createDirectory(String dirName) {
            if (!isDirectory()) {
                return false;
            }
            String p = child(path, dirName);
            if (nodes.containsKey(p)) {
                return false;
            }
            nodes.put(p, new Node(true));
            return true;
        }

        @Override
        public boolean delete() {
            Node n = node();
            if (n == null || path.equals("/")) {
                return false;
            }
            if (n.directory) {
                String prefix = path + "/";
                for (String key : nodes.keySet()) {
                    if (key.startsWith(prefix)) {
                        return false;
                    }
                }
            }
            nodes.remove(path);
            return true;
        }

        @Override
        public boolean adopt(VirtualFile file, String fileName) {
            if (!(file instanceof FileRef) || !isDirectory()) {
                return false;
            }
            FileRef other = (FileRef) file;
            Node n = other.node();
            if (n == null || n.directory) {
                return false;
            }
            nodes.remove(other.path);
            nodes.put(child(path, fileName), n);
            return true;
        }

        @Override
        public boolean canRead() {
            return node() != null;
        }

        @Override
        public boolean canWrite() {
            return node() != null;
        }

        @Override
        public long lastModified() {
            Node n = node();
            return n == null ? 0 : n.modified;
        }

        @Override
        public boolean setLastModified(long time) {
            Node n = node();
            if (n == null) {
                return false;
            }
            n.modified = time;
            return true;
        }

        @Override
        public boolean setReadOnly(boolean readOnly) {
            return node() != null;
        }

        @Override
        public int length() {
            Node n = node();
            return n == null || n.directory ? 0 : n.size;
        }
    }

    private static final class Accessor implements VirtualFileAccessor {
        private final Node node;
        private int pos;

        Accessor(Node node, int pos) {
            this.node = node;
            this.pos = pos;
        }

        @Override
        public int read(byte[] buffer, int offset, int limit) {
            int available = node.size - pos;
            if (available <= 0) {
                return limit == 0 ? 0 : -1;
            }
            int count = Math.min(limit, available);
            System.arraycopy(node.data, pos, buffer, offset, count);
            pos += count;
            return count;
        }

        @Override
        public void write(byte[] buffer, int offset, int limit) {
            int end = pos + limit;
            node.ensureCapacity(end);
            if (pos > node.size) {
                java.util.Arrays.fill(node.data, node.size, pos, (byte) 0);
            }
            System.arraycopy(buffer, offset, node.data, pos, limit);
            pos = end;
            if (end > node.size) {
                node.size = end;
            }
            node.modified = System.currentTimeMillis();
        }

        @Override
        public int tell() {
            return pos;
        }

        @Override
        public void seek(int target) {
            pos = target;
        }

        @Override
        public void skip(int amount) {
            pos += amount;
        }

        @Override
        public int size() {
            return node.size;
        }

        @Override
        public void resize(int newSize) {
            node.ensureCapacity(newSize);
            if (newSize > node.size) {
                java.util.Arrays.fill(node.data, node.size, newSize, (byte) 0);
            }
            node.size = newSize;
        }

        @Override
        public void close() {
        }

        @Override
        public void flush() {
        }
    }
}
