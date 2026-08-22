package catchrelease.helper.cache;

import java.util.Objects;
import java.util.function.Supplier;


public class TimedValue<T> {

    protected final double ttl;

    protected boolean held = false;
    protected double stampTime;
    protected Object stampKey;
    protected T value;


    public TimedValue(double ttl) {
        this.ttl = ttl;
    }

    public T get(double now, Supplier<T> compute) {
        return get(now, null, compute);
    }


    public T get(double now, Object key, Supplier<T> compute) {
        if (!held || !Objects.equals(stampKey, key) || now - stampTime >= ttl) {
            value = compute.get();

            stampTime = now;
            stampKey = key;
            held = true;
        }

        return value;
    }

    public void invalidate() {
        held = false;
        value = null;
        stampKey = null;
    }
}
