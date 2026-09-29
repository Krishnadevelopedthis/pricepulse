import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Executor framework and concurrency utilities, shaped like PricePulse's background price checks:
 * many tracked products -> bounded pool -> tasks -> results. Prices here are simulated; the production
 * scheduler (PriceCheckScheduler) runs the same pattern with real page fetches.
 */
public class ExecutorDemo {
    record Check(int productId, int price) {}

    static Check simulateCheck(int productId) throws InterruptedException {
        Thread.sleep(20 + (productId * 7) % 40);
        if (productId == 7) throw new IllegalStateException("page blocked automated requests");
        return new Check(productId, 1000 + productId * 13);
    }

    public static void main(String[] args) throws Exception {
        ExecutorService pool = new ThreadPoolExecutor(4, 4, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(20), new ThreadPoolExecutor.CallerRunsPolicy()); // bounded threads AND queue

        // Callable + Future
        List<Future<Check>> futures = new ArrayList<>();
        for (int id = 1; id <= 10; id++) { final int pid = id; futures.add(pool.submit(() -> simulateCheck(pid))); }
        int ok = 0, failed = 0;
        for (int i = 0; i < futures.size(); i++) {
            try { Check c = futures.get(i).get(2, TimeUnit.SECONDS); ok++; System.out.println("  product " + c.productId() + " -> " + c.price()); }
            catch (ExecutionException e) { failed++; System.out.println("  product " + (i + 1) + " failed: " + e.getCause().getMessage()); }
        }
        System.out.println("Callable/Future: " + ok + " ok, " + failed + " failed");

        // CompletableFuture composition
        CompletableFuture<Integer> total = CompletableFuture.supplyAsync(() -> 100, pool)
                .thenCombine(CompletableFuture.supplyAsync(() -> 25, pool), Integer::sum)
                .thenApply(v -> v * 2);
        System.out.println("CompletableFuture chain result: " + total.get(2, TimeUnit.SECONDS));

        // ConcurrentHashMap + AtomicInteger + CountDownLatch
        ConcurrentHashMap<String, Integer> byDomain = new ConcurrentHashMap<>();
        AtomicInteger done = new AtomicInteger();
        String[] domains = {"a.example", "b.example", "c.example"};
        CountDownLatch latch = new CountDownLatch(30);
        for (int i = 0; i < 30; i++) {
            final String d = domains[i % 3];
            pool.execute(() -> { byDomain.merge(d, 1, Integer::sum); done.incrementAndGet(); latch.countDown(); });
        }
        latch.await(3, TimeUnit.SECONDS);
        System.out.println("CountDownLatch released; done=" + done.get() + " byDomain=" + new TreeMap<>(byDomain));

        // BlockingQueue hand-off
        BlockingQueue<String> results = new LinkedBlockingQueue<>();
        pool.execute(() -> { for (int i = 1; i <= 3; i++) results.add("result-" + i); results.add("END"); });
        String r; StringBuilder got = new StringBuilder();
        while (!(r = results.take()).equals("END")) got.append(r).append(' ');
        System.out.println("BlockingQueue delivered: " + got.toString().trim());

        // ScheduledExecutorService: periodic ticks like the scheduler's tick()
        ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor();
        CountDownLatch ticks = new CountDownLatch(3);
        AtomicInteger n = new AtomicInteger();
        ses.scheduleAtFixedRate(() -> { System.out.println("  tick " + n.incrementAndGet()); ticks.countDown(); }, 0, 100, TimeUnit.MILLISECONDS);
        ticks.await(2, TimeUnit.SECONDS);
        ses.shutdownNow();

        pool.shutdown();
        boolean clean = pool.awaitTermination(3, TimeUnit.SECONDS);
        System.out.println("pool terminated cleanly: " + clean);
        if (ok != 9 || failed != 1 || done.get() != 30 || total.get() != 250 || !clean) { System.out.println("FAIL"); System.exit(1); }
        System.out.println("Executor demo complete.");
    }
}
