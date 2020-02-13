package com.theKsoni.executor.jooq;

import static com.theKsoni.executor.jooq.tables.Data.DATA;

import java.sql.Connection;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

import com.theKsoni.executor.jooq.tables.records.DataRecord;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.vertx.junit5.VertxTestContext;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.jooq.Configuration;
import org.jooq.ConnectionProvider;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.impl.DataSourceConnectionProvider;
import org.jooq.impl.DefaultConfiguration;

@Getter
@Log4j2
public abstract class Common {

  private final DSLContext dslContext;
  private final ConnectionProvider connectionProvider;

  Common() {

    Properties props = new Properties();

    props.setProperty("dataSourceClassName", "org.postgresql.ds.PGSimpleDataSource");
    props.setProperty("maximumPoolSize", "1");
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
  }

  protected Integer insertRecord(Connection connection) {
    DataRecord result =
      DSL.using(connection)
        .insertInto(DATA)
        .columns(DATA.VALUE)
        .values(42)
        .returning(DATA.ID, DATA.VALUE)
        .fetchOne();

    return result.getId();
  }

  protected Boolean acquireLocked(Connection connection, int value) {
    return DSL.using(connection)
      .fetchOne("select pg_try_advisory_lock(?)", value) //acquire postgres 9.6 advisory lock
      .get(0, Boolean.class);
  }

  protected Boolean releaseLocked(Connection connection, int value) {
    return DSL.using(connection)
      .fetchOne("select pg_advisory_unlock(?)", value) //release postgres 9.6 advisory lock
      .get(0, Boolean.class);
  }

  protected CompletableFuture<Void> executeQueries(VertxTestContext testContext, Connection connection) {
    return _acquireLocked(connection, 1)
      .thenApply(acquireLocked -> {
        log.info("lock acquired : {}", acquireLocked);
        return acquireLocked;
      })
      .thenCompose(ignored -> _insertRecord(connection))
      .thenCompose(result -> _releaseLocked(connection, 1))
      .thenAccept(lockReleased -> {
        log.info("lock released : {}", lockReleased);
        testContext.completeNow();
      }).exceptionally(ex -> {
        testContext.failNow(ex);
        return null;
      });
  }

  protected abstract CompletableFuture<Integer> _insertRecord(Connection connection);

  protected abstract CompletableFuture<Boolean> _acquireLocked(Connection connection, int value);

  protected abstract CompletableFuture<Boolean> _releaseLocked(Connection connection, int value);

}
