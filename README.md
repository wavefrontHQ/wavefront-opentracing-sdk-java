# wavefront-opentracing-java-sdk

This library provides open tracing support for Wavefront.

## Usage

### Tracer
```
Tracer tracer = new WavefrontTracer.Builder().build();
```

The builder pattern can be used to customize the reporter:

```
# Proxy reporter
Reporter proxyReporter = new WavefrontTraceReporter.Builder().
  withSource("wavefront-tracing-example").
  buildProxy("proxyHostName", proxyPort);

# Direct reporter
Reporter directReporter = new WavefrontTraceReporter.Builder().
  withSource("wavefront-tracing-example").
  buildDirect("clusterName.wavefront.com", "WAVEFRONT_TOKEN_HERE");

# Console reporter
Reporter consoleReporter = new ConsoleReporter("sourceName");

# Initialize the tracer with a specific reporter
Tracer tracer = new WavefrontTracer.Builder().
  withReporter(proxyReporter).
  build();
```
