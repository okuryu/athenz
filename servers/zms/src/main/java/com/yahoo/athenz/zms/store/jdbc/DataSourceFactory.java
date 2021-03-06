/**
 * Copyright 2016 Yahoo Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.yahoo.athenz.zms.store.jdbc;

import java.io.PrintWriter;
import java.util.concurrent.TimeUnit;
import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLException;

import org.apache.commons.dbcp2.ConnectionFactory;
import org.apache.commons.dbcp2.DriverManagerConnectionFactory;
import org.apache.commons.dbcp2.PoolableConnection;
import org.apache.commons.dbcp2.PoolableConnectionFactory;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.commons.pool2.impl.BaseObjectPoolConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yahoo.athenz.zms.ZMSConsts;

public class DataSourceFactory {

    private static final Logger LOG = LoggerFactory.getLogger(DataSourceFactory.class);

    static final String ZMS_PROP_DBPOOL_MAX_TOTAL = "athenz.zms.db_pool_max_total";
    static final String ZMS_PROP_DBPOOL_MAX_IDLE = "athenz.zms.db_pool_max_idle";
    static final String ZMS_PROP_DBPOOL_MIN_IDLE = "athenz.zms.db_pool_min_idle";
    static final String ZMS_PROP_DBPOOL_MAX_WAIT = "athenz.zms.db_pool_max_wait";
    static final String ZMS_PROP_DBPOOL_EVICT_IDLE_TIMEOUT = "athenz.zms.db_pool_evict_idle_timeout";
    static final String ZMS_PROP_DBPOOL_EVICT_IDLE_INTERVAL = "athenz.zms.db_pool_evict_idle_interval";
    static final String ZMS_PROP_DBPOOL_MAX_TTL = "athenz.zms.db_pool_max_ttl";

    static final long MAX_TTL_CONN_MS = TimeUnit.MILLISECONDS.convert(10L, TimeUnit.MINUTES);
    
    public static PoolableDataSource create(String url) {
        
        String driver = null;
        try {
            if (url.indexOf("sqlite") > 0) {
                
                driver = "org.sqlite.JDBC";
                Class.forName(driver);
                return new SimpleDataSource(driver, url);
                
            } else if (url.indexOf(":mysql:") > 0) {
                
                driver = "com.mysql.jdbc.Driver";
                Class.forName(driver);
                
                String userName = System.getProperty(ZMSConsts.ZMS_PROP_JDBC_USER);
                String password = System.getProperty(ZMSConsts.ZMS_PROP_JDBC_PASSWORD, "");
                ConnectionFactory connectionFactory =
                    new DriverManagerConnectionFactory(url, userName, password);

                return create(connectionFactory);
                
            } else {
                throw new RuntimeException("Cannot figure out how to instantiate this data source: " + url);
            }
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Cannot load driver class: " + driver);
        } catch (Exception exc) {
            throw new RuntimeException("Failed to create database source(" +
                url + ") with driver(" + driver + ")", exc);
        }
    }
    
    static long retrieveConfigSetting(String propName, long defaultValue) {

        final String errPrefix = "Using default instead. Ignoring Invalid number(";

        String propValue = System.getProperty(propName);
        if (propValue == null) {
            return defaultValue;
        }
        
        long value = defaultValue;
        try {
            value = Long.parseLong(propValue);
        } catch (NumberFormatException ex) {
            if (LOG.isWarnEnabled()) {
                LOG.warn(errPrefix + propValue + ") set in system property(" + propName + "): " + ex.getMessage());
            }
        }
        
        return value;
    }
    
    static int retrieveConfigSetting(String propName, int defaultValue) {

        final String errPrefix = "Using default instead. Ignoring Invalid number(";

        String propValue = System.getProperty(propName);
        if (propValue == null) {
            return defaultValue;
        }
        
        int value = defaultValue;
        try {
            value = Integer.parseInt(propValue);
        } catch (NumberFormatException ex) {
            if (LOG.isWarnEnabled()) {
                LOG.warn(errPrefix + propValue + ") set in system property(" + propName + "): " + ex.getMessage());
            }
        }
        
        return value;
    }
    
    public static GenericObjectPoolConfig setupPoolConfig() {
        
        // setup config vars for the object pool
        // ie. min and max idle instances, and max total instances of arbitrary objects
        
        GenericObjectPoolConfig config = new GenericObjectPoolConfig();

        // The maximum number of active connections that can be allocated from
        // this pool at the same time, or negative for no limit. Default: 8
        config.setMaxTotal(retrieveConfigSetting(ZMS_PROP_DBPOOL_MAX_TOTAL, GenericObjectPoolConfig.DEFAULT_MAX_TOTAL));
        if (config.getMaxTotal() == 0) {
            config.setMaxTotal(-1); // -1 means no limit
        }
        
        //  The maximum number of connections that can remain idle in the pool,
        // without extra ones being released, or negative for no limit. Default 8
        config.setMaxIdle(retrieveConfigSetting(ZMS_PROP_DBPOOL_MAX_IDLE, GenericObjectPoolConfig.DEFAULT_MAX_IDLE));
        if (config.getMaxIdle() == 0) {
            config.setMaxIdle(-1); // -1 means no limit
        }
        
        // The minimum number of connections that can remain idle in the pool,
        // without extra ones being created, or zero to create none. Default 0
        config.setMinIdle(retrieveConfigSetting(ZMS_PROP_DBPOOL_MIN_IDLE, GenericObjectPoolConfig.DEFAULT_MIN_IDLE));
        
        // The maximum number of milliseconds that the pool will wait (when
        // there are no available connections) for a connection to be returned
        // before throwing an exception, or -1 to wait indefinitely. Default -1
        config.setMaxWaitMillis(retrieveConfigSetting(ZMS_PROP_DBPOOL_MAX_WAIT,
                GenericObjectPoolConfig.DEFAULT_MAX_WAIT_MILLIS));
        
        // setup the configuration to cleanup idle connections
        //
        // Minimum time an object can be idle in the pool before being eligible
        // for eviction by the idle object evictor.
        // The default value is 30 minutes (1000 * 60 * 30).
        config.setMinEvictableIdleTimeMillis(retrieveConfigSetting(ZMS_PROP_DBPOOL_EVICT_IDLE_TIMEOUT,
                BaseObjectPoolConfig.DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS));
        
        // Number of milliseconds to sleep between runs of idle object evictor thread.
        // Not using DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS since it is -1
        // meaning it will not run the evictor thread and instead we're using
        // the default min value for evictable idle connections (Default 30 minutes)
        config.setTimeBetweenEvictionRunsMillis(retrieveConfigSetting(ZMS_PROP_DBPOOL_EVICT_IDLE_INTERVAL,
                BaseObjectPoolConfig.DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS));
        
        if (LOG.isDebugEnabled()) {
            LOG.debug("Config settings for idle object eviction: " +
                    "time interval between eviction thread runs(" +
                    config.getTimeBetweenEvictionRunsMillis() +
                    " millis): minimum timeout for idle objects(" +
                    config.getMinEvictableIdleTimeMillis() + " millis)");
        }
        
        // Validate objects by the idle object evictor. If invalid, gets dropped
        // from the pool.
        config.setTestWhileIdle(true);
        
        // Validate object before borrowing from pool. If invalid, gets dropped
        // from the pool and an attempt to borrow another one will occur.
        config.setTestOnBorrow(true);
        return config;
    }
    
    static PoolableDataSource create(ConnectionFactory connectionFactory) {

        // setup our pool config object
        
        GenericObjectPoolConfig config = setupPoolConfig();
        
        PoolableConnectionFactory poolableConnectionFactory =
            new PoolableConnectionFactory(connectionFactory, null);
         
        // Set max lifetime of a connection in milli-secs, after which it will
        // always fail activation, passivation, and validation.
        // Value of -1 means infinite life time. The default value
        // defined in this class is 10 minutes.
        long connTtlMillis = retrieveConfigSetting(ZMS_PROP_DBPOOL_MAX_TTL, MAX_TTL_CONN_MS);
        poolableConnectionFactory.setMaxConnLifetimeMillis(connTtlMillis);
        if (LOG.isInfoEnabled()) {
            LOG.info("Setting Time-To-Live interval for live connections(" +
                    connTtlMillis + ")milli-secs");
        }
        
        ObjectPool<PoolableConnection> connectionPool = new GenericObjectPool<>(poolableConnectionFactory, config);
        poolableConnectionFactory.setPool(connectionPool);
        
        ZMSDataSource dataSource = new ZMSDataSource(connectionPool);
        return dataSource;
    }

    static class SimpleDataSource implements PoolableDataSource {
        PrintWriter logger;
        String className;
        String url;
        
        SimpleDataSource(String className, String url) {
            this.className = className;
            this.url = url;
            this.logger = new PrintWriter(System.out);
        }
        
        public Connection getConnection() {
            try {
                Class.forName(className);
                return DriverManager.getConnection(url);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Cannot load class: " + className);
            } catch (SQLException e) {
                throw new RuntimeException("Cannot connect to database " + url);
            }
        }
        public Connection getConnection(String user, String pass) {
            return getConnection(); //use a real datasource if you want this
        }
        public int getLoginTimeout() {
            return 0;
        }
        public void setLoginTimeout(int seconds) {
        }
        public void setLogWriter(PrintWriter out) {
            logger = out;
        }
        public PrintWriter getLogWriter() {
            return logger;
        }
        public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("Not implemented");
        }

        @Override
        public void clearPoolConnections() {
        }
    }
}

