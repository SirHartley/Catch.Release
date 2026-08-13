package catchrelease.helper.cache;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * One value held for a while: the standing answer to "this read is expensive and something asks
 * for it every frame". The caller brings its own clock - game days, accumulated real seconds,
 * wall-clock millis - and the time-to-live is in whatever that clock counts. A key can ride
 * along; the value is recomputed early when the key stops matching, which is how a cache tied
 * to a place follows a fleet through a jump instead of waiting out its window.
 * <p>
 * The compute may be an action wearing a value - "rename the fleet and hand back the name" -
 * which is what makes this the do-at-most-once-per-window primitive as well as a cache.
 * <p>
 * Holders keep instances in transient fields, so a deserialized owner starts empty and lazily
 * rebuilds - which is also why there is no eager state worth persisting in here.
 */
public class TimedValue<T> {

    protected final double ttl;

    protected boolean held = false;
    protected double stampTime;
    protected Object stampKey;
    protected T value;

    /** @param ttl how long a computed value is trusted, in the caller's own clock units */
    public TimedValue(double ttl) {
        this.ttl = ttl;
    }

    public T get(double now, Supplier<T> compute) {
        return get(now, null, compute);
    }

    /** @param key what the value was read for - a change forces the read even mid-window */
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
