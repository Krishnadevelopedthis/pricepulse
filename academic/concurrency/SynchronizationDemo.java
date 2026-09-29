import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Why synchronisation matters. A read-modify-write on shared state loses updates without protection.
 * The same idea protects a tracked product's previous/current price pair in ProductService (a per-product lock).
 */
public class SynchronizationDemo {
    static int unsafeCounter = 0;
    static int syncCounter = 0;
    static final AtomicInteger atomicCounter = new AtomicInteger();
    static synchronized void incSync() { syncCounter++; }

    /** Two fields that must change together, like previousPrice/currentPrice. */
    static class PricePair {
        private final ReentrantLock lock = new ReentrantLock();
        private BigDecimal previous = null, current = new BigDecimal("100");
        void update(BigDecimal next) { lock.lock(); try { previous = current; current = next; } finally { lock.unlock(); } }
        BigDecimal[] snapshot() { lock.lock(); try { return new BigDecimal[]{previous, current}; } finally { lock.unlock(); } }
    }

    static void runConcurrently(int n, Runnable r) throws InterruptedException {
        Thread[] ts = new Thread[n];
        for (int i = 0; i < n; i++) { ts[i] = new Thread(r); ts[i].start(); }
        for (Thread t : ts) t.join();
    }

    public static void main(String[] args) throws Exception {
        final int threads = 4, per = 500_000;
        int expected = threads * per;
        // Each variant runs alone so lock contention in one cannot hide a race in another.
        runConcurrently(threads, () -> { for (int j = 0; j < per; j++) unsafeCounter++; });
        runConcurrently(threads, () -> { for (int j = 0; j < per; j++) incSync(); });
        runConcurrently(threads, () -> { for (int j = 0; j < per; j++) atomicCounter.incrementAndGet(); });
        System.out.println("expected            : " + expected);
        System.out.println("unsynchronized      : " + unsafeCounter + (unsafeCounter == expected ? " (no race observed this run; races are non-deterministic)" : "  <- lost updates"));
        System.out.println("synchronized method : " + syncCounter);
        System.out.println("AtomicInteger       : " + atomicCounter.get());

        PricePair pair = new PricePair();
        Thread[] writers = new Thread[4];
        for (int i = 0; i < writers.length; i++) {
            final int base = (i + 1) * 1000;
            writers[i] = new Thread(() -> { for (int j = 0; j < 1000; j++) pair.update(new BigDecimal(base + j)); });
            writers[i].start();
        }
        for (Thread w : writers) w.join();
        BigDecimal[] snap = pair.snapshot();
        System.out.println("locked price pair   : previous=" + snap[0] + " current=" + snap[1] + " (always a consistent pair)");
        if (syncCounter != expected || atomicCounter.get() != expected) { System.out.println("FAIL"); System.exit(1); }
    }
}
