# wavefront-opentracing-java-sdk

This library provides open tracing support for Wavefront.

## Usage
The builder pattern can be used to customize the reporter.

### Proxy reporter
```
Reporter proxyReporter = new WavefrontProxyReporter.
  Builder("proxyHostName", proxyTracingPort).
  withSource("wavefront-tracing-example").
  flushIntervalSeconds(10).    // set this only if you want to override the default value of 5 seconds
  build();
  
/* Report opentracing span to Wavefront */
proxyReporter.report(wavefrontSpan);

/* Get total number of failures observed while reporting */
int totalFailures = proxyReporter.getFailureCount();

/* Will flush in-flight buffer and close connection */
proxyReporter.close();  
```

### Direct reporter
```
Reporter directReporter = new WavefrontDirectReporter.
  Builder("clusterName.wavefront.com", "WAVEFRONT_API_TOKEN").
  withSource("wavefront-tracing-example").
  flushIntervalSeconds(10).    // set this only if you want to override the default value of 1 second
  maxQueueSize(100_000).       // set this only if you want to override the default value of 50,000 
  build();
  
/* Report opentracing span to Wavefront */
directReporter.report(wavefrontSpan);

/* Get total number of failures observed while reporting */
int totalFailures = directReporter.getFailureCount();

/* Will flush in-flight buffer and close connection */
directReporter.close();  
```

### Composite reporter
```
Reporter consoleReporter = new ConsoleReporter("sourceName");
Reporter compositeReporter = new CompositeReporter(directReporter, consoleReporter);
```

### Initialize the tracer with a specific reporter
```
Tracer tracer = new WavefrontTracer.Builder().
  withReporter(directReporter).
  build();
```
