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
import com.echobox.distributedratelimiters.DistributedSemaphore;
import com.echobox.shutdown.ShutdownRequestedException;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The type Distributed semaphore demo.
 * @author MarcF
 */
public class DistributedSemaphoreDemo {

  /**
   * A logging instance for this class
   */
  private static Logger logger = LoggerFactory.getLogger(DistributedSemaphoreDemo.class);

  /**
   * A random number generator
   */
  private static final Random rand = new Random();

  /**
   * The running threads
   */
  private static final Set<Integer> runningThreads = ConcurrentHashMap.newKeySet();

  /**
   * The maximum concurrent number of operations
   */
  private static final int MAX_CONCURRENT = 5;
  
  /**
   * A count of the number of operations started during the demo
   */
  private static int numThreadsStarted = 0;

  /**
   * The entry point of application.
   *
   * @param args the input arguments
   * @throws Exception the exception
   */
  public static void main(String[] args) throws Exception {

    DemoBase.initialiseCache("src/main/resources/app.properties");

    CacheService cacheService = DemoBase.getCacheService();
    
     //The limiter we are testing. We initialise with a random string identifier to ensure
     // multiple tests don't overlap with each other
    DistributedSemaphore limiter =
        DistributedSemaphore.create(cacheService, RandomStringUtils.randomAlphabetic(5), 
            MAX_CONCURRENT, 60);
    
    //We will run the test for 120 seconds
    long endTime = cacheService.getUnixTimeSupplier().get() + 120;

    int maxThreads = 0;

    do {
      int threadCount = numRunningThreads();
      maxThreads = Math.max(maxThreads, threadCount);
      if (threadCount > MAX_CONCURRENT) {
        logger.warn("We have " + threadCount + " threads active which is more than allowed.");
      }

      tryStartThread(limiter);
      logger.info("Currently " + numThreadsStarted + " threads have been started with " + maxThreads
          + " concurrent.");

      Thread.sleep(1000);
    } while (cacheService.getUnixTimeSupplier().get() < endTime);
  }

  /**
   * Try to start a new concurrent operation
   * @throws ShutdownRequestedException
   * @throws InterruptedException
   */
  private static void tryStartThread(DistributedSemaphore limiter) 
      throws ShutdownRequestedException, InterruptedException {

    boolean acquired = limiter.tryAcquire(DemoBase.SHUTDOWN_MONITOR, 0);
    if (acquired) {
      //Start a new concurrent operation
      int threadNum = ++numThreadsStarted;
      Thread newThread = new Thread(new WorkerThread(limiter, threadNum));
      runningThreads.add(threadNum);
      newThread.start();
    } else {
      logger.debug("Failed to acquire.");
    }
  }

  /**
   * Get the number of running threads
   * @return The number of active threads
   */
  private static int numRunningThreads() {
    return runningThreads.size();
  }

  /**
   * An example of a concurrent operation that takes a random amount of time to complete
   * @author MarcF
   */
  private static class WorkerThread implements Runnable {

    private final int threadNum;
    private final DistributedSemaphore limiter;

    /**
     * Instantiates a new Worker thread.
     *
     * @param limiter The distributed semaphore 
     * @param threadNum the thread num
     */
    WorkerThread(DistributedSemaphore limiter, int threadNum) {
      this.threadNum = threadNum;
      this.limiter = limiter;
    }

    public void run() {
      try {
        //Sleep for some random time between 1 and 30 seconds and then release
        long sleepTime = (long) (rand.nextDouble() * 30) + 1;
        logger.debug("New thread start and sleeping for " + sleepTime + " secs.");
        Thread.sleep(sleepTime * 1000);
        limiter.release(DemoBase.SHUTDOWN_MONITOR);
        runningThreads.remove(threadNum);
      } catch (Exception e) {
        logger.error("Worker thread failed", e);
      }
    }
  }
}
