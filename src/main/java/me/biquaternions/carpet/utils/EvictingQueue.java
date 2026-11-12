package me.biquaternions.carpet.utils;

import java.io.Serial;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Insertion ordered set that keeps only the last 10 elements.
 */
public class EvictingQueue<K> extends LinkedHashMap<K, Integer> {

    @Serial
    private static final long serialVersionUID = 1L;

    public void put(K key) {
        super.put(key, 1);
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, Integer> eldest) {
        return this.size() > 10;
    }

}
