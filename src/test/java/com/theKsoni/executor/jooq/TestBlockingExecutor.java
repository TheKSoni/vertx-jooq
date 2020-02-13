package com.theKsoni.executor.jooq;

import java.sql.Connection;
import java.util.concurrent.CompletableFuture;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//Using Vertx Blocking Executor for query execution
@ExtendWith(VertxExtension.class)
public class TestBlockingExecutor extends Common {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  private VertxBlockingExecutor vertxBlockingExecutor;

  @BeforeEach
  void deploy_verticle(Vertx vertx, VertxTestContext testContext) {
    vertx.deployVerticle(new MainVerticle(), testContext.succeeding(id -> testContext.completeNow()));
    vertxBlockingExecutor = new VertxBlockingExecutor(vertx);
  }

  @Test
  void connectionProviderAcquireConnection_FailCase(Vertx vertx, VertxTestContext testContext) {
    log.info("executing connectionProviderAcquireConnection_FailCase");

    Connection connection = getConnectionProvider().acquire(); //Acquire Connection manually
    executeQueries(testContext, connection);
    getConnectionProvider().release(connection);
  }

  @Test
  void connectionRunnable_FailCase(Vertx vertx, VertxTestContext testContext) {
    log.info("executing connectionRunnable_FailCase");
    getDslContext().connection(connection -> {//connectionRunnable
      executeQueries(testContext, connection);
    });
  }

  @Override
  protected final CompletableFuture<Integer> _insertRecord(Connection connection) {
    return vertxBlockingExecutor.run(() -> insertRecord(connection));
  }

  @Override
  protected final CompletableFuture<Boolean> _acquireLocked(Connection connection, int value) {
    return vertxBlockingExecutor.run(() -> acquireLocked(connection, value)); //acquire postgres 9.6 advisory lock
  }

  @Override
  protected final CompletableFuture<Boolean> _releaseLocked(Connection connection, int value) {
    return vertxBlockingExecutor.run(() -> releaseLocked(connection, value)); //release postgres 9.6 advisory lock
  }
}
