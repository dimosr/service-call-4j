package com.dimosr.service.util;

import com.google.common.collect.EvictingQueue;

import java.util.HashMap;
import java.util.Map;

/**
 * A queue that can maintain the last elements of a sequence
 * and provide fast, efficient access to statistics
 *
 * The queue has a maximum size and when it's full, then
 * new items replace older items (in LRU fashion)
 *
 * The queue can hold elements that have values belonging to an enum
 * It provides constant-time [0(1)] access to the number of occurences
 * of each value
 */
public class StatisticsQueue<T extends Enum> {
    private final EvictingQueue<T> queue;
    private final Map<T, Integer> occurences;

    public StatisticsQueue(int size) {
        queue = EvictingQueue.create(size);
        occurences = new HashMap<>();
    }

    public void addItem(T item) {
        if (queue.remainingCapacity() <= 0) {
            T itemToBeRemoved = queue.peek();
            int itemOccurences = occurences.getOrDefault(itemToBeRemoved, 0);
            occurences.put(itemToBeRemoved, itemOccurences-1);
        }

        int itemOccurences = occurences.getOrDefault(item, 0);
        occurences.put(item, itemOccurences+1);
        queue.add(item);
    }

    public int getOccurences(T value) {
        return occurences.getOrDefault(value, 0);
    }

    public void clear() {
        queue.clear();
        occurences.clear();
    }

    public boolean isFull() {
        return queue.remainingCapacity() == 0;
    }
}
