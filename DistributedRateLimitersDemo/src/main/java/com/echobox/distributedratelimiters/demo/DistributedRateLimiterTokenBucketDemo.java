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
import com.echobox.distributedratelimiters.DistributedRateLimiterTokenBucket;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.math3.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * The type Distributed rate limiter demo.
 * @author MarcF
 */
public class DistributedRateLimiterTokenBucketDemo {

  private static Logger logger =
      LoggerFactory.getLogger(DistributedRateLimiterTokenBucketDemo.class);

  /**
   * Run a distributed rate limiter, acquiring 100 permits at a rate specified
   * @param args the input arguments
   * @throws Exception the exception
   */
  public static void main(String[] args) throws Exception {

    // Requires Memcached by default
    DemoBase.initialiseCache("src/main/resources/app.properties");
    
    CacheService cacheService = DemoBase.getCacheService();
    
    int bucketSize = 10;

    //Use a random string as the rate limiter idenifier to ensure multiple tests don't clash
    String uniqueCacheKeyPrefix = RandomStringUtils.randomAlphabetic(5);
    int permitsPerTimeInterval = 5;
    int timeIntervalSeconds = 1;
    DistributedRateLimiterTokenBucket distributedRateLimiter = DistributedRateLimiterTokenBucket
        .create(cacheService, uniqueCacheKeyPrefix, bucketSize, permitsPerTimeInterval, 
            timeIntervalSeconds);

    //We will run the test for 30 seconds
    long startTime = cacheService.getUnixTimeSupplier().get();
    long scheduledEndTime = startTime + 30;
    
    double aquired = 0;
    
    Map<Long, AtomicLong> acquiresInSecond = new HashMap<>();
    
    do {
      long tryAcquireTime = cacheService.getUnixTimeSupplier().get();
      boolean acquired = distributedRateLimiter.tryAcquire(1);
      if (acquired) {
        if (!acquiresInSecond.containsKey(tryAcquireTime)) {
          acquiresInSecond.put(tryAcquireTime, new AtomicLong());
        }
        acquiresInSecond.get(tryAcquireTime).incrementAndGet();
        
        logger.info("Permit aquired");
        aquired++;
      } else {
        logger.info("Permit rejected");
      }
    } while (cacheService.getUnixTimeSupplier().get() < scheduledEndTime);

    long endTime = cacheService.getUnixTimeSupplier().get();
    double durationSeconds = endTime - startTime;
    logger.info("Aquisition Rate:" + (aquired / durationSeconds));
    
    List<Pair<Long, AtomicLong>> acquireList = acquiresInSecond.entrySet().stream()
        .map(x -> new Pair<>(x.getKey(), x.getValue())).collect(Collectors.toList());
    acquireList.sort((p1, p2) -> p1.getKey().compareTo(p2.getKey()));
    
    //Write out the acquire profile
    try (PrintWriter wr = new PrintWriter("output.csv", "UTF-8")) {
      wr.write("unixTime,acquires\n");
      for (Pair<Long, AtomicLong> entry : acquireList) {
        wr.write(entry.getKey() + "," + entry.getValue() + "\n");
      }
    }
  }
}
