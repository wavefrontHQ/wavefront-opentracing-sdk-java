# wavefront-opentracing-sdk-java [![travis build status](https://travis-ci.com/wavefrontHQ/wavefront-opentracing-sdk-java.svg?branch=master)](https://travis-ci.com/wavefrontHQ/wavefront-opentracing-sdk-java)

The Wavefront by VMware OpenTracing SDK for Java is a library that provides open tracing support for Wavefront.

## Maven
If you are using Maven, add the following maven dependency to your pom.xml:
```
<dependency>
  <groupId>com.wavefront</groupId>
  <artifactId>wavefront-opentracing-sdk-java</artifactId>
  <version>$releaseVersion</version>
</dependency>
```

## Set Up a Tracer
[Tracer](https://github.com/opentracing/specification/blob/master/specification.md#tracer) is an OpenTracing [interface](https://github.com/opentracing/opentracing-java#initialization) for creating spans and propagating them across arbitrary transports.

This SDK provides a `WavefrontTracer` for creating spans and sending them to Wavefront. The steps for creating a `WavefrontTracer` are:
1. Create an `ApplicationTags` instance, which specifies metadata about your application.
2. Create a `WavefrontSender` for sending data to Wavefront.
3. Create a `WavefrontSpanReporter` for reporting trace data to Wavefront.
4. Create the `WavefrontTracer` instance.

For the details of each step, see the sections below.

### 1. Set Up Application Tags

Application tags determine the metadata (span tags) that are included with every span reported to Wavefront. These tags enable you to filter and query trace data in Wavefront.

You encapsulate application tags in an `ApplicationTags` object.
See [Instantiating ApplicationTags](https://github.com/wavefrontHQ/wavefront-sdk-java/blob/master/docs/apptags.md) for details.

### 2. Set Up a WavefrontSender

A `WavefrontSender` object implements the low-level interface for sending data to Wavefront. You can choose to send data to Wavefront using either the [Wavefront proxy](https://docs.wavefront.com/proxies.html) or [direct ingestion](https://docs.wavefront.com/direct_ingestion.html).

* See [Set Up a WavefrontSender](https://github.com/wavefrontHQ/wavefront-sdk-java/blob/master/README.md#set-up-a-wavefrontsender) for details on instantiating a proxy or direct ingestion client.

**Note:** If you are using multiple Wavefront Java SDKs, see [Sharing a WavefrontSender](https://github.com/wavefrontHQ/wavefront-sdk-java/blob/master/docs/sender.md) for information about sharing a single `WavefrontSender` instance across SDKs.


### 3. Reporter
You must create a `WavefrontSpanReporter` to report trace data to Wavefront. You can optionally create a `CompositeReporter` to send data to Wavefront and to print to the console.

#### Create a WavefrontSpanReporter
To build a `WavefrontSpanReporter`, you must specify a `WavefrontSender` and optionally specify a source for the reported spans. If you omit the source, the host name is automatically used.

To create a `WavefrontSpanReporter`:

```java
// Create a WavefrontProxyClient or WavefrontDirectIngestionClient
WavefrontSender sender = buildWavefrontSender(); // pseudocode; see above

Reporter wfSpanReporter = new WavefrontSpanReporter.Builder().
  withSource("wavefront-tracing-example"). // optional nondefault source name
  build(sender);

//  To get the number of failures observed while reporting
int totalFailures = wfSpanReporter.getFailureCount();
```
**Note:** After you initialize the `WavefrontTracer` with the `WavefrontSpanReporter` (below), completed spans will automatically be reported to Wavefront.
You do not need to start the reporter explicitly.


#### Create a CompositeReporter (Optional)

A `CompositeReporter` enables you to chain a `WavefrontSpanReporter` to another reporter, such as a `ConsoleReporter`. A console reporter is useful for debugging.

```java
// Create a console reporter that reports span to stdout
Reporter consoleReporter = new ConsoleReporter("sourceName"); // Specify the same source you used for the WavefrontSpanReporter

// Instantiate a composite reporter composed of a console reporter and a WavefrontSpanReporter
Reporter compositeReporter = new CompositeReporter(wfSpanReporter, consoleReporter);

```

### 4. Create a WavefrontTracer
To create a `WavefrontTracer`, you pass the `ApplicationTags` and `Reporter` instances you created above to a Builder:

```java
ApplicationTags appTags = buildTags(); // pseudocode; see above
Reporter wfSpanReporter = buildReporter();  // pseudocode; see above
WavefrontTracer.Builder builder = new WavefrontTracer.Builder(wfSpanReporter, appTags);
// Optionally add multi-valued span tags before building
Tracer tracer = builder.build();
```

#### Multi-valued Span Tags (Optional)
You can optionally add metadata to OpenTracing spans in the form of multi-valued tags. The `WavefrontTracer` builder supports different methods to add those tags.

```java
// Construct WavefrontTracer.Builder instance
WavefrontTracer.Builder builder = new WavefrontTracer.Builder(...);

// Add individual tag key value
builder.withGlobalTag("env", "Staging");

// Add a map of tags
builder.withGlobalTags(new HashMap<String, String>() {{ put("severity", "sev-1"); }});

// Add a map of multivalued tags since Wavefront supports repeated tags
builder.withGlobalMultiValuedTags(new HashMap<String, Collection<String>>() {{
     put("location", Arrays.asList("SF", "NY", "LA")); }});

// Construct Wavefront opentracing Tracer
Tracer tracer = builder.build();
```

#### Close the Tracer
Always close the tracer before exiting your application to flush all buffered spans to Wavefront.
```java
tracer.close();
```

## Cross Process Context Propagation
The `Tracer` provides `inject` and `extract` methods that can be used to propagate span contexts across process boundaries. This is useful to propagate childOf or followsFrom relationship between spans across process or host boundaries.

Inject a span context (of the current span) when making an external call such as a HTTP invocation:
```java
TextMap carrier = new TextMapInjectAdapter(new HashMap<>());
tracer.inject(currentSpan.context(), Format.Builtin.HTTP_HEADERS, carrier);

// loop over the injected text map and set its contents on the HTTP request header...
```

Extract the propagated span context on receiving a HTTP request:
```java
TextMap carrier = new TextMapExtractAdapter(new HashMap<>());
SpanContext ctx = tracer.extract(Format.Builtin.HTTP_HEADERS, carrier);
Span receivingSpan = tracer.buildSpan("httpRequestOperationName").asChildOf(ctx).startActive(true);
```
