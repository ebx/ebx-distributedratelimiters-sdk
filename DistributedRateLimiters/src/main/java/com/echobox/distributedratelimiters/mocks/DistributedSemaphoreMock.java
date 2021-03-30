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

package com.echobox.distributedratelimiters.mocks;

import com.echobox.cache.CacheService;
import com.echobox.distributedratelimiters.DistributedSemaphore;
import com.echobox.shutdown.ShutdownMonitor;
import com.echobox.shutdown.ShutdownRequestedException;
import mockit.Mock;
import mockit.MockUp;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * A mock of the DistributedSemaphore rate limiter that uses an in memory semaphore
 * @author MarcF
 */
public class DistributedSemaphoreMock extends MockUp<DistributedSemaphore> {

  /**
   * Creates a new DistributedSemaphore mock
   * @param cacheService The cache service we will use
   * @param limiterId The unique id for this RateLimiter, which links distributed limiters.
   * @param concurrentPermits The maximum number of concurrent permits allowed
   * @param maxProcessRunTimeSecs The default length of time that this semaphore control item in
   *                              MEMCACHE will last for unless either a tryAcquire or release
   *                              takes place.  VERY IMPORTANT!!! The processes being protected
   *                              by this semaphore must NEVER takes more than  this interval to
   *                              ensure everything works as intended.
   * @return The mocked DistributedSemaphore
   */
  @Mock
  public static DistributedSemaphore create(CacheService cacheService, String limiterId, 
      int concurrentPermits, int maxProcessRunTimeSecs) {
    return new MockDistributedSemaphoreImpl(limiterId, concurrentPermits, maxProcessRunTimeSecs);
  }

  /**
   * The type Mock distributed semaphore.
   * @author MarcF
   */
  public static class MockDistributedSemaphoreImpl extends DistributedSemaphore {

    private Semaphore semaphore;

    /**
     * Instantiates a new in memory sempahore
     * @param limiterId the limiter id
     * @param concurrentPermits the concurrent permits
     * @param maxProcessRunTimeSecs the max process run time secs
     */
    public MockDistributedSemaphoreImpl(String limiterId, int concurrentPermits,
        int maxProcessRunTimeSecs) {
      super(null, limiterId, concurrentPermits, maxProcessRunTimeSecs);
      this.semaphore = new Semaphore(concurrentPermits);
    }

    @Override
    public boolean tryAcquire(ShutdownMonitor shutdownMonitor, int timeoutSeconds)
        throws ShutdownRequestedException, InterruptedException {
      boolean aquired = semaphore.tryAcquire(timeoutSeconds, TimeUnit.SECONDS);
      return aquired;
    }

    @Override
    public void release(ShutdownMonitor shutdownMonitor)
        throws InterruptedException, ShutdownRequestedException {
      semaphore.release();
    }

    @Override
    public synchronized void clearAllSemaphorePermits() {
      // No-op in this implementation
    }
  }
}
