package com.wavefront.opentracing;

import com.wavefront.opentracing.reporting.ConsoleReporter;

import org.junit.Test;

import io.opentracing.Scope;
import io.opentracing.Span;

import static com.wavefront.opentracing.common.Constants.DEFAULT_SOURCE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class WavefrontSpanBuilderTest {

  @Test
  public void testIgnoreActiveSpan() {
    WavefrontTracer tracer = new WavefrontTracer.Builder().
        build(new ConsoleReporter(DEFAULT_SOURCE));
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
    WavefrontTracer tracer = new WavefrontTracer.Builder().
        build(new ConsoleReporter(DEFAULT_SOURCE));
    WavefrontSpan span = (WavefrontSpan) tracer.buildSpan("testOp").
        withTag("key1", "value1").
        withTag("key1", "value2").
        start();

    assertNotNull(span);
    assertNotNull(span.getTagsAsMap());
    assertEquals(1, span.getTagsAsMap().size());
    assertTrue(span.getTagsAsMap().get("key1").contains("value1"));
    assertTrue(span.getTagsAsMap().get("key1").contains("value2"));
  }
}