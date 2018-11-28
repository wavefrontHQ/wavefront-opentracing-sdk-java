# Cross-Process Context Propagation

Following the [OpenTracing standard](https://opentracing.io/docs/overview/inject-extract/), you must arrange for your applicaton's `Tracer` to propagate a span context across process boundaries whenever a client microservice sends a request to another microservice. Doing so enables you to represent the client's request as part of a continuing trace that consists of multiple connected spans. 

The `Tracer` provides `inject` and `extract` methods for propagating span contexts across process boundaries. You can use these methods to propagate a `childOf` or `followsFrom` relationship between spans across process or host boundaries.

* In code that makes an external call (such as an HTTP invocation), obtain the current span and its span context, create a carrier, and inject the span context into the carrier:
  
```java
currentSpan = ...  // obtain the current span
TextMap carrier = new TextMapInjectAdapter(new HashMap<>());
tracer.inject(currentSpan.context(), Format.Builtin.HTTP_HEADERS, carrier); 

// loop over the injected text map and set its contents on the HTTP request header...
```

* In code that responds to the call (i.e., that receives the HTTP request), extract the propagated span context:
```java
TextMap carrier = new TextMapExtractAdapter(new HashMap<>());
SpanContext ctx = tracer.extract(Format.Builtin.HTTP_HEADERS, carrier);
Span receivingSpan = tracer.buildSpan("httpRequestOperationName").asChildOf(ctx).startActive(true);
```

