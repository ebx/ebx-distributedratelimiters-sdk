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
import com.google.gson.reflect.TypeToken;

import java.util.Random;

/**
 * A semaphore which can be used in a distributed environment to control the number of concurrent
 * permits issued to a process (eg. 10 concurrent requests )
 *
 * This DistributedSemaphore uses Memcached to create a cache key containing a counter
 * of the number of concurrent permits issued
 *
 * VERY IMPORTANT INFORMATION - Distributed semaphores are very tricky to make resilient. It's not
 * possible to tell between a very long running process and one that has crashed, i.e. we don't know
 * if an acquired permit will EVER be released.
 * In order to TRY and protect against this a maximum process run time must be provided
 * which, if no tryAcquires or releases take place in this timeframe, the semaphore will be reset.
 * This ensures that any LOST permits can 'eventually' be recovered in periods of inactivity.
 *
 * Importantly this also means if the processes protected by this semaphore take longer than
 * the provided maximum process run time, this semaphore may not exhibit the expected behaviour
 * (so be warned!)
 *
 * @author MarcF
 */
public class DistributedSemaphore extends DistributedRateLimiterBase {

  /**
   * The maximum number of concurrent permits granted
   */
  protected int concurrentPermits;

  /**
   * A unique cache key prefix for the cache elements used by this rate limiter
   */
  protected String uniqueCacheKeyPrefix;

  /**
   * The default length of time that our semaphore control item in memcache will last for unless
   * either a tryAcquire or release takes place. It's important that the processes being protected
   * by this semaphore NEVER takes more than this interval to ensure everything works as
   * intended.
   */
  protected int cacheKeyExpireTimeSeconds;

  /**
   * A random number generator
   */
  private final Random rand = new Random();

  /**
   * Create a new distributed semaphore
   * @param cacheService The cache service we will use
   * @param limiterId The unique id for this RateLimiter, which links distributed limiters.
   * @param concurrentPermits The maximum number of concurrent permits allowed
   * @param maxProcessRunTimeSecs The default length of time that this semaphore control item in
   *                              MEMCACHE will last for unless either a tryAcquire or release
   *                              takes place.  VERY IMPORTANT!!! The processes being protected
   *                              by this semaphore must NEVER takes more than  this interval to
   *                              ensure everything works as intended.
   * @return An appropriate DistributedSemaphore
   */
  public static DistributedSemaphore create(CacheService cacheService, String limiterId, 
      int concurrentPermits, int maxProcessRunTimeSecs) {

    //Let's add a little protection against trying to use this for long running processes
    //which may introduce it's own problems!
    if (maxProcessRunTimeSecs > 120) {
      throw new IllegalArgumentException("This exception is here to protect you. If you are "
          + "trying to create a distributed semaphore for long running tasks there may be "
          + "additional problems caused by lost PERMITS. I'd strongly urge you to consider"
          + "a different implementation that doesn't require this semaphore.");
    }

    return new DistributedSemaphore(cacheService, limiterId, concurrentPermits, 
        maxProcessRunTimeSecs);
  }

  /**
   * Instantiates a new Distributed semaphore.
   * 
   * @param cacheService The cache service we are using
   * @param limiterId the limiter id
   * @param concurrentPermits The number of concurrent permits
   * @param cacheKeyExpireTimeSeconds The cache key expire time in seconds
   */
  protected DistributedSemaphore(CacheService cacheService, String limiterId, int concurrentPermits,
      int cacheKeyExpireTimeSeconds) {

    super(cacheService);
    if (limiterId == null || limiterId.isEmpty()) {
      throw new IllegalArgumentException("limiterId must not be zero length");
    }
    if (concurrentPermits <= 0) {
      throw new IllegalArgumentException("concurrentPermits must be greater than zero");
    }
    if (cacheKeyExpireTimeSeconds <= 0) {
      throw new IllegalArgumentException("cacheKeyExpireTimeSeconds must be greater than zero");
    }

    this.concurrentPermits = concurrentPermits;
    this.uniqueCacheKeyPrefix = "DistributedSemaphore_" + limiterId + "_";
    this.cacheKeyExpireTimeSeconds = cacheKeyExpireTimeSeconds;
  }

  @Override
  protected boolean tryAcquireInternal() {
    return cacheService.tryToIncrementOrDecrementCounterInCacheIfBelowLimit(uniqueCacheKeyPrefix,
        cacheKeyExpireTimeSeconds, concurrentPermits, false);
  }

  /**
   * Release the permit. Is it VITAL that this is called when the protected process is finished
   * so that other processes may pickup the released permit.
   * @param shutdownMonitor the shutdown monitor
   * @throws InterruptedException the interrupted exception
   * @throws ShutdownRequestedException This method *may* throw a ShutdownRequestedException in
   * the event of a request to shutdown, but this is not guaranteed
   */
  public void release(ShutdownMonitor shutdownMonitor)
      throws InterruptedException, ShutdownRequestedException {

    if (shutdownMonitor == null) {
      throw new IllegalArgumentException("shutdownMonitor cannot be null.");
    }

    if (cacheService.isCacheAvailable()) {
      boolean permitReleased = false;
      do {

        Long permits = cacheService.tryGetCachedItem(uniqueCacheKeyPrefix, new TypeToken<Long>(){});
        if (permits == null || permits.longValue() <= 0) {
          return;
        }

        permitReleased = cacheService
            .tryToIncrementOrDecrementCounterInCacheIfBelowLimit(uniqueCacheKeyPrefix,
                cacheKeyExpireTimeSeconds, concurrentPermits, true);

        if (!permitReleased && !shutdownMonitor.isShutdownRequested()) {
          // Sleep for an appropriate length of time to avoid thrashing Memcached
          synchronized (shutdownMonitor) {
            shutdownMonitor.wait(getSleepTimeBetweenAcquireAttempts());
          }
        }
      } while (!permitReleased && !shutdownMonitor.isShutdownRequested());

      if (shutdownMonitor.isShutdownRequested()) {
        throw new ShutdownRequestedException();
      }
    } else {
      throw new IllegalStateException(
          "Cache must be available for the distributed " + "semaphore to work correctly.");
    }
  }

  /**
   * Resets this semaphore permits back to zero, i.e. ensures that there are no outstanding acquires
   */
  public synchronized void clearAllSemaphorePermits() {
    if (cacheService.isCacheAvailable()) {
      cacheService.trySaveItemToCache(uniqueCacheKeyPrefix, cacheKeyExpireTimeSeconds, 0L);
    } else {
      throw new IllegalStateException(
          "Cache must be available for the distributed " + "semaphore to work correctly.");
    }
  }

  @Override
  protected long getSleepTimeBetweenAcquireAttempts() {
    //+1 so that we never return 0
    return (long) (100 * rand.nextDouble()) + 1;
  }
}
