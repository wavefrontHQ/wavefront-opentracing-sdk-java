# wavefront-opentracing-sdk-java [![travis build status](https://travis-ci.com/wavefrontHQ/wavefront-opentracing-sdk-java.svg?branch=master)](https://travis-ci.com/wavefrontHQ/wavefront-opentracing-sdk-java)

This Java library provides open tracing support for Wavefront.

## Maven
If you are using Maven, add the following maven dependency to your pom.xml:
```
<dependency>
  <groupId>com.wavefront</groupId>
  <artifactId>wavefront-opentracing-sdk-java</artifactId>
  <version>$releaseVersion</version>
</dependency>
```

## Tracer
OpenTracing Tracer is an interface for Span creation and propagation across arbitrary transports.

### Create a WavefrontTracer
To create a `WavefrontTracer`, create a `Reporter` instance (see section below) and pass it to the builder:

```java
Reporter reporter = buildReporter(); // see below
Tracer tracer = new WavefrontTracer.Builder().build(reporter);
```

### Close the tracer
Always close the tracer before exiting your application to flush all buffered spans to Wavefront.
```java
tracer.close();
```

## Reporter
Create a WavefrontSpanReporter to send data to Wavefront or a CompositeReporter to send data to Wavefront and print to console.

### WavefrontSpanReporter
The `WavefrontSpanReporter` can send data to Wavefront using either the [proxy](https://docs.wavefront.com/proxies.html) or [direct ingestion](https://docs.wavefront.com/direct_ingestion.html).

To create a `WavefrontSpanReporter`, you first create a `WavefrontSender`. There are two implementations of the Wavefront sender:
* WavefrontProxyClient
* WavefrontDirectIngestionClient

See the [Wavefront sender documentation](https://github.com/wavefrontHQ/wavefront-sdk-java/blob/master/README.md#wavefrontsender) for details on instantiating a proxy or direct ingestion client.

Once you have a Wavefront sender, create the WavefrontReporter as follows:

```java
// Create WavefrontProxyClient or WavefrontDirectIngestionClient
WavefrontSender sender = buildWavefrontSender();

Reporter wavefrontReporter = new WavefrontSpanReporter.Builder().
  withSource("wavefront-tracing-example"). // change the source to a relevant name
  build(sender);

/* Construct Wavefront opentracing Tracer using wavefront reporter */
Tracer tracer = new WavefrontTracer.Builder().build(wavefrontReporter);

/*  To get failures observed while reporting */
int totalFailures = wavefrontReporter.getFailureCount();
```
There is no need to start the Reporter. Once the tracer has been initialized with the Reporter, completed spans will be reported to Wavefront.

**Note:** If you are using more than one Wavefront SDK (i.e. wavefront-opentracing-sdk-java, wavefront-dropwizard-metrics-sdk-java etc.) that requires you to instantiate WavefrontSender, then you should instantiate the WavefrontSender only once and share that sender instance across multiple SDKs inside the same JVM. If the SDKs will be installed on different JVMs, then you would need to instantiate one WavefrontSender per JVM.

### CompositeReporter (chaining multiple reporters)
```java
/* Creates a console reporter that reports span to stdout (useful for debugging) */
Reporter consoleReporter = new ConsoleReporter("sourceName");

/* Instantiate a composite reporter composed of console and Wavefront reporter */
Reporter compositeReporter = new CompositeReporter(wavefrontReporter, consoleReporter);

/* Construct Wavefront opentracing Tracer composed of composite reporter */
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
