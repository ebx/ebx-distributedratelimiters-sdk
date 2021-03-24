/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.echobox.distributedratelimiters.demo;

import com.echobox.cache.CacheService;
import com.echobox.cache.impl.RedisCacheService;
import com.echobox.shutdown.ShutdownMonitor;
import com.echobox.shutdown.impl.SimpleShutdownMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

/**
 * Demo base class that defines/initialises global constants and variables.
 *
 * @author MarcF
 */
public class DemoBase {

  /**
   * A global shutdown monitor that can be hooked for application shutdown events
   */
  public static final ShutdownMonitor SHUTDOWN_MONITOR = new SimpleShutdownMonitor();

  /**
   * A logging instance for this class
   */
  private static Logger logger = LoggerFactory.getLogger(DemoBase.class);
 
  /**
   * Get the cache service
   * @return The cache service we wish to use for our demos
   * @throws Exception 
   */
  public static CacheService getCacheService() throws Exception {
    return RedisCacheService.getInstance();
  }

  /**
   * Initialise the database connections using the provided connection mode. If database connections
   * have already been initialised they will be closed before using the new connection mode.
   *
   * @param fileName the file name
   * @throws Exception the exception
   */
  public static void initialiseCache(String fileName) throws Exception {

    if (RedisCacheService.getInstance().isCacheAvailable()) {
      RedisCacheService.shutdown();
    }

    // Determine where the file should be located
    String dbCredsLocation =
        System.getProperty("user.dir") + "/DistributedRateLimitersDemo/" + fileName;

    // Get the input stream
    InputStream is = new FileInputStream(dbCredsLocation);

    // Parse the properties
    Properties props = new Properties();
    props.load(is);
    is.close();

    String cacheClusterEndPoint = props.getProperty("cachecluster_endpoint");
    String cacheClusterPortStr = props.getProperty("cachecluster_port");
    Integer cacheClusterPort =
        cacheClusterPortStr == null ||  cacheClusterPortStr.isEmpty() 
        ? null : Integer.parseInt(cacheClusterPortStr);

    RedisCacheService.initialise(cacheClusterEndPoint, cacheClusterPort, 
        SHUTDOWN_MONITOR);

    logger.info(
        "Initialised database connections to " + fileName + " using " + dbCredsLocation + " ("
            + System.getProperty("java.version") + ").");
  }

  /**
   * Closes the database connections to the mysql and the mongodb database
   */
  public static void shutdownDBConnections() {
    RedisCacheService.shutdown();
  }
}
