import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/** Producer-consumer task coordination with wait(), notify() and notifyAll() on a bounded queue. */
public class WaitNotifyDemo {
    static class BoundedTaskQueue {
        private final Queue<String> q = new ArrayDeque<>();
        private final int capacity;
        private boolean closed = false;
        private int maxSeen = 0;
        BoundedTaskQueue(int capacity) { this.capacity = capacity; }

        synchronized void put(String task) throws InterruptedException {
            while (q.size() == capacity) wait();     // full: release the monitor and sleep
            q.add(task);
            maxSeen = Math.max(maxSeen, q.size());
            notifyAll();                              // wake consumers waiting on "empty"
        }

        /** Returns null when the queue is closed and drained. */
        synchronized String take() throws InterruptedException {
            while (q.isEmpty() && !closed) wait();   // empty: wait for a producer
            String t = q.poll();
            notifyAll();                              // wake producers waiting on "full"
            return t;
        }

        synchronized int maxSeen() { return maxSeen; }

        synchronized void close() { closed = true; notifyAll(); }
    }

    public static void main(String[] args) throws Exception {
        BoundedTaskQueue queue = new BoundedTaskQueue(3);
        List<String> processed = java.util.Collections.synchronizedList(new ArrayList<>());
        int total = 12;

        Thread producer = new Thread(() -> {
            try {
                for (int i = 1; i <= total; i++) { queue.put("check-product-" + i); System.out.println("  produced check-product-" + i); }
            } catch (InterruptedException ignored) { }
            queue.close();
        }, "producer");

        Runnable consumerTask = () -> {
            try {
                String t;
                while ((t = queue.take()) != null) { processed.add(t); System.out.println("  " + Thread.currentThread().getName() + " consumed " + t); Thread.sleep(5); }
            } catch (InterruptedException ignored) { }
        };
        Thread c1 = new Thread(consumerTask, "consumer-1"), c2 = new Thread(consumerTask, "consumer-2");
        producer.start(); c1.start(); c2.start();
        producer.join(); c1.join(); c2.join();

        System.out.println("produced " + total + ", consumed " + processed.size());
        System.out.println("largest queue size observed: " + queue.maxSeen() + " (capacity 3)");
        if (processed.size() != total || queue.maxSeen() > 3) { System.out.println("FAIL"); System.exit(1); }
        System.out.println("Wait/notify demo complete.");
    }
}
