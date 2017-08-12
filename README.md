<img src="https://raw.githubusercontent.com/wiki/dimosr/service-call-4j/images/logo.png" height="240" width="240">

Protect your RPCs with ServiceCall4j

[![Build Status](https://travis-ci.org/dimosr/service-call-4j.svg?branch=master)](https://travis-ci.org/dimosr/service-call-4j)
[![Coverage Status](https://coveralls.io/repos/github/dimosr/service-call-4j/badge.svg?branch=master)](https://coveralls.io/github/dimosr/service-call-4j?branch=master)
[![SonarQube Analysis](https://sonarcloud.io/api/badges/gate?key=com.dimosr%3AServiceCall4j)](https://sonarcloud.io/dashboard/index/com.dimosr%3AServiceCall4j)

## Service-Call-4j

A library for adding resiliency capabilities to your RPCs (Remote Procedure Calls) in a declarative way. The capabilities provided by this library are the following:
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