package io.github.sudoitir.taraz.container.it;

import io.github.sudoitir.taraz.core.application.ports.CommandOutcome;
import io.github.sudoitir.taraz.core.domain.common.ErrorCode;
import io.github.sudoitir.taraz.core.domain.common.Result;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.IntStream;

/** Barrier-synchronized concurrent task runner shared by the concurrency/idempotency IT suite. */
final class TestConcurrency {

    private TestConcurrency() {}

    static <T> List<T> runConcurrently(int n, IntFunction<T> task) throws Exception {
        return runConcurrently(n, task, 60);
    }

    /**
     * A deliberately adversarial barrier-synchronized burst (hundreds of virtual threads racing one
     * account row) legitimately exceeds Hikari's short {@code connection-timeout} (ADR-0054) for some
     * callers — that timeout exists precisely to fail fast under real overload rather than queue
     * silently. A well-behaved client retries a typed {@link ErrorCode#CONCURRENCY_CONFLICT} 503 with
     * the <em>same</em> idempotency key (the operation never reached the database, so retrying it is
     * both safe and correct — ADR-0041). This wraps {@code task} so each submission does exactly that,
     * bounded, so the suite still proves "every operation eventually lands exactly once, exact final
     * balance" without asserting away the backpressure the ADR intentionally introduces.
     */
    static List<Result<CommandOutcome>> runConcurrentlyRetryingOnBackpressure(
            int n, IntFunction<Supplier<Result<CommandOutcome>>> task) throws Exception {
        return runConcurrently(n, i -> retryOnConcurrencyConflict(task.apply(i)));
    }

    private static Result<CommandOutcome> retryOnConcurrencyConflict(Supplier<Result<CommandOutcome>> op) {
        int maxAttempts = 20;
        Result<CommandOutcome> last = op.get();
        int attempt = 1;
        while (isConcurrencyConflict(last) && attempt < maxAttempts) {
            try {
                Thread.sleep(25L * attempt);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return last;
            }
            last = op.get();
            attempt++;
        }
        return last;
    }

    private static boolean isConcurrencyConflict(Result<CommandOutcome> result) {
        return result instanceof Result.Failure<CommandOutcome> failure
                && failure.domainError().code() == ErrorCode.CONCURRENCY_CONFLICT;
    }

    static <T> List<T> runConcurrently(int n, IntFunction<T> task, long timeoutSeconds) throws Exception {
        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        CyclicBarrier barrier = new CyclicBarrier(n);
        try {
            List<Future<T>> futures = IntStream.range(0, n)
                    .<Callable<T>>mapToObj(i -> () -> {
                        barrier.await();
                        return task.apply(i);
                    })
                    .map(pool::submit)
                    .toList();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(timeoutSeconds, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdown();
        }
    }
}
