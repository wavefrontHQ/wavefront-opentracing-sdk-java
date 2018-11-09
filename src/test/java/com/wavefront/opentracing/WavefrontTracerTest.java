package com.wavefront.opentracing;

import com.wavefront.opentracing.reporting.ConsoleReporter;
import com.wavefront.sdk.entities.tracing.sampling.ConstantSampler;
import com.wavefront.sdk.common.application.ApplicationTags;

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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class WavefrontTracerTest {

  private ApplicationTags buildApplicationTags() {
    return new ApplicationTags.Builder("myApplication", "myService").build();
  }

  @Test
  public void testInjectExtract() {
    WavefrontTracer tracer = new WavefrontTracer.Builder(new ConsoleReporter(DEFAULT_SOURCE),
        buildApplicationTags()).
        withSampler(new ConstantSampler(true)).
        build();

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
    assertTrue(ctx.isSampled());
    assertTrue(ctx.getSamplingDecision());
  }

  @Test
  public void testSampling() {
    WavefrontTracer tracer = new WavefrontTracer.Builder(new ConsoleReporter(DEFAULT_SOURCE),
        buildApplicationTags()).
        withSampler(new ConstantSampler(true)).
        build();
    assertTrue(tracer.sample("testOp", 1L, 0));

    tracer = new WavefrontTracer.Builder(new ConsoleReporter(DEFAULT_SOURCE),
        buildApplicationTags()).
        withSampler(new ConstantSampler(false)).
        build();
    assertFalse(tracer.sample("testOp", 1L, 0));
  }

  @Test
  public void testActiveSpan() {
    WavefrontTracer tracer = new WavefrontTracer.Builder(
        new ConsoleReporter(DEFAULT_SOURCE), buildApplicationTags()).build();
    Scope scope = tracer.buildSpan("testOp").startActive(true);
    Span span = tracer.activeSpan();
    assertNotNull(span);
    assertEquals(span, scope.span());
  }

  @Test
  public void testGlobalTags() {
    WavefrontTracer tracer = new WavefrontTracer.Builder(new ConsoleReporter(DEFAULT_SOURCE),
        buildApplicationTags()).withGlobalTag("foo", "bar").build();
    WavefrontSpan span = (WavefrontSpan) tracer.buildSpan("testOp").start();
    assertNotNull(span);
    assertNotNull(span.getTagsAsMap());
    // Note: Will emit ApplicationTags along with regular tags.
    assertEquals(5, span.getTagsAsMap().size());
    assertTrue(span.getTagsAsMap().get("foo").contains("bar"));

    Map<String, String> tags = new HashMap<>();
    tags.put("foo1", "bar1");
    tags.put("foo2", "bar2");
    tracer = new WavefrontTracer.Builder(new ConsoleReporter(DEFAULT_SOURCE),
        buildApplicationTags()).withGlobalTags(tags).build();
    span = (WavefrontSpan) tracer.buildSpan("testOp").
        withTag("foo3", "bar3").
        start();
    assertNotNull(span);
    assertNotNull(span.getTagsAsMap());
    // Note: Will emit ApplicationTags along with regular tags.
    assertEquals(7, span.getTagsAsMap().size());
    assertTrue("bar1", span.getTagsAsMap().get("foo1").contains("bar1"));
    assertTrue(span.getTagsAsMap().get("foo2").contains("bar2"));
    assertTrue(span.getTagsAsMap().get("foo3").contains("bar3"));
  }

  @Test
  public void testGlobalMultiValuedTags() {
    WavefrontTracer tracer = new WavefrontTracer.Builder(
        new ConsoleReporter(DEFAULT_SOURCE), buildApplicationTags()).
        withGlobalTag("key1", "value1").withGlobalTag("key1", "value2").build();
    WavefrontSpan span = (WavefrontSpan) tracer.buildSpan("testOp").start();
    assertNotNull(span);
    assertNotNull(span.getTagsAsMap());
    // Note: Will emit ApplicationTags along with regular tags.
    assertEquals(5, span.getTagsAsMap().size());
    assertTrue(span.getTagsAsMap().get("key1").contains("value1"));
    assertTrue(span.getTagsAsMap().get("key1").contains("value2"));
  }
}
