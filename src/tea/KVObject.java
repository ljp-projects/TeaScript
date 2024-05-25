package tea;

import java.util.ArrayList;
import java.util.List;

// This is a Key-Value Object
// For TeaScript objects when compiling to the JVM
public class KVObject<K, V> {
    private final ArrayList<K> keys;
    private final ArrayList<V> values;

    public KVObject(K[] keys, V[] values) throws java.lang.RuntimeException {
        if (keys.length != values.length) {
            throw new java.lang.RuntimeException("Expected keys and values to be the same length.");
        }

        this.keys = new ArrayList<>(keys.length);
        this.values = new ArrayList<>(values.length);

        this.keys.addAll(0, List.of(keys));
        this.values.addAll(0, List.of(values));
    }

    public V get(K key) {
        int idx = this.keys.indexOf(key);

        return this.values.get(idx);
    }

    public void add(K key, V value) {
        this.keys.add(key);
        this.values.add(value);
    }

    public void set(K key, V value) {
        int idx = this.keys.indexOf(key);
        this.values.set(idx, value);
    }
}
