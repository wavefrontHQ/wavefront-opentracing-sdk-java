package com.wavefront.opentracing;

import com.wavefront.opentracing.reporting.ConsoleReporter;
import com.wavefront.sdk.common.application.ApplicationTags;

import org.junit.Test;

import io.opentracing.Scope;
import io.opentracing.Span;

import static com.wavefront.opentracing.common.Constants.DEFAULT_SOURCE;
import static org.junit.Assert.assertEquals;
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
}