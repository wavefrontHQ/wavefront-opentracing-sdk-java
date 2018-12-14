package com.wavefront.opentracing;

import com.wavefront.opentracing.reporting.ConsoleReporter;
import com.wavefront.sdk.entities.tracing.sampling.ConstantSampler;
import com.wavefront.sdk.common.application.ApplicationTags;

import org.junit.Test;

import java.util.UUID;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.tag.Tags;

import static com.wavefront.opentracing.common.Constants.DEFAULT_SOURCE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class WavefrontSpanBuilderTest {

  private ApplicationTags buildApplicationTags() {
    return new ApplicationTags.Builder("myApplication", "myService").build();
  }

  @Test
  public void testIgnoreActiveSpan() {
    WavefrontTracer tracer = new WavefrontTracer.Builder(new ConsoleReporter(DEFAULT_SOURCE),
        buildApplicationTags()).build();
    Scope scope = tracer.buildSpan("testOp").startActive(true);
    Span activeSpan = scope.span();

    // Span created without invoking ignoreActiveSpan() on SpanBuilder
    Span childSpan = tracer.buildSpan("childOp").start();
    String activeTraceId = ((WavefrontSpanContext) activeSpan.context()).getTraceId().toString();
    String childTraceId = ((WavefrontSpanContext) childSpan.context()).getTraceId().toString();
    assertEquals(activeTraceId, childTraceId);

    // Span created with ignoreActiveSpan() on SpanBuilder
    childSpan = tracer.buildSpan("childOp").ignoreActiveSpan().start();
    childTraceId = ((WavefrontSpanContext) childSpan.context()).getTraceId().toString();
    assertNotEquals(activeTraceId, childTraceId);
  }

  @Test
  public void testMultiValuedTags() {
    WavefrontTracer tracer = new WavefrontTracer.Builder(new ConsoleReporter(DEFAULT_SOURCE),
        buildApplicationTags()).build();
    WavefrontSpan span = (WavefrontSpan) tracer.buildSpan("testOp").
        withTag("key1", "value1").
        withTag("key1", "value2").
        start();

    assertNotNull(span);
    assertNotNull(span.getTagsAsMap());
    // Note: Will emit ApplicationTags along with regular tags.
    assertEquals(5, span.getTagsAsMap().size());
    assertTrue(span.getTagsAsMap().get("key1").contains("value1"));
    assertTrue(span.getTagsAsMap().get("key1").contains("value2"));
  }

  @Test
  public void testForcedSampling() {
    // Create tracer with constant sampler set to false
    WavefrontTracer tracer = new WavefrontTracer.Builder(new ConsoleReporter(DEFAULT_SOURCE),
        buildApplicationTags()).
        withSampler(new ConstantSampler(false)).
        build();

    WavefrontSpan span = (WavefrontSpan) tracer.buildSpan("testOp").start();
    assertNotNull(span);
    assertNotNull(span.context());
    assertNotNull(span.context().getSamplingDecision());
    assertFalse(span.context().getSamplingDecision());

    Tags.SAMPLING_PRIORITY.set(span, 1);
    assertNotNull(span.context().getSamplingDecision());
    assertTrue(span.context().getSamplingDecision());

    span = (WavefrontSpan) tracer.buildSpan("testOp").start();
    assertNotNull(span);
    assertNotNull(span.context());
    assertNotNull(span.context().getSamplingDecision());
    assertFalse(span.context().getSamplingDecision());

    Tags.ERROR.set(span, Boolean.TRUE);
    assertNotNull(span.context().getSamplingDecision());
    assertTrue(span.context().getSamplingDecision());
  }

  @Test
  public void testRootSampling() {
    // Create tracer with constant sampler set to false
    WavefrontTracer tracer = new WavefrontTracer.Builder(new ConsoleReporter(DEFAULT_SOURCE),
        buildApplicationTags()).
        withSampler(new ConstantSampler(false)).
        build();

    WavefrontSpan span = (WavefrontSpan) tracer.buildSpan("testOp").start();
    assertNotNull(span);
    assertNotNull(span.context());
    assertEquals(0, span.getParents().size());
    assertEquals(0, span.getFollows().size());
    assertTrue(span.context().isSampled());
    assertNotNull(span.context().getSamplingDecision());
    assertFalse(span.context().getSamplingDecision());

    // Create tracer with constant sampler set to true
    tracer = new WavefrontTracer.Builder(new ConsoleReporter(DEFAULT_SOURCE),
        buildApplicationTags()).
        withSampler(new ConstantSampler(true)).
        build();

    span = (WavefrontSpan) tracer.buildSpan("testOp").start();
    assertNotNull(span);
    assertNotNull(span.context());
    assertEquals(0, span.getParents().size());
    assertEquals(0, span.getFollows().size());
    assertTrue(span.context().isSampled());
    assertNotNull(span.context().getSamplingDecision());
    assertTrue(span.context().getSamplingDecision());
  }

  @Test
  public void testPositiveChildSampling() {
    // Create tracer with constant sampler set to false
    WavefrontTracer tracer = new WavefrontTracer.Builder(new ConsoleReporter(DEFAULT_SOURCE),
        buildApplicationTags()).
        withSampler(new ConstantSampler(false)).
        build();

    // Create parentCtx with sampled set to true
    WavefrontSpanContext parentCtx = new WavefrontSpanContext(UUID.randomUUID(), UUID.randomUUID(),
        null, Boolean.TRUE);

    // Verify span created asChildOf parentCtx inherits parent sampling decision
    WavefrontSpan span = (WavefrontSpan) tracer.buildSpan("testOp").
        asChildOf(parentCtx).
        start();

    assertFalse(tracer.sample(span.getOperationName(), span.context().getTraceId().
        getLeastSignificantBits(), 0));
    assertNotNull(span);
    assertEquals(parentCtx.getTraceId().toString(), span.context().getTraceId().toString());
    assertTrue(span.context().isSampled());
    assertNotNull(span.context().getSamplingDecision());
    assertTrue(span.context().getSamplingDecision());
  }

  @Test
  public void testNegativeChildSampling() {
    // Create tracer with constant sampler set to true
    WavefrontTracer tracer = new WavefrontTracer.Builder(new ConsoleReporter(DEFAULT_SOURCE),
        buildApplicationTags()).
        withSampler(new ConstantSampler(true)).
        build();

    // Create parentCtx with sampled set to false
    WavefrontSpanContext parentCtx = new WavefrontSpanContext(UUID.randomUUID(), UUID.randomUUID(),
        null, Boolean.FALSE);

    // Verify span created asChildOf parentCtx inherits parent sampling decision
    WavefrontSpan span = (WavefrontSpan) tracer.buildSpan("testOp").
        asChildOf(parentCtx).
        start();

    assertTrue(tracer.sample(span.getOperationName(), span.context().getTraceId().
        getLeastSignificantBits(), 0));
    assertNotNull(span);
    assertEquals(parentCtx.getTraceId().toString(), span.context().getTraceId().toString());
    assertTrue(span.context().isSampled());
    assertNotNull(span.context().getSamplingDecision());
    assertFalse(span.context().getSamplingDecision());
  }
}