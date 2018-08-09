# wavefront-opentracing-java-sdk

This library provides open tracing support for Wavefront.

## Usage

### Tracer
```
Tracer tracer = new WavefrontTracer.Builder().build();
```
The builder pattern can be used to customize the reporter.

```
# Proxy reporter
Reporter proxyReporter = new WavefrontProxyReporter.Builder().
  withSource("wavefront-tracing-example").
  build("proxyHostName", proxyTracingPort);

# Direct reporter
Reporter directReporter = new WavefrontDirectReporter.Builder().
  withSource("wavefront-tracing-example").
  buildDirect("clusterName.wavefront.com", "WAVEFRONT_API_TOKEN");

# Composite reporter
Reporter consoleReporter = new ConsoleReporter("sourceName");
Reporter compositeReporter = new CompositeReporter(directReporter, consoleReporter);

# Initialize the tracer with a specific reporter
Tracer tracer = new WavefrontTracer.Builder().
  withReporter(directReporter).
  build();
```
