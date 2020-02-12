package com.theKsoni.executor.jooq;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Singleton
@RequiredArgsConstructor(onConstructor = @__({ @Inject }))
@Log4j2
public class VertxBlockingExecutor {

    private final Vertx vertx;

    public <T> CompletableFuture<T> run(Supplier<T> blockingOperationSupplier) {
        CompletableFuture<T> result = new CompletableFuture<>();

        Handler<AsyncResult<T>> handleResult = ar -> {
            if (ar.succeeded()) {
                result.complete(ar.result());
            } else {
                result.completeExceptionally(ar.cause());
            }
        };

        vertx.executeBlocking((Future<T> f) -> f.complete(blockingOperationSupplier.get()), handleResult);

        return result;
    }

}
