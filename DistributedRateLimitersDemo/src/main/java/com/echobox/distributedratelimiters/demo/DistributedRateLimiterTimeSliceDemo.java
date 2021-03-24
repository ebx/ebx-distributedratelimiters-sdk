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
import com.echobox.distributedratelimiters.DistributedRateLimiterTimeSlice;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The type Distributed rate limiter demo.
 * @author MarcF
 */
public class DistributedRateLimiterTimeSliceDemo {

  private static Logger logger = LoggerFactory.getLogger(DistributedRateLimiterTimeSliceDemo.class);

  /**
   * Run a distributed rate limiter, acquiring 100 permits at a rate specified
   * @param args the input arguments
   * @throws Exception the exception
   */
  public static void main(String[] args) throws Exception {

    // Requires Memcached by default
    DemoBase.initialiseCache("src/main/resources/app.properties");
    
    CacheService cacheService = DemoBase.getCacheService();

    //Use a random string as the rate limiter idenifier to ensure multiple tests don't clash
    String uniqueCacheKeyPrefix = RandomStringUtils.randomAlphabetic(5);
    int permitsPerTimeInterval = 3;
    int timeIntervalSeconds = 1;
    DistributedRateLimiterTimeSlice distributedRateLimiter = DistributedRateLimiterTimeSlice
        .create(cacheService, uniqueCacheKeyPrefix, permitsPerTimeInterval, timeIntervalSeconds);

    //We will run the test for 120 seconds
    long endTime = cacheService.getUnixTimeSupplier().get() + 120;
    do {
      distributedRateLimiter.acquire(DemoBase.SHUTDOWN_MONITOR);
      logger.info("Permit aquired");
    } while (cacheService.getUnixTimeSupplier().get() < endTime);
  }
}
