package com.wavefront.opentracing;

import com.wavefront.opentracing.reporting.ConsoleReporter;
import com.wavefront.sdk.common.Constants;
import com.wavefront.sdk.common.application.ApplicationTags;
import com.wavefront.sdk.entities.tracing.sampling.ConstantSampler;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.tag.Tags;

import static com.wavefront.opentracing.common.Constants.DEFAULT_SOURCE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WavefrontSpanBuilderTest {

  private ApplicationTags buildApplicationTags() {
    return new ApplicationTags.Builder("myApplication", "myService").build();
  }

  @Test
  public void testIgnoreActiveSpan() {
    WavefrontTracer tracer = new WavefrontTracer.Builder(new ConsoleReporter(DEFAULT_SOURCE),
        buildApplicationTags()).build();
    Span activeSpan = tracer.buildSpan("testOp").start();
    tracer.activateSpan(activeSpan);

    // Span created without invoking ignoreActiveSpan() on SpanBuilder
    Span childSpan = tracer.buildSpan("childOp").start();
    tracer.activateSpan(childSpan);
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
        withTag(Constants.APPLICATION_TAG_KEY, "yourApplication").
        start();

    assertNotNull(span);
    assertNotNull(span.getTagsAsMap());
    // Note: Will emit ApplicationTags along with regular tags.
    assertEquals(5, span.getTagsAsMap().size());
    assertTrue(span.getTagsAsMap().get("key1").contains("value1"));
    assertTrue(span.getTagsAsMap().get("key1").contains("value2"));
    assertTrue(span.getTagsAsMap().get(Constants.SERVICE_TAG_KEY).contains("myService"));
    // Check that application tag was replaced
    assertEquals(1, span.getTagsAsMap().get(Constants.APPLICATION_TAG_KEY).size());
    assertTrue(span.getTagsAsMap().get(Constants.APPLICATION_TAG_KEY).contains("yourApplication"));
    assertEquals("yourApplication", span.getSingleValuedTagValue(Constants.APPLICATION_TAG_KEY));
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

  @Test
  public void testBaggageItems() {
    WavefrontTracer tracer = new WavefrontTracer.Builder(new ConsoleReporter(DEFAULT_SOURCE),
        buildApplicationTags()).build();

    // Create parentCtx with baggage items
    Map<String, String> bag = new HashMap<>();
    bag.put("foo", "bar");
    bag.put("user", "name");
    WavefrontSpanContext parentCtx = new WavefrontSpanContext(UUID.randomUUID(), UUID.randomUUID(),
        bag, Boolean.TRUE);

    WavefrontSpan span = (WavefrontSpan) tracer.buildSpan("testOp").
        asChildOf(parentCtx).
        start();
    assertNotNull(span.getBaggageItem("foo"));
    assertNotNull(span.getBaggageItem("user"));

    // parent and follows
    Map<String, String> items = new HashMap<>();
    items.put("tracker", "id");
    items.put("db.name", "name");
    WavefrontSpanContext follows = new WavefrontSpanContext(UUID.randomUUID(), UUID.randomUUID(),
        items, Boolean.TRUE);

    span = (WavefrontSpan) tracer.buildSpan("testOp").
        asChildOf(parentCtx).
        asChildOf(follows).
        start();
    assertNotNull(span.getBaggageItem("foo"));
    assertNotNull(span.getBaggageItem("user"));
    assertNotNull(span.getBaggageItem("tracker"));
    assertNotNull(span.getBaggageItem("db.name"));

    // validate root span
    span = (WavefrontSpan) tracer.buildSpan("testOp").start();
    assertNotNull(span.context().getBaggage());
    assertTrue(span.context().getBaggage().isEmpty());
  }

  @Test
  public void test128BitSpanId() {
    WavefrontTracer tracer = new WavefrontTracer.Builder(new ConsoleReporter(DEFAULT_SOURCE),
        buildApplicationTags()).build();
    Span activeSpan = tracer.buildSpan("testOp").start();
    tracer.activateSpan(activeSpan);
    final String spanId = activeSpan.context().toSpanId();
    final UUID spanUUID = UUID.fromString(spanId);
    assertNotEquals(0,spanUUID.getMostSignificantBits());
  }

  @Test
  public void test64BitSpanId() {
    WavefrontTracer tracer = new WavefrontTracer.Builder(new ConsoleReporter(DEFAULT_SOURCE),
        buildApplicationTags()).useSpanId128Bit(false).build();
    Span activeSpan = tracer.buildSpan("testOp").start();
    tracer.activateSpan(activeSpan);
    final String spanId = activeSpan.context().toSpanId();
    final UUID spanUUID = UUID.fromString(spanId);
    assertEquals(0,spanUUID.getMostSignificantBits());
  }
}
