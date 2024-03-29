[![Maven Central](https://img.shields.io/maven-central/v/com.echobox/ebx-distributedratelimiters-sdk.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22com.echobox%22%20AND%20a:%22ebx-distributedratelimiters-sdk%22) [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://raw.githubusercontent.com/ebx/ebx-distributedratelimiters-sdk/master/LICENSE) [![Build Status](https://travis-ci.org/ebx/ebx-distributedratelimiters-sdk.svg?branch=dev)](https://travis-ci.org/ebx/ebx-distributedratelimiters-sdk)
# ebx-distributedratelimiters-sdk

This project provides the three core rate limiter/semaphore implementations listed below, each of
 which uses a [CacheService](https://github.com/ebx/ebx-cachebase-sdk) to issue tokens or permits
for processes we wish to subject to rate or concurrency limits.

* [Leaky Token Bucket](https://en.wikipedia.org/wiki/Leaky_bucket) - Used to allow burst rates
but not beyond some configured limit. One the configured limit is past an exception occurs. See
 [here](https://github.com/ebx/ebx-distributedratelimiters-sdk/blob/dev/DistributedRateLimiters/src/main/java/com/echobox/distributedratelimiters/DistributedRateLimiterTokenBucket.java) for
  more information.
 * Time Slice - Used to smooth out the rate at which operations can occur concurrently. If too
 many operations occur concurrently this will block. See 
 [here](https://github.com/ebx/ebx-distributedratelimiters-sdk/blob/dev/DistributedRateLimiters/src/main/java/com/echobox/distributedratelimiters/DistributedRateLimiterTimeSlice.java) for more information.
* [Semaphore](https://en.wikipedia.org/wiki/Semaphore_(programming)) - Only allow X concurrent
 events to occur within the sempahore. Operations block while waiting to enter the sempahore. See 
 [here](https://github.com/ebx/ebx-distributedratelimiters-sdk/blob/dev/DistributedRateLimiters/src/main/java/com/echobox/distributedratelimiters/DistributedSemaphore.java) for more information.

## Getting Started

Please see examples in the [Demo](https://github.com/ebx/ebx-distributedratelimiters-sdk/tree/dev/DistributedRateLimitersDemo/src/main/java/com/echobox/distributedratelimiters/demo) 
project.

**Important Note:** It's not ideal but for the demos to work you must first provide endpoint and
 port information for a redis cluster in _src/main/resources/app.properties_. Feel free to
  recommend a better way 😊

## Installation

For our latest stable release use:

```
<dependency>
  <groupId>com.echobox</groupId>
  <artifactId>ebx-distributedratelimiters-sdk</artifactId>
  <version>1.0.3</version>
</dependency>
```

## Getting in touch

* **[GitHub Issues](https://github.com/ebx/ebx-distributedratelimiters-sdk/issues/new)**: If you have ideas, bugs, 
or problems with our library, just open a new issue.

## Contributing

If you would like to get involved please follow the instructions 
[here](https://github.com/ebx/ebx-distributedratelimiters-sdk/tree/master/CONTRIBUTING.md)

## Releases

We use [semantic versioning](https://semver.org/).

All merges into DEV will automatically get released as a maven central snapshot, which can be easily
included in any downstream dependencies that always desire the latest changes (see above for 
'Most Up To Date' installation).

Each merge into the MASTER branch will automatically get released to Maven central and github 
releases, using the current library version. As such, following every merge to master, the version 
number of the dev branch should be incremented and will represent 'Work In Progress' towards the 
next release. 

Please use a merge (not rebase) commit when merging dev into master to perform the release.

To create a full release to Maven central please follow these steps:
1. Ensure the `CHANGELOG.md` is up to date with all the changes in the release, if not please raise 
a suitable PR into `DEV`. Typically the change log should be updated as we go.
3. Create a PR from `DEV` into `MASTER`. Ensure the version in the `pom.xml` is the 
correct version to be released. Merging this PR into `MASTER` will automatically create the maven 
and github releases. Please note that a release is final, it can not be undone/deleted/overwritten.
5. Once the public release has been successful create a final PR into `DEV` that contains an 
incremented `pom.xml` version to ensure the correct snapshot gets updated on subsequent merges
into `DEV`. This PR should also include:
    * An update to the `README.md` latest stable release version number.
    * A 'Work In Progress' entry for the next anticipated release in `CHANGELOG.md`.