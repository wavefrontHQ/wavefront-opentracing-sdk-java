package com.wavefront.opentracing;

import com.wavefront.opentracing.reporting.ConsoleReporter;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import io.opentracing.propagation.TextMapExtractAdapter;
import io.opentracing.propagation.TextMapInjectAdapter;

import static com.wavefront.opentracing.common.Constants.DEFAULT_SOURCE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class WavefrontTracerTest {

  @Test
  public void testInjectExtract() {
    WavefrontTracer tracer = new WavefrontTracer.Builder().
        build(new ConsoleReporter(DEFAULT_SOURCE));

    Span span = tracer.buildSpan("testOp").start();
    assertNotNull(span);

    span.setBaggageItem("customer", "testCustomer");
    span.setBaggageItem("requestType", "mobile");

    Map<String, String> map = new HashMap<>();
    TextMap textMapInjectAdapter = new TextMapInjectAdapter(map);
    tracer.inject(span.context(), Format.Builtin.TEXT_MAP, textMapInjectAdapter);

    TextMap textMapExtractAdapter = new TextMapExtractAdapter(map);
    WavefrontSpanContext ctx = (WavefrontSpanContext) tracer.extract(Format.Builtin.TEXT_MAP,
        textMapExtractAdapter);

    assertEquals("testCustomer", ctx.getBaggageItem("customer"));
    assertEquals("mobile", ctx.getBaggageItem("requesttype"));
  }

  @Test
  public void testActiveSpan() {
    WavefrontTracer tracer = new WavefrontTracer.Builder().
        build(new ConsoleReporter(DEFAULT_SOURCE));
    Scope scope = tracer.buildSpan("testOp").startActive(true);
    Span span = tracer.activeSpan();
    assertNotNull(span);
    assertEquals(span, scope.span());
  }

  @Test
  public void testGlobalTags() {
    WavefrontTracer tracer = new WavefrontTracer.Builder().
        withGlobalTag("foo", "bar").
        build(new ConsoleReporter(DEFAULT_SOURCE));
    WavefrontSpan span = (WavefrontSpan) tracer.buildSpan("testOp").start();
    assertNotNull(span);
    assertNotNull(span.getTagsAsMap());
    assertEquals(1, span.getTagsAsMap().size());
    assertTrue(span.getTagsAsMap().get("foo").contains("bar"));

    Map<String, String> tags = new HashMap<>();
    tags.put("foo1", "bar1");
    tags.put("foo2", "bar2");
    tracer = new WavefrontTracer.Builder().
        withGlobalTags(tags).
        build(new ConsoleReporter(DEFAULT_SOURCE));
    span = (WavefrontSpan) tracer.buildSpan("testOp").
        withTag("foo3", "bar3").
        start();
    assertNotNull(span);
    assertNotNull(span.getTagsAsMap());
    assertEquals(3, span.getTagsAsMap().size());
    assertTrue("bar1", span.getTagsAsMap().get("foo1").contains("bar1"));
    assertTrue(span.getTagsAsMap().get("foo2").contains("bar2"));
    assertTrue(span.getTagsAsMap().get("foo3").contains("bar3"));
  }

  @Test
  public void testGlobalMultiValuedTags() {
    WavefrontTracer tracer = new WavefrontTracer.Builder().
        withGlobalTag("key1", "value1").
        withGlobalTag("key1", "value2").
        build(new ConsoleReporter(DEFAULT_SOURCE));
    WavefrontSpan span = (WavefrontSpan) tracer.buildSpan("testOp").start();
    assertNotNull(span);
    assertNotNull(span.getTagsAsMap());
    assertEquals(1, span.getTagsAsMap().size());
    assertTrue(span.getTagsAsMap().get("key1").contains("value1"));
    assertTrue(span.getTagsAsMap().get("key1").contains("value2"));
  }
}
