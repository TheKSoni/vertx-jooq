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

@ExtendWith(VertxExtension.class)

//Main Thread (event-loop) executing queries
public class TestMainVerticle extends Common {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  @BeforeEach
  void deploy_verticle(Vertx vertx, VertxTestContext testContext) {
    vertx.deployVerticle(new MainVerticle(), testContext.succeeding(id -> testContext.completeNow()));
  }

  @Test
  void connectionProviderAcquireConnection_SuccessCase(Vertx vertx, VertxTestContext testContext) {
    log.info("executing connectionProviderAcquireConnection_SuccessCase");

    Connection connection = getConnectionProvider().acquire(); //Acquire Connection manually
    executeQueries(testContext, connection);
    getConnectionProvider().release(connection);
  }

  @Test
  void connectionRunnable_SuccessCase(Vertx vertx, VertxTestContext testContext) {
    log.info("executing connectionRunnable_SuccessCase");
    getDslContext().connection(connection -> {//connectionRunnable
      executeQueries(testContext, connection);
    });
  }

  protected final CompletableFuture<Integer> _insertRecord(Connection connection) {
    return CompletableFuture.completedFuture(insertRecord(connection));
  }

  protected final CompletableFuture<Boolean> _acquireLocked(Connection connection, int value) {
    return CompletableFuture.completedFuture(acquireLocked(connection, value)); //acquire postgres 9.6 advisory lock
  }

  protected final CompletableFuture<Boolean> _releaseLocked(Connection connection, int value) {
    return CompletableFuture.completedFuture(releaseLocked(connection, value)); //release postgres 9.6 advisory lock
  }
}
