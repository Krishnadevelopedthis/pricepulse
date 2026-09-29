import java.util.concurrent.CountDownLatch;

/** Thread vs Runnable, and all six Thread.State values observed on real threads. */
public class ThreadLifecycleDemo {
    static final Object monitor = new Object();

    /** Subclassing Thread (prefer Runnable/executors in real code). */
    static class PriceCheckThread extends Thread {
        PriceCheckThread() { super("subclass-thread"); }
        @Override public void run() { System.out.println("  [" + getName() + "] run() via Thread subclass"); }
    }

    static void awaitState(Thread t, Thread.State wanted) throws InterruptedException {
        long deadline = System.nanoTime() + 3_000_000_000L;
        while (t.getState() != wanted && System.nanoTime() < deadline) Thread.sleep(5);
    }

    public static void main(String[] args) throws Exception {
        System.out.println("== Thread and Runnable ==");
        Thread a = new PriceCheckThread();
        Runnable task = () -> System.out.println("  [" + Thread.currentThread().getName() + "] run() via Runnable");
        Thread b = new Thread(task, "runnable-thread");
        a.start(); b.start(); a.join(); b.join();

        System.out.println("== Lifecycle ==");
        // NEW -> RUNNABLE -> TERMINATED
        Thread simple = new Thread(() -> { }, "simple");
        System.out.println("  NEW           : " + simple.getState());
        simple.start(); simple.join();
        System.out.println("  TERMINATED    : " + simple.getState());

        // RUNNABLE: spinning on the CPU
        Thread spinner = new Thread(() -> { while (!Thread.currentThread().isInterrupted()) { /* busy */ } }, "spinner");
        spinner.start(); awaitState(spinner, Thread.State.RUNNABLE);
        System.out.println("  RUNNABLE      : " + spinner.getState());
        spinner.interrupt(); spinner.join();

        // BLOCKED: waiting to enter a monitor held by another thread
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread holder = new Thread(() -> {
            synchronized (monitor) { holding.countDown(); try { release.await(); } catch (InterruptedException ignored) { } }
        }, "holder");
        holder.start(); holding.await();
        Thread blocked = new Thread(() -> { synchronized (monitor) { /* enters after holder leaves */ } }, "blocked");
        blocked.start(); awaitState(blocked, Thread.State.BLOCKED);
        System.out.println("  BLOCKED       : " + blocked.getState());
        release.countDown(); holder.join(); blocked.join();

        // WAITING: Object.wait() with no timeout
        Thread waiter = new Thread(() -> {
            synchronized (monitor) { try { monitor.wait(); } catch (InterruptedException ignored) { } }
        }, "waiter");
        waiter.start(); awaitState(waiter, Thread.State.WAITING);
        System.out.println("  WAITING       : " + waiter.getState());
        synchronized (monitor) { monitor.notifyAll(); }
        waiter.join();

        // TIMED_WAITING: sleep with a timeout
        Thread sleeper = new Thread(() -> { try { Thread.sleep(2000); } catch (InterruptedException ignored) { } }, "sleeper");
        sleeper.start(); awaitState(sleeper, Thread.State.TIMED_WAITING);
        System.out.println("  TIMED_WAITING : " + sleeper.getState());
        sleeper.interrupt(); sleeper.join();
        System.out.println("Lifecycle demo complete.");
    }
}
