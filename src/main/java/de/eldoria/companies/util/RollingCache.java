package de.eldoria.companies.util;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class RollingCache<T> {
    private final int size;
    private final ConcurrentLinkedQueue<T> queue;

    public RollingCache(int size) {
        this.size = size;
        queue = new ConcurrentLinkedQueue<>();
    }

    public boolean add(@NotNull T t) {
        synchronized (queue) {
            if (size == queue.size()) queue.remove();
            return queue.add(t);
        }
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public synchronized List<T> flush() {
        synchronized (queue) {
            var cache = new ArrayList<>(queue);
            queue.clear();
            return cache;
        }
    }
}
