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

package com.echobox.distributedratelimiters;

import com.echobox.cache.CacheService;
import com.echobox.shutdown.ShutdownMonitor;
import com.echobox.shutdown.ShutdownRequestedException;

import java.util.Date;
import java.util.Random;
import java.util.concurrent.TimeoutException;

/**
 * A rate limiter which can be used in a distributed environment to control the rate of
 * a process by issuing a specified number of permits spread equally over a specified time interval
 *
 * (eg. 5 permits per second, 3 permits every 10 seconds etc, 1 permit every 30 seconds)
 *
 * This DistributedRateLimiter uses a cache to create a cache key unique for each per-permit time
 * slice and will only issue one permit for that time-slice.
 *
 * @author MarcF
 */
public class DistributedRateLimiterTimeSlice extends DistributedRateLimiterBase {

  /**
   * The number of permits per time interval
   */
  protected double permitsPerTimeInterval;

  /**
   * The time interval across which we issue permits at equal spacing
   */
  protected int timeIntervalSeconds;

  /**
   * A unique cache key prefix for the cache elements used by this rate limiter
   */
  protected String uniqueCacheKeyPrefix;

  /**
   * A random number generator
   */
  private final Random rand = new Random();

  /**
   * Create distributed rate limiter.
   * 
   * @param cacheService The cache service we use
   * @param limiterId The unique id for this RateLimiter, which links distributed limiters.
   * @param permitsPerTimeInterval The number of permits per time interval allowed
   * @param timeIntervalSeconds The duration of the time interval in seconds
   * @return An appropriate RateLimiter
   */
  public static DistributedRateLimiterTimeSlice create(CacheService cacheService, 
      String limiterId, int permitsPerTimeInterval,
      int timeIntervalSeconds) {
    // time unit is a second or below
    return new DistributedRateLimiterTimeSlice(cacheService, limiterId, permitsPerTimeInterval, 
        timeIntervalSeconds);
  }

  private DistributedRateLimiterTimeSlice(CacheService cacheService, 
      String limiterId, int permitsPerTimeInterval,
      int timeIntervalSeconds) {
    super(cacheService);

    if (limiterId == null || limiterId.isEmpty()) {
      throw new IllegalArgumentException("limiterId must not be zero length");
    }
    if (permitsPerTimeInterval <= 0) {
      throw new IllegalArgumentException("permitsPerTimeInterval must be greater than zero");
    }
    if (timeIntervalSeconds <= 0) {
      throw new IllegalArgumentException("timeIntervalSeconds must be greater than zero");
    }

    this.permitsPerTimeInterval = permitsPerTimeInterval;
    this.timeIntervalSeconds = timeIntervalSeconds;
    this.uniqueCacheKeyPrefix = "DistributedRateLimiterTimeSlice_" + limiterId + "_";
  }

  /**
   * Acquires a permit and then returns when succesfull.
   * @param shutdownMonitor the shutdown monitor
   * @throws ShutdownRequestedException the shutdown requested exception
   * @throws InterruptedException the interrupted exception
   */
  public void acquire(ShutdownMonitor shutdownMonitor)
      throws ShutdownRequestedException, InterruptedException {
    //Return only once we have acquired the permit
    tryAcquire(shutdownMonitor, Integer.MAX_VALUE);
  }

  /**
   * acquires a permit with a timeout
   * @param shutdownMonitor the shutdown monitor
   * @param timeOutSeconds the time out seconds
   * @throws InterruptedException the interrupted exception
   * @throws ShutdownRequestedException the shutdown requested exception
   * @throws TimeoutException the timeout exception
   */
  public void acquire(ShutdownMonitor shutdownMonitor, int timeOutSeconds)
      throws InterruptedException, ShutdownRequestedException, TimeoutException {
    boolean acquired = tryAcquire(shutdownMonitor, timeOutSeconds);
    if (!acquired) {
      throw new TimeoutException(
          "Failed to get rate limit permit in " + timeOutSeconds + " " + "seconds for "
              + uniqueCacheKeyPrefix);
    }
  }

  /* (non-Javadoc)
   * @see com.echobox.distributedratelimiters.DistributedRateLimiterBase#tryAcquireInterval()
   */
  @Override
  protected boolean tryAcquireInternal() {
    long timeInMilliseconds = new Date().getTime();
    return cacheService
        .tryAddItemToCache(getCacheKeyForTime(timeInMilliseconds), getCacheExpiryTimeSeconds(),
            timeInMilliseconds);
  }

  @Override
  protected long getSleepTimeBetweenAcquireAttempts() {
    // Sleep for 'on average' half the timePerPermitInMilliseconds.
    // We use a random number so that the rate is more evenly distributed across all 
    // instances
    double timePerPermitInMilliseconds =
        1000 * (double) timeIntervalSeconds / (double) permitsPerTimeInterval;
    //+1 so that we never return 0
    return (long) (timePerPermitInMilliseconds * rand.nextDouble()) + 1;
  }

  /**
   * Gets cache key for time.
   *
   * @param currentTimeInMilliseconds the current time in milliseconds
   * @return The key we use for the cache element we use to control the rate at a given time
   */
  protected String getCacheKeyForTime(long currentTimeInMilliseconds) {
    return uniqueCacheKeyPrefix + getPermitTimeWindowKey(currentTimeInMilliseconds);
  }

  /**
   * Get a string that represents the part of the time window we are currently in, i.e
   * the start of the time window would return 0, and the end of the time window would return
   * something equivalent to the current position in timeIntervalSeconds and the time slice
   * allowed for 1 permit.
   * @param currentTimeInMilliseconds the current time in milliseconds
   * @return permit time window key
   */
  protected String getPermitTimeWindowKey(long currentTimeInMilliseconds) {

    long startOfTimeIntervalInMilliseconds =
        (currentTimeInMilliseconds / (1000 * timeIntervalSeconds)) * (1000 * timeIntervalSeconds);
    long durationIntoTimeIntervalInMilliseconds =
        currentTimeInMilliseconds - startOfTimeIntervalInMilliseconds;
    long perPermitMillseconds = (long) ((1000 * timeIntervalSeconds) / permitsPerTimeInterval);

    return new Long(durationIntoTimeIntervalInMilliseconds / perPermitMillseconds).toString();
  }

  /**
   * Gets cache expiry time seconds.
   *
   * @return The expiry time in seconds of the cache elements we use to control the rate
   */
  protected int getCacheExpiryTimeSeconds() {
    return timeIntervalSeconds;
  }
}
