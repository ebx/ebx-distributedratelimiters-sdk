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

import com.echobox.distributedratelimiters.DistributedRateLimiterTimeSlice;
import com.echobox.shutdown.ShutdownMonitor;
import com.echobox.shutdown.ShutdownRequestedException;
import com.google.common.util.concurrent.RateLimiter;
import mockit.Mock;
import mockit.MockUp;

import java.util.concurrent.TimeUnit;

/**
 * A mock of the DistributedRateLimiterTimeSlice rate limiter that uses an in memory rate limiter
 * @author MarcF
 */
public class DistributedRateLimiterTimeSliceMock extends MockUp<DistributedRateLimiterTimeSlice> {

  private RateLimiter rateLimiter;

  /**
   * Create a new DistributedRateLimiterTimeSlice mock
   * @param permitsPerTimeInterval The number of permits per time interval allowed
   * @param timeIntervalSeconds The duration of the time interval in seconds
   */
  public DistributedRateLimiterTimeSliceMock(int permitsPerTimeInterval, int timeIntervalSeconds) {
    double permitsPerSecond = (double) permitsPerTimeInterval / (double) timeIntervalSeconds;
    this.rateLimiter = RateLimiter.create(permitsPerSecond);
  }

  /**
   * Acquires a permit and then returns when successful.
   * @param shutdownMonitor the shutdown monitor. Unused by this mock.
   * @throws ShutdownRequestedException the shutdown requested exception
   * @throws InterruptedException the interrupted exception
   */
  @Mock
  public void acquire(ShutdownMonitor shutdownMonitor) throws ShutdownRequestedException, 
    InterruptedException {
    rateLimiter.acquire();
  }

  /**
   * Attempt to acquire a permit in the provided time allowed.
   * @param shutdownMonitor the shutdown monitor. Unused by this mock.
   * @param timeoutSeconds The number of seconds over which to attempt the acquire. Use  a
   *                       timeout of zero to only make a single attempt without waiting.
   * @return True if a permit was acquired in the available time, false otherwise
   * @throws ShutdownRequestedException This method *may* throw a ShutdownRequestedException in
   * the event of a request to shutdown, but this is not guaranteed
   * @throws InterruptedException the interrupted exception
   */
  @Mock
  public boolean tryAcquire(ShutdownMonitor shutdownMonitor, int timeoutSeconds)
      throws ShutdownRequestedException, InterruptedException {
    return rateLimiter.tryAcquire(timeoutSeconds, TimeUnit.SECONDS);
  }
}
