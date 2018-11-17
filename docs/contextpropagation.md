# Cross Process Context Propagation
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

