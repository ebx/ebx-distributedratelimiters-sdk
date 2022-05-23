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

import java.util.function.Supplier;

/**
 * A suitable base class for the shared functionality of any rate limit 'like' tools.
 * @author MarcF
 */
public abstract class DistributedRateLimiterBase {

  /**
   * The cache service we use
   */
  protected CacheService cacheService;
  
  /**
   * The constructor
   * @param cacheService The cache service we use
   */
  protected DistributedRateLimiterBase(CacheService cacheService) {
    this.cacheService = cacheService;
  }
  
  /**
   * Make a single attempt to acquire a permit, returns immediately
   * @return True if a permit was acquired in the available time, false otherwise
   */
  public boolean tryAcquire() {
    try {
      return tryAcquire(null, 0);
    } catch (ShutdownRequestedException | InterruptedException e) {
      return false;
    }
  }
  
  /**
   * Attempt to acquire a permit in the provided time allowed.
   * @param shutdownMonitor the shutdown monitor
   * @param timeoutSeconds The number of seconds over which to attempt the acquire. Use  a
   *                       timeout of zero to only make a single attempt without waiting.
   * @return True if a permit was acquired in the available time, false otherwise
   * @throws ShutdownRequestedException This method *may* throw a ShutdownRequestedException in
   * the event of a request to shutdown, but this is not guaranteed
   * @throws InterruptedException the interrupted exception
   */
  public boolean tryAcquire(ShutdownMonitor shutdownMonitor, int timeoutSeconds)
      throws ShutdownRequestedException, InterruptedException {
    return tryAcquire(shutdownMonitor, timeoutSeconds, this::tryAcquireInternal);
  }

  /**
   * Attempt to acquire a permit in the provided time allowed.
   * @param shutdownMonitor the shutdown monitor
   * @param timeoutSeconds The number of seconds over which to attempt the acquire. Use  a
   *                       timeout of zero to only make a single attempt without waiting.
   * @param tryAcquireFunc The function that attempts to get the permit
   * @return True if a permit was acquired in the available time, false otherwise
   * @throws ShutdownRequestedException This method *may* throw a ShutdownRequestedException in
   * the event of a request to shutdown, but this is not guaranteed
   * @throws InterruptedException the interrupted exception
   */
  protected boolean tryAcquire(ShutdownMonitor shutdownMonitor, int timeoutSeconds,
      Supplier<Boolean> tryAcquireFunc) throws ShutdownRequestedException, InterruptedException {

    if (timeoutSeconds < 0) {
      throw new IllegalArgumentException("timeoutSeconds cannot be negative.");
    }
    if (timeoutSeconds > 0 && shutdownMonitor == null) {
      throw new IllegalArgumentException("shutdownMonitor cannot be null if a timeout is used.");
    }
  
    if (shutdownMonitor != null && shutdownMonitor.isShutdownRequested()) {
      throw new ShutdownRequestedException();
    }
  
    if (cacheService.isCacheAvailable()) {
      boolean permitAquired = false;
      if (timeoutSeconds == 0) {
        //If the timeout is zero the attempt is one shot, no need for additional logic
        permitAquired = tryAcquireFunc.get();
      } else {
        long startTime = cacheService.getUnixTimeSupplier().get();
        do {
          permitAquired = tryAcquireFunc.get();

          if (!permitAquired && !shutdownMonitor.isShutdownRequested()
              && cacheService.getUnixTimeSupplier().get() < startTime + timeoutSeconds) {
            // Sleep for an appropriate length of time to avoid thrashing Memcached
            synchronized (shutdownMonitor) {
              shutdownMonitor.wait(getSleepTimeBetweenAcquireAttempts());
            }
          }
        } while (!permitAquired && !shutdownMonitor.isShutdownRequested()
            && cacheService.getUnixTimeSupplier().get() < startTime + timeoutSeconds);
      }

      if (shutdownMonitor != null && shutdownMonitor.isShutdownRequested()) {
        throw new ShutdownRequestedException();
      } else {
        return permitAquired;
      }
    } else {
      throw new IllegalStateException(
          "Cache must be available for the distributed rate limiter to work correctly.");
    }
  }

  /**
   * Attempt to get the respective permit.
   * @return true if the permit was acquired, otherwise false
   */
  protected abstract boolean tryAcquireInternal();

  /**
   * Gets sleep time between acquire attempts.
   *
   * @return The number of milliseconds to wait between permit acquire attempts
   */
  protected abstract long getSleepTimeBetweenAcquireAttempts();
}
