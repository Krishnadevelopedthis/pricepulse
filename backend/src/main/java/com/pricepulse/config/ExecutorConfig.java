package com.pricepulse.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class ExecutorConfig {

    /**
     * Bounded pool for price checks: fixed worker count and a bounded queue. When the queue is
     * full the submitting (scheduler) thread runs the task itself, which applies back-pressure
     * instead of creating unlimited threads or dropping work.
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService priceCheckExecutor(PricePulseProperties props) {
        var s = props.scheduler();
        AtomicInteger n = new AtomicInteger();
        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "price-check-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
        return new ThreadPoolExecutor(s.poolSize(), s.poolSize(), 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(s.queueCapacity()), tf, new ThreadPoolExecutor.CallerRunsPolicy());
    }
}
