package com.theKsoni.executor.jooq;

import static com.theKsoni.executor.jooq.tables.Data.DATA;

import java.sql.Connection;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

import com.theKsoni.executor.jooq.tables.records.DataRecord;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.jooq.Configuration;
import org.jooq.ConnectionProvider;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.impl.DataSourceConnectionProvider;
import org.jooq.impl.DefaultConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(VertxExtension.class)

public class TestMainVerticle {

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  DSLContext dslContext;
  VertxBlockingExecutor vertxBlockingExecutor;
  ConnectionProvider connectionProvider;

  @BeforeEach
  void deploy_verticle(Vertx vertx, VertxTestContext testContext) {
    vertx.deployVerticle(new MainVerticle(), testContext.succeeding(id -> testContext.completeNow()));

    Properties props = new Properties();

    props.setProperty("dataSourceClassName", "org.postgresql.ds.PGSimpleDataSource");
    props.setProperty("maximumPoolSize", "2");
    props.setProperty("connectionTimeout", "10000");
    props.setProperty("schema", "mcve");
    props.setProperty("username", "postgres");
    props.setProperty("password", "postgres");
    props.setProperty("dataSource.socketTimeout", "30");
    props.setProperty("dataSource.loginTimeout", "30");
    props.setProperty("dataSource.tcpKeepAlive", "true");
    props.setProperty("dataSource.assumeMinServerVersion", "9.6");
    props.setProperty("dataSource.serverName", "localhost");
    props.setProperty("dataSource.portNumber", "5430");
    props.setProperty("dataSource.databaseName", "mcve");

    HikariDataSource hikariDataSource = new HikariDataSource(new HikariConfig(props));
    connectionProvider = new DataSourceConnectionProvider(hikariDataSource);

    Configuration config = new DefaultConfiguration()
      .set(hikariDataSource)
      .set(SQLDialect.POSTGRES);

    dslContext = DSL.using(config);

    vertxBlockingExecutor = new VertxBlockingExecutor(vertx);

  }

  @Test
  void connectionProviderAcquireConnection_SuccessCase(Vertx vertx, VertxTestContext testContext) {

    vertx.createSharedWorkerExecutor("test-executor").executeBlocking(promise -> {

      Connection connection = connectionProvider.acquire(); //Acquire Connection manually
      executeQueries(testContext, connection);

    }, res -> testContext.completeNow());
  }

  @Test
  void connectionRunnable_failCase(Vertx vertx, VertxTestContext testContext) {

    dslContext.connection(connection -> {//connectionRunnable
      executeQueries(testContext, connection);
    });
  }

  private CompletableFuture<Void> executeQueries(VertxTestContext testContext, Connection connection) {
    return acquireLocked(connection, 1)
      .thenApply(acquireLocked -> {
        log.info("lock acquired : {}", acquireLocked);
        return acquireLocked;
      })
      .thenCompose(ignored -> insertRecord(connection))
      .thenCompose(result -> releaseLocked(connection, 1))
      .thenAccept(lockReleased -> {
        log.info("lock released : {}", lockReleased);
        testContext.completeNow();
      }).exceptionally(ex -> {
        testContext.failNow(ex);
        return null;
      });
  }

  private CompletableFuture<Integer> insertRecord(Connection connection) {

    return vertxBlockingExecutor.run(() -> {
      DataRecord result =
        DSL.using(connection)
          .insertInto(DATA)
          .columns(DATA.VALUE)
          .values(42)
          .returning(DATA.ID, DATA.VALUE)
          .fetchOne();
      return result.getId();
    });
  }

  private CompletableFuture<Boolean> acquireLocked(Connection connection, int value) {
    return vertxBlockingExecutor.run(() -> DSL.using(connection)
      .fetchOne("select pg_try_advisory_lock(?)", value) //acquire postgres 9.6 advisory lock
      .get(0, Boolean.class));
  }

  private CompletableFuture<Boolean> releaseLocked(Connection connection, int value) {
    return vertxBlockingExecutor.run(() -> DSL.using(connection)
      .fetchOne("select pg_advisory_unlock(?)", value) //release postgres 9.6 advisory lock
      .get(0, Boolean.class));
  }
}
