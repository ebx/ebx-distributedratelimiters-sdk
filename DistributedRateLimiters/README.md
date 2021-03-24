# Echobox Rate Limiters #

Simple RateLimiter and Semaphore implementations for a distributed environment

This project provides the three rate limiter/semaphore implementations listed below, each of which use a distributed [CacheService](/Base/src/main/java/com/echobox/cache/CacheService.java) to issue tokens or permits for processes
we wish to subject to rate or concurrency limits.

* [DistributedRateLimiterTokenBucket](/DistributedRateLimiters/src/main/java/com/echobox/distributedratelimiters/DistributedRateLimiterTokenBucket.java)
* [DistributedRateLimiterTimeSlice](/DistributedRateLimiters/src/main/java/com/echobox/distributedratelimiters/DistributedRateLimiterTimeSlice.java) 
* [DistributedSemaphore](/DistributedRateLimiters/src/main/java/com/echobox/distributedratelimiters/DistributedSemaphore.java) 

We provide a [DefaultMemcachedCacheService](/Base/src/main/java/com/echobox/cache/impl/DefaultMemcachedCacheService.java) implementation as part of the Base project which applications using Memcached as the distributed cache may find useful, although
any implementation of CacheService can be wired into these components.

## DistributedRateLimiterTokenBucket ##

A rate limiter which can be used in a distributed environment to control the rate of a process by issuing permits obtained from a fixed size leaky bucket.

The algorithm used is form of Leaky Bucket Algorithm, where *permitsPerTimeInterval* tokens are "leaked" from a bucket of total capacity specified by *bucketSize* every *timeIntervalSeconds* - permits are able to acquired if the bucket can accommodate a new token.

The permitted rate's burstiness is controlled by the *bucketSize* - the average rate is controlled by the *permitsPerTimeInterval* and *timeIntervalSeconds* parameters.

 ( eg. Average rate : 5 permits per second, 3 permits every 10 seconds etc, 1 permit every 30 seconds)

### Create the DistributedRateLimiterTokenBucket: ###

``` 
   DefaultMemcachedCacheService.initialise(...,...)

   // Use Memcached as our CacheService, but any implementation of CacheService can be used
   CacheService cacheService = DefaultMemcachedCacheService.getInstance();

   // Create the rate limiter, using *uniqueCacheKeyPrefixForThisLimiter* as a unique identifier which can be used to access the rate limiter from different machines.
   DistributedRateLimiterTokenBucket distributedRateLimiter = DistributedRateLimiterTokenBucket
        .create(cacheService, uniqueCacheKeyPrefixForThisLimiter, bucketSize, permitsPerTimeInterval, 
            timeIntervalSeconds);
``` 

### To obtain a permit from the rate limiter, returning immediately: ###
 
``` 
         boolean acquired = distributedRateLimiter.tryAcquire();
``` 

### To obtain a permit from the rate limiter, waiting up to a specified amount of time.  ###


``` 
	boolean acquired = distributedRateLimiter.tryAcquire(shutdownMonitor, maximumNumberOfSecondsToWait);
``` 

( We provide a ShutdownMonitor to allow application shutdown requests to allow for early termination of the wait on shutdown )

## DistributedRateLimiterTimeSlice ##

A rate limiter which can be used in a distributed environment to control the rate of a process by issuing a specified number of permits spread equally over a specified time interval

The average rate is controlled by the *permitsPerTimeInterval* and *timeIntervalSeconds* parameters.

 ( eg. Average rate : 5 permits per second, 3 permits every 10 seconds etc, 1 permit every 30 seconds)

### Create the DistributedRateLimiterTimeSlice: ###

   DefaultMemcachedCacheService.initialise(...,...)

   // Use Memcached as our CacheService, but any implementation of CacheService can be used
   CacheService cacheService = DefaultMemcachedCacheService.getInstance();

   // Create the rate limiter, using *uniqueCacheKeyPrefixForThisLimiter* as a unique identifier which can be used to access the rate limiter from different machines.
   DistributedRateLimiterTimeSlice distributedRateLimiter = DistributedRateLimiterTimeSlice
        .create(cacheService, uniqueCacheKeyPrefixForThisLimiter, permitsPerTimeInterval, 
            timeIntervalSeconds);


### To obtain a permit from the rate limiter, returning immediately: ###

``` 
         boolean acquired = distributedRateLimiter.tryAcquire();
```

### To obtain a permit from the rate limiter, waiting up to a specified amount of time. ###


```
	boolean acquired = distributedRateLimiter.tryAcquire(shutdownMonitor, maximumNumberOfSecondsToWait);
```

( We provide a ShutdownMonitor to allow application shutdown requests to allow for early termination of the wait on shutdown )


## DistributedSemaphore ##

A semaphore which can be used in a distributed environment to control the number of concurrent permits issued to a process (eg. 10 concurrent requests )

Processes requiring permits are responsible for not only aquiring the permits, but also for releasing those permits once they are finished with the permit.  

A common pattern to follow is to release the permit within a finally block surrounding the unit of work requiring the permit, so that a permit would be released at the end of the work
irrespective of whether the work completed successfully or unsuccessfully.

### Create the DistributedSemaphore: ###

```
   DefaultMemcachedCacheService.initialise(...,...)

   // Use Memcached as our CacheService, but any implementation of CacheService can be used
   CacheService cacheService = DefaultMemcachedCacheService.getInstance();

   // Create the semaphore, using *uniqueCacheKeyPrefixForThisLimiter* as a unique identifier which can be used to access the rate limiter from different machines.
   // We specify the maximum number of concurrent permits desired, and a maximum number of seconds a permit will exist for before becoming available again.  This is a protection
   // againts "lost" permits which could occur if there was an issue releasing

    DistributedSemaphore distributedSemaphore =
        DistributedSemaphore.create(cacheService, uniqueCacheKeyPrefixForThisLimiter, maxConcurrentPermits,  maxPermitLifetimeSeconds);
```  

### To obtain a permit from the semaphore, returning immediately: ###
 
```
         boolean acquired = distributedSemaphore.tryAcquire();
```

### To obtain a permit from the semaphore, waiting up to a specified amount of time. ###


```
	 boolean acquired = distributedSemaphore.tryAcquire(shutdownMonitor, maximumNumberOfSecondsToWait);
```

( We provide a ShutdownMonitor to allow application shutdown requests to allow for early termination of the wait on shutdown )

### Remember to release the permit once it is no longer required: ###

```
	distributedSemaphore.release(shutdownMonitor);  
```


