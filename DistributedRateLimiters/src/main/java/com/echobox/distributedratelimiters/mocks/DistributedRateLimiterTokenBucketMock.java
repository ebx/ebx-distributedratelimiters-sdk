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
import com.echobox.distributedratelimiters.DistributedRateLimiterTokenBucket;
import mockit.Mock;
import mockit.MockUp;

/**
 * A mock of the DistributedRateLimiterTokenBucket class that allows an unlimited number of 
 * acquires
 * @author MarcF
 */
public class DistributedRateLimiterTokenBucketMock
    extends MockUp<DistributedRateLimiterTokenBucket> {
  
  private DistributedRateLimiterTokenBucket mock;
  
  /**
   * The constructor
   * @param mock the object to use as instance
   */
  public DistributedRateLimiterTokenBucketMock(DistributedRateLimiterTokenBucket mock) {
    this.mock = mock;
  }
  
  /**
   * Creates a new DistributedRateLimiterTokenBucket mock
   * @param cacheService The cache service we use
   * @param limiterId The unique id for this RateLimiter, which links distributed limiters.
   * @param bucketSize The size of the bucket specified as total number of tokens it can hold
   * @param permitsPerTimeInterval The number of permits per time interval allowed
   * @param timeIntervalSeconds The duration of the time interval in seconds
   * @return The mocked DistributedRateLimiterTokenBucket
   */
  @Mock
  public DistributedRateLimiterTokenBucket create(CacheService cacheService,
      String limiterId, int bucketSize, int permitsPerTimeInterval, int timeIntervalSeconds) {
    return mock;
  }
  
  /**
   * Mocks the tryAcquireInternal method
   * @return For now this mock allows an unlimited number of acquires by always returning true
   */
  @Mock
  protected boolean tryAcquireInternal() {
    return true;
  }
}
