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
import com.echobox.shutdown.ShutdownRequestedException;

import java.util.Random;

/**
 * A rate limiter which can be used in a distributed environment to control the rate of
 * a process by issuing permits obtained from a fixed size leaky bucket
 *
 * (eg. 5 permits per second, 3 permits every 10 seconds etc, 1 permit every 30 seconds)
 * 
 * This algorithm is form of Leaky Bucket Algorithm, where permitsPerTimeInterval tokens are
 * "leaked" from a bucket of total capacity specified by bucketSize every timeIntervalSeconds,
 * and where permits are acquired if the bucket can accommodate a new token.
 *
 * This DistributedRateLimiterTokenBucket divides the bucket into a ring of fixed-size slices
 * - the token capacity of each slice is set to the number of permitsPerTimeInterval, with the 
 * total number of slices determined by the total bucketSize.
 * 
 * Each bucket slice exists until a specified time before it expires, and
 * is replaced by a new empty bucket slice.
 * 
 * The expire times of the bucket slices are staggered so that the one bucket slice expires every
 * timeIntervalSeconds, with each of the bucket slices in the ring being expired in turn in a 
 * round robin fashion.
 *
 * When a request to acquire a permit is received, a token is attempted to be added to the
 * bucket slice next in line to expire.  
 * 
 * We keep a counter in Memcached specifying the number of attempts made to add tokens to 
 * each bucket slice,  these counters reset at the same times as the corresponding slices expire.
 * 
 * If a token is attempted to be added to a bucket slice, and the number of attempts for that slice 
 * is less than the slice capacity, the token is added to the slice and the permit acquired.
 * 
 * Adding a token to a slice would place it over capacity, attempts are made to add the 
 * token to each subsequent slice (overflow) in the ring in turn, stopping and acquiring the permit 
 * when we successfully add a token to subsequent bucket.
  
 * If we are not able to add a token to any of the subsequent slices, we know we have reached 
 * the maximum number of tokens in the entire bucket, and the request for the permit is rejected
 * 
 * @author MarcF
 */
public class DistributedRateLimiterTokenBucket extends DistributedRateLimiterBase {
  
  /**
   * Default number of tokens to acquire
   */
  protected static final int DEFAULT_NUM_TOKENS = 1;
  
  /**
   * The number of permits per time interval
   */
  protected final double permitsPerTimeInterval;

  /**
   * The time interval across which we issue permits at equal spacing
   */
  protected final int timeIntervalSeconds;

  /**
   * A unique cache key prefix for the cache elements used by this rate limiter
   */
  protected final String uniqueCacheKeyPrefix;
  
  /**
   * The number of tokens that this bucket can hold it total
   */
  protected final int bucketSize;
  
  /**
   * We divide the bucket into slices, each bucket slice is valid for bucketSliceTimeDurationSeconds
   *( same as timeIntervalSeconds) before the tokens in the bucket expire
   */
  protected final int bucketSliceSize;
  
  /**
   * The number of slices in the bucket - this is calculated using the bucketSliceSize and
   * the total bucket size
   */
  protected final int numberOfBucketSlices;
  
  /**
   * Each bucket slice is valid for bucketSliceTimeDuration
   * seconds ( same as timeIntervalSeconds) before the tokens in the bucket expire
   */
  protected final int bucketSliceTimeDurationSeconds;

  /**
   * A random number generator
   */
  private final Random rand = new Random();
  
  /**
   * Create distributed rate limiter.
   * 
   * @param cacheService The cache service we use
   * @param limiterId The unique id for this RateLimiter, which links distributed limiters.
   * @param bucketSize The size of the bucket specified as total number of tokens it can hold
   * @param permitsPerTimeInterval The number of permits per time interval allowed
   * @param timeIntervalSeconds The duration of the time interval in seconds
   * @return An appropriate RateLimiter
   */
  public static DistributedRateLimiterTokenBucket create(CacheService cacheService, 
      String limiterId, int bucketSize,   int permitsPerTimeInterval, int timeIntervalSeconds) {
    // time unit is a second or below
    return new DistributedRateLimiterTokenBucket(cacheService, limiterId, bucketSize, 
        permitsPerTimeInterval, 
        timeIntervalSeconds);
  }

