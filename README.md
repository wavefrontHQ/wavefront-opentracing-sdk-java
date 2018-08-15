# wavefront-opentracing-java-sdk

This library provides open tracing support for Wavefront.

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
```
Tracer tracer = new WavefrontTracer.Builder().withReporter(reporter).build();
```

The builder pattern can be used to customize the reporter as shown below.

### Proxy reporter
```
// Report opentracing spans to Wavefront via a Wavefront Proxy
Reporter proxyReporter = new WavefrontProxyReporter.
  Builder("proxyHostName", proxyTracingPort).
  withSource("wavefront-tracing-example").
  flushIntervalSeconds(10). // only required to override default value of 5 seconds
  build();

Tracer tracer = new WavefrontTracer.Builder().withReporter(proxyReporter).build();  

// To get failures observed while reporting
int totalFailures = proxyReporter.getFailureCount();
```

### Direct reporter
```
// Report opentracing spans to Wavefront via Direct Ingestion
Reporter directReporter = new WavefrontDirectReporter.
  Builder("clusterName.wavefront.com", "WAVEFRONT_API_TOKEN").
  withSource("wavefront-tracing-example").
  flushIntervalSeconds(10). // only required to override the default value of 1 second
  maxQueueSize(100_000). // only required to override the default value of 50,000
  build();

Tracer tracer = new WavefrontTracer.Builder().withReporter(directReporter).build();

// To get failures observed while reporting
int totalFailures = directReporter.getFailureCount();
```

### Composite reporter (chaining multiple reporters)
```
Reporter consoleReporter = new ConsoleReporter("sourceName");
Reporter compositeReporter = new CompositeReporter(directReporter, consoleReporter);

Tracer tracer = new WavefrontTracer.Builder().withReporter(compositeReporter).build();
```
