# wavefront-opentracing-java-sdk

This Java library provides open tracing support for Wavefront.

## Usage

### Maven
```
<dependency>
    <groupId>com.wavefront</groupId>
    <artifactId>opentracing-java</artifactId>
    <version>$releaseVersion</version>
</dependency>
```

### Tracer
OpenTracing Tracer is a simple, thin interface for Span creation and propagation across arbitrary transports.

#### How to instantiate a Wavefront tracer?
```java
Tracer tracer = new WavefrontTracer.Builder().withReporter(reporter).build();
```

#### Close the tracer
Before exiting your application, don't forget to close the tracer which will flush all the buffered spans to Wavefront.
```java
tracer.close();
```

When you instantiate the tracer, the builder pattern can be used to customize the reporter as shown below.

### WavefrontSender
Before we instantiate the Wavefront opentracing reporter, we need to instantiate a WavefrontSender 
(i.e. either WavefrontProxyClient or WavefrontDirectIngestionClient)
Refer to this page (https://github.com/wavefrontHQ/wavefront-java-sdk/blob/master/README.md)
to instantiate WavefrontProxyClient or WavefrontDirectIngestionClient.

### Option 1 - Proxy reporter using proxy WavefrontSender
```java
/* Report opentracing spans to Wavefront via a Wavefront Proxy */
Reporter proxyReporter = new WavefrontOpenTracingReporter.Builder().
  withSource("wavefront-tracing-example").
  build(proxyWavefrontSender);

/* Construct Wavefront opentracing Tracer using proxy reporter */
Tracer tracer = new WavefrontTracer.Builder().withReporter(proxyReporter).build();  

/*  To get failures observed while reporting */
int totalFailures = proxyReporter.getFailureCount();
```

### Option 2 - Direct reporter using direct ingestion WavefrontSender
```java
/* Report opentracing spans to Wavefront via Direct Ingestion */
Reporter directReporter = new WavefrontOpenTracingReporter.Builder().
  withSource("wavefront-tracing-example").
  build(directWavefrontSender);

/* Construct Wavefront opentracing Tracer using direct ingestion reporter */
Tracer tracer = new WavefrontTracer.Builder().withReporter(directReporter).build();

/* To get failures observed while reporting */
int totalFailures = directReporter.getFailureCount();
```

### Composite reporter (chaining multiple reporters)
```java
/* Creates a console reporter that reports span to stdout (useful for debugging) */
Reporter consoleReporter = new ConsoleReporter("sourceName");

/* Instantiate a composite reporter composed of console and direct reporter */
Reporter compositeReporter = new CompositeReporter(directReporter, consoleReporter);

/* Construct Wavefront opentracing Tracer composed of console and direct reporter */
Tracer tracer = new WavefrontTracer.Builder().withReporter(compositeReporter).build();
```

