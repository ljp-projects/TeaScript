package tea;

import java.util.ArrayList;
import java.util.List;

// This is a Key-Value Object
// For TeaScript objects when compiling to the JVM
@SuppressWarnings("PublicMethodNotExposedInInterface")
public class KVObject<K, V> {
    private final List<K> keys;
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

    @SuppressWarnings("PublicMethodNotExposedInInterface")
    public V get(K key) {
        int idx = this.keys.indexOf(key);

        return this.values.get(idx);
    }

    @SuppressWarnings("PublicMethodNotExposedInInterface")
    public void add(K key, V value) {
        this.keys.add(key);
        this.values.add(value);
    }

    @SuppressWarnings("PublicMethodNotExposedInInterface")
    public void set(K key, V value) {
        int idx = this.keys.indexOf(key);
        this.values.set(idx, value);
    }
}