  private DistributedRateLimiterTokenBucket(CacheService cacheService, String limiterId, 
      int bucketSize, int permitsPerTimeInterval, int timeIntervalSeconds) {

    super(cacheService);
    this.bucketSize = bucketSize;
    this.permitsPerTimeInterval = permitsPerTimeInterval;
    this.timeIntervalSeconds = timeIntervalSeconds;
    this.uniqueCacheKeyPrefix = "DistributedRateLimiterTokenBucket_" + limiterId + "_";
    this.bucketSliceSize = permitsPerTimeInterval;
    
    if (limiterId == null || limiterId.isEmpty()) {
      throw new IllegalArgumentException("limiterId must not be zero length");
    }
    if (permitsPerTimeInterval <= 0) {
      throw new IllegalArgumentException("permitsPerTimeInterval must be greater than zero");
    }
    if (timeIntervalSeconds <= 0) {
      throw new IllegalArgumentException("timeIntervalSeconds must be greater than zero");
    }
    if (bucketSize % permitsPerTimeInterval != 0) {
      throw new IllegalArgumentException("Token bucket size must be an integer multiple of"
          + " permitsPerTimeInterval");
    }
    this.numberOfBucketSlices = bucketSize / permitsPerTimeInterval;
    this.bucketSliceTimeDurationSeconds = timeIntervalSeconds;
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
   * Gets cache expiry time seconds.
   *
   * @return The expiry time in seconds of the cache elements we use to control the rate
   */
  protected int getCacheExpiryTimeSeconds() {
    return timeIntervalSeconds;
  }
  
  /**
   * Make a single attempt to acquire a permit, returns immediately
   * @param numTokens the tokens to acquire
   * @return True if a permit was acquired in the available time, false otherwise
   */
  public boolean tryAcquire(int numTokens) {
    try {
      return tryAcquire(null, 0, () -> tryAcquireInternal(numTokens));
    } catch (ShutdownRequestedException | InterruptedException e) {
      return false;
    }
  }
  
  /*
   * (non-Javadoc)
   *
   * @see com.echobox.distributedratelimiters.DistributedRateLimiterBase#tryAcquireInterval()
   */
  @Override
  protected boolean tryAcquireInternal() {
    return tryAcquireInternal(DEFAULT_NUM_TOKENS);
  }

  private boolean tryAcquireInternal(int numTokens) {
    boolean success = false;
    
    long calculationTime = cacheService.getUnixTimeSupplier().get();
    
    // Obtain the bucket slice indexes in a sequence starting with the slice corresponding to the
    // calculation time.. and which addresses all buckets in a round robin
    int[] bucketSliceIndexSequence = new int[numberOfBucketSlices];
    int bucketSliceStartIndex = getBucketSliceIndex(calculationTime);
    int bucketSliceIndex = bucketSliceStartIndex;
    for (int i = 0; i < this.numberOfBucketSlices; i++) {
      bucketSliceIndexSequence[i] = bucketSliceIndex;
      bucketSliceIndex++;
      if (bucketSliceIndex == numberOfBucketSlices) {
        bucketSliceIndex = 0;
      }
    }
    // Set the initial expire time of the first bucket slice to be bucketSliceTimeDurationSeconds 
    // ahead.
    int bucketSliceExpirySecs = bucketSliceTimeDurationSeconds;
    
    // Loop through all bucket slices in the sequence.. stopping when we successfully add a token
    // to a bucket slice, or when we have looped through all buckets
    for (int index : bucketSliceIndexSequence) {
      // Attempt to add the token to the bucket slice
      success = addToBucketSlice(index, numTokens, bucketSliceExpirySecs);
      if (success) {
        break;
      } else {
        // If we need to advance into another slice (due to the size of the bucket) the expiry
        //time of that acquire will be bucketSliceTimeDurationSeconds ahead 
        // of the previous slice in the sequence
        bucketSliceExpirySecs = bucketSliceExpirySecs + bucketSliceTimeDurationSeconds;
      }
    }
    
    return success;
  }

  /**
   * Calculates the bucket slice index corresponding to a particular acquire time.  The bucket
   * slices are accessed in a time-dependent round robin strategy.  
   * 
   * @param calculationTime The time at which we are attempting to acquire the permit
   * @return The index of the bucket slice corresponding to the calculation time.
   */
  private int getBucketSliceIndex(long calculationTime) {
    int bucketSliceIndex = (int) (((double) calculationTime) / bucketSliceTimeDurationSeconds) 
        % numberOfBucketSlices;
    return bucketSliceIndex;
  }

  /**
   * Attempts to add a token to the specified bucket slice, initialising the bucket if not already
   * present in the cache with the specified expire time
   * 
   * @param bucketSliceIndex The index of the bucket slice we wish to add a token to
   * @param numTokens the number of tokens to add in the bucket
   * @param expiryTime The expire time used to initialise the counter for this bucket if the bucket
   * has not been found in the cache.
   * @return true if the token was added to the bucket, false otherwise
   */
  private boolean addToBucketSlice(int bucketSliceIndex, int numTokens, int expiryTime) {
    String key = getCacheKeyForBucketSlice(bucketSliceIndex);
    long counter = cacheService.incr(key, numTokens, numTokens, expiryTime);

    return counter <= bucketSliceSize;
  }

  /**
   * Obtain a cache key for a bucket slice
   * 
   * @param bucketSliceIndex The index of the bucket slice we wish to obtain the cache key for
   * @return The cache key for this bucket slice
   */
  private String getCacheKeyForBucketSlice(int bucketSliceIndex) {
    return uniqueCacheKeyPrefix + bucketSliceIndex;
  }
}
