package org.ursc.trajectory.data;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Watches folders (recursively) for new or changed files and indexes them into the
 * {@link DataCatalog} once their size has stopped changing (so files still being copied in
 * from removable media are not read half-written). Never starts a run by itself.
 */
public final class FolderWatcher implements AutoCloseable {

    /** A file is indexed once its size has been stable for this long. */
    static final long QUIET_MS = 2000;

    private final DataCatalog catalog;
    private final Consumer<List<DataCatalog.Entry>> onNew;
    private final Consumer<String> onError;
    private final WatchService service;
    private final Map<Path, long[]> pending = new ConcurrentHashMap<>(); // path -> {size, since}
    private final Thread thread;
    private volatile boolean running = true;

    public FolderWatcher(final DataCatalog catalog, final Consumer<List<DataCatalog.Entry>> onNew,
                         final Consumer<String> onError) throws IOException {
        this.catalog = catalog;
        this.onNew = onNew;
        this.onError = onError;
        this.service = FileSystems.getDefault().newWatchService();
        this.thread = new Thread(this::loop, "ursc-folder-watcher");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    /** Start watching a folder (and its subfolders); existing files are indexed immediately. */
    public void watch(final String folder) throws IOException {
        final Path root = Paths.get(folder).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IOException("not a folder: " + folder);
        }
        catalog.addWatched(root.toString());
        registerAll(root);
        final List<DataCatalog.Entry> found = catalog.add(List.of(root.toString()));
        if (!found.isEmpty()) {
            onNew.accept(found);
        }
    }

    private void registerAll(final Path root) throws IOException {
        try (Stream<Path> s = Files.walk(root)) {
            for (final Path d : (Iterable<Path>) s.filter(Files::isDirectory)::iterator) {
                d.register(service, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);
            }
        }
    }

    private void loop() {
        while (running) {
            try {
                final WatchKey key = service.poll(500, TimeUnit.MILLISECONDS);
                if (key != null) {
                    final Path dir = (Path) key.watchable();
                    for (final WatchEvent<?> ev : key.pollEvents()) {
                        if (ev.kind() == StandardWatchEventKinds.OVERFLOW) {
                            continue;
                        }
                        final Path p = dir.resolve((Path) ev.context());
                        if (Files.isDirectory(p)) {
                            registerAll(p);
                            try (Stream<Path> s = Files.walk(p)) {
                                s.filter(Files::isRegularFile).forEach(f -> pending.put(f, new long[] {-1, 0}));
                            }
                        } else if (!p.getFileName().toString().startsWith(".")) {
                            pending.put(p, new long[] {-1, 0});
                        }
                    }
                    key.reset();
                }
                settle();
            } catch (final ClosedWatchServiceException e) {
                return;
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (final IOException | RuntimeException e) {
                onError.accept("folder watcher: " + e.getMessage());
            }
        }
    }

    /** Index files whose size has been stable for {@link #QUIET_MS}. */
    private void settle() {
        final long now = System.currentTimeMillis();
        final List<DataCatalog.Entry> ready = new ArrayList<>();
        for (final Map.Entry<Path, long[]> e : pending.entrySet()) {
            final Path p = e.getKey();
            try {
                if (!Files.isRegularFile(p)) {
                    pending.remove(p);
                    continue;
                }
                final long size = Files.size(p);
                final long[] st = e.getValue();
                if (size != st[0]) {
                    st[0] = size;
                    st[1] = now;
                } else if (now - st[1] >= QUIET_MS) {
                    pending.remove(p);
                    final DataCatalog.Entry entry = catalog.index(p);
                    if (entry != null) {
                        ready.add(entry);
                    }
                }
            } catch (final IOException ex) {
                pending.remove(p);
                onError.accept(p.getFileName() + ": " + ex.getMessage());
            }
        }
        if (!ready.isEmpty()) {
            try {
                catalog.save();
            } catch (final IOException ex) {
                onError.accept("catalog save failed: " + ex.getMessage());
            }
            onNew.accept(ready);
        }
    }

    @Override
    public void close() throws IOException {
        running = false;
        service.close();
        thread.interrupt();
    }
}
