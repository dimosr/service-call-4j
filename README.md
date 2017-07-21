[![Build Status](https://travis-ci.org/dimosr/service-call-4j.svg?branch=master)](https://travis-ci.org/dimosr/service-call-4j)
[![Coverage Status](https://coveralls.io/repos/github/dimosr/service-call-4j/badge.svg?branch=master)](https://coveralls.io/github/dimosr/service-call-4j?branch=master)
[![SonarQube Analysis](https://sonarcloud.io/api/badges/gate?key=com.dimosr%3AServiceCall4j)](https://sonarcloud.io/dashboard/index/com.dimosr%3AServiceCall4j)

## Service-Call-4j

A library for adding resiliency capabilities to your RPCs (Remote Procedure Calls) in a declarative way. The capabilities provided by this library currently are the following:
* Caching
* Monitoring
* Retrying
* Timeout
* Throttling
* Circuit Breaker

### Install
* Maven
```xml
<dependency>
    <groupId>com.github.dimosr</groupId>
    <artifactId>ServiceCall4j</artifactId>
    <version>1.0.0</version>
</dependency>
```
* Gradle
```
compile 'com.github.dimosr:ServiceCall4j:1.0.0'
```

### Getting Started

1. Make sure the call you want to enhance implements the **ServiceCall** interface provided by Service-Call-4j:
```java
public interface ServiceCall<REQUEST, RESPONSE> {
    RESPONSE call(REQUEST request);
}

...

public class MyAdjustedHelloWorldCall implements ServiceCall<String, String> {
	String call(String input) {
		return "Hello " + input;
	}
}
```

2. Use the provided **Builder** to build your enhanced ServiceCall:
```java
ServiceCall<String, String> enhancedHelloWorldCall = new ServiceCallBuilder<>(new MyAdjustedHelloWorldCall())
                .withCircuitBreaker(15, 5, 3, 300)
                .withCache(cache)
                .withMonitoring((i, d) -> System.out.println("Duration: " + d.toMillis()))
                .withTimeouts(Duration.ofMillis(1), TimeUnit.MILLISECONDS, Executors.newFixedThreadPool(10))
                .withThrottling(100)
                .withRetrying(false, 2)
                .build();
```

3. Perform your calls
```java
String response = enhancedHelloWorldCall.call("World");
```

Check the project's Wiki for more documentation about how each capability can be used.

### Build Process

Clean previous build artifacts
```sh
mvn clean
```

Execute unit tests
```sh
mvn test
```

Compile, run unit tests, package into .jar and generate unit-test, surefire reports
```sh
mvn package
```

Deploy in the local maven repo
```
mvn deploy
```

Update version, release in maven Central and reset version to SNAPSHOT
```
mvn versions:set -DnewVersion=<MAJOR>.<MINOR>.<PATCH>
mvn clean deploy -P release
mvn versions:set -DnewVersion=<MAJOR>.<MINOR>.<PATCH>-SNAPSHOT
```


### Contributing

There are many ways you can contribute to the project:
* by opening issues for existing **bugs** in the project
* by adding new **features/capabilities** to the library
* by **benchmarking** some of the capabilities of the library and providing the results!
* by letting us know if you are using the library in production (and **your experience**)

The library operates under a CI/CD model. Every pull-request is automatically built by TravisCI and a set of code coverage and quality tests are being performed, before being approved. So, in order to contribute a bug-fix or a feature:
* make sure that you can build successfully the project, without breaking existing tests
* make sure you have added sufficient unit testing (& be prepared to justify any missing scenario) for new code
* make a pull request and wait for someone to review it, we try to be as responsive as possible :)

A final tip for adding new features to the library:
* The library makes extensive use of the the Decorator design pattern. So, please try to comply with this design decision, by implementing your new capability as a new ServiceCall and then adding it in the Builder.
* Feel free to open an issue for a missing feature, so that we can first have a fruitful discussion about it. This will help both you and us making potential design decisions, making the pull-request review much smoother.