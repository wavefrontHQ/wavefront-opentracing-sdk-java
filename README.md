# wavefront-opentracing-sdk-java [![travis build status](https://travis-ci.com/wavefrontHQ/wavefront-opentracing-sdk-java.svg?branch=master)](https://travis-ci.com/wavefrontHQ/wavefront-opentracing-sdk-java)

This Java library provides open tracing support for Wavefront.

## Usage

### Maven
```
<dependency>
  <groupId>com.wavefront</groupId>
  <artifactId>wavefront-opentracing-sdk-java</artifactId>
  <version>$releaseVersion</version>
</dependency>
```

### Tracer
OpenTracing Tracer is a simple, thin interface for Span creation and propagation across arbitrary transports.

#### How to instantiate a Wavefront tracer?
```java
Tracer tracer = new WavefrontTracer.Builder().build(reporter);
```

#### Close the tracer
Before exiting your application, don't forget to close the tracer which will flush all the buffered spans to Wavefront.
```java
tracer.close();
```

When you instantiate the tracer, the builder pattern can be used to customize the reporter as shown below.

### WavefrontSender
Before we instantiate the Wavefront opentracing span reporter, we need to instantiate a WavefrontSender 
(i.e. either WavefrontProxyClient or WavefrontDirectIngestionClient)
Refer to this page (https://github.com/wavefrontHQ/wavefront-sdk-java/blob/master/README.md#wavefrontsender)
to instantiate WavefrontProxyClient or WavefrontDirectIngestionClient.
<br />
<br />
**Note:** If you are using more than one Wavefront SDK (i.e. wavefront-opentracing-sdk-java, wavefront-dropwizard-metrics-sdk-java, wavefront-jersey-sdk-java, wavefront-grpc-sdk-java etc.) that requires you to instantiate WavefrontSender, then you should instantiate the WavefrontSender only once and share that sender instance across multiple SDKs inside the same JVM.
If the SDKs will be installed on different JVMs, then you would need to instantiate one WavefrontSender per JVM.

### Option 1 - Proxy reporter using proxy WavefrontSender
```java
/* Report opentracing spans to Wavefront via a Wavefront Proxy */
Reporter proxyReporter = new WavefrontSpanReporter.Builder().
  withSource("wavefront-tracing-example").
  build(proxyWavefrontSender);

/* Construct Wavefront opentracing Tracer using proxy reporter */
Tracer tracer = new WavefrontTracer.Builder().build(proxyReporter);

/*  To get failures observed while reporting */
int totalFailures = proxyReporter.getFailureCount();
```

### Option 2 - Direct reporter using direct ingestion WavefrontSender
```java
/* Report opentracing spans to Wavefront via Direct Ingestion */
Reporter directReporter = new WavefrontSpanReporter.Builder().
  withSource("wavefront-tracing-example").
  build(directWavefrontSender);

/* Construct Wavefront opentracing Tracer using direct ingestion reporter */
Tracer tracer = new WavefrontTracer.Builder().build(directReporter);

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
Tracer tracer = new WavefrontTracer.Builder().build(compositeReporter);
```

### Global Span Tags
You can add metadata to opentracing spans using tags. The WavefrontTracer builder supports different methods to add those tags.
```java
/* Construct WavefrontTracer.Builder instance */
WavefrontTracer.Builder builder = new WavefrontTracer.Builder();

/* Add individual tag key value */
builder.withGlobalTag("env", "Staging");

/* Add a map of tags */
builder.withGlobalTags(new HashMap<String, String>() {{ put("severity", "sev-1"); }});

/* Add a map of multivalued tags since Wavefront supports repeated tags */
builder.withGlobalMultiValuedTags(new HashMap<String, Collection<String>>() {{ 
     put("location", Arrays.asList("SF", "NY", "LA")); }});

/* Construct Wavefront opentracing Tracer */
Tracer tracer = builder.build(reporter);
```

