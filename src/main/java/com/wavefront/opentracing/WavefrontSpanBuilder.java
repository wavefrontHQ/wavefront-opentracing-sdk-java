package com.wavefront.opentracing;

import com.wavefront.sdk.common.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import io.opentracing.References;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;

/**
 * The class used for building {@link WavefrontSpan}s in accordance with the OpenTracing spec:
 *
 * https://github.com/opentracing/specification/blob/master/specification.md
 *
 * @author Vikram Raman (vikram@wavefront.com)
 */
@NotThreadSafe
public class WavefrontSpanBuilder implements Tracer.SpanBuilder {

  /** The tracer to report spans to. */
  private final WavefrontTracer tracer;

  /** The operation name. Required for every span per opentracing spec. */
  private final String operationName;

  /** The list of parent references. */
  private List<Reference> parents = null;

  /** The list of follows from references. */
  private List<Reference> follows = null;

  private long startTimeMicros;
  private boolean ignoreActiveSpan = false;
  private final List<Pair<String, String>> tags = new ArrayList<>();

  public WavefrontSpanBuilder(String operationName, WavefrontTracer tracer) {
    this.operationName = operationName;
    this.tracer = tracer;
  }

  @Override
  public Tracer.SpanBuilder asChildOf(SpanContext parentContext) {
    return addReference(References.CHILD_OF, parentContext);
  }

  @Override
  public Tracer.SpanBuilder asChildOf(Span parent) {
    return addReference(References.CHILD_OF, parent == null ? null : parent.context());
  }

  @Override
  public Tracer.SpanBuilder addReference(String type, SpanContext spanContext) {
    if (!(spanContext instanceof WavefrontSpanContext) ||
        (!References.CHILD_OF.equals(type) && !References.FOLLOWS_FROM.equals(type))) {
      return this;
    }
    Reference ref = new Reference((WavefrontSpanContext) spanContext, type);
    if (References.CHILD_OF.equals(type)) {
      if (parents == null) {
        parents = new ArrayList<>(1);
      }
      parents.add(ref);
    } else if (References.FOLLOWS_FROM.equals(type)) {
      if (follows == null) {
        follows = new ArrayList<>(1);
      }
      follows.add(ref);
    }
    return this;
  }

  @Override
  public Tracer.SpanBuilder ignoreActiveSpan() {
    this.ignoreActiveSpan = true;
    return this;
  }

  @Override
  public Tracer.SpanBuilder withTag(String key, String value) {
    return setTagObject(key, value);
  }

  @Override
  public Tracer.SpanBuilder withTag(String key, boolean value) {
    return setTagObject(key, value);
  }

  @Override
  public Tracer.SpanBuilder withTag(String key, Number value) {
    return setTagObject(key, value);
  }

  private Tracer.SpanBuilder setTagObject(String key, Object value) {
    if (key != null && !key.isEmpty() && value != null) {
      tags.add(Pair.of(key, value.toString()));
    }
    return this;
  }

  @Override
  public Tracer.SpanBuilder withStartTimestamp(long startMicros) {
    this.startTimeMicros = startMicros;
    return this;
  }

  @Override
  public Scope startActive(boolean finishSpanOnClose) {
    return tracer.scopeManager().activate(start(), finishSpanOnClose);
  }

  @Override
  public Span startManual() {
    return start();
  }

  @Override
  public Span start() {
    long startTimeNanos = 0;
    if (startTimeMicros == 0) {
      startTimeMicros = tracer.currentTimeMicros();
      startTimeNanos = System.nanoTime();
    }
    List<Pair<String, String>> globalTags = tracer.getTags();
    if (globalTags != null && !globalTags.isEmpty()) {
      tags.addAll(globalTags);
    }
    WavefrontSpanContext ctx = createSpanContext();
    if (!ctx.isSampled()) {
      // this indicates a root span and that no decision has been inherited from a parent span.
      // perform head based sampling as no sampling decision has been obtained for this span yet.
      boolean decision = tracer.sample(operationName, ctx.getTraceId().getLeastSignificantBits(), 0);
      ctx = ctx.withSamplingDecision(decision);
    }
    WavefrontSpan wavefrontSpan = new WavefrontSpan(tracer, operationName, ctx, startTimeMicros,
        startTimeNanos, parents, follows, tags);
    tracer.startSpan(wavefrontSpan);
    return wavefrontSpan;
  }

  private WavefrontSpanContext createSpanContext() {
    UUID spanId = UUID.randomUUID();
    WavefrontSpanContext traceCtx = traceAncestry();
    UUID traceId = (traceCtx == null) ? UUID.randomUUID() : traceCtx.getTraceId();
    Boolean sampling = (traceCtx == null) ? null : traceCtx.getSamplingDecision();
    return new WavefrontSpanContext(traceId, spanId, null, sampling);
  }

  @Nullable
  private WavefrontSpanContext traceAncestry() {
    if (parents != null && !parents.isEmpty()) {
      // prefer child_of relationship for assigning traceId
      return parents.get(0).getSpanContext();
    }
    if (follows != null && !follows.isEmpty()) {
      return follows.get(0).getSpanContext();
    }

    // use active span as parent if ignoreActiveSpan is false
    Span parentSpan = !ignoreActiveSpan ? tracer.activeSpan() : null;
    if (parentSpan != null) {
      asChildOf(parentSpan);
    }

    // root span if parentSpan is null
    return parentSpan == null ? null : ((WavefrontSpanContext) parentSpan.context());
  }
}
