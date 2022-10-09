package com.wavefront.opentracing;

import com.wavefront.opentracing.reporting.ConsoleReporter;
import com.wavefront.sdk.entities.tracing.SpanLog;
import com.wavefront.sdk.entities.tracing.sampling.ConstantSampler;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.log.Fields;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import io.opentracing.propagation.TextMapAdapter;

import static com.wavefront.opentracing.Utils.buildApplicationTags;
import static com.wavefront.opentracing.common.Constants.DEFAULT_SOURCE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WavefrontTracerTest {
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
    TextMap textMapInjectAdapter = new TextMapAdapter(map);
    tracer.inject(span.context(), Format.Builtin.TEXT_MAP, textMapInjectAdapter);

    TextMap textMapExtractAdapter = new TextMapAdapter(map);
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
    Span builtSpan = tracer.buildSpan("testOp").start();
    tracer.activateSpan(builtSpan);
    Span activeSpan = tracer.activeSpan();
    assertNotNull(activeSpan);
    assertEquals(builtSpan, activeSpan);
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
    assertTrue(span.getTagsAsMap().get("foo1").contains("bar1"));
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

  @Test
  public void testSpanLogs() {
    WavefrontTracer tracer = new WavefrontTracer.Builder(
        new ConsoleReporter(DEFAULT_SOURCE), buildApplicationTags()).build();
    WavefrontSpan span = (WavefrontSpan) tracer.buildSpan("testOp").start();
    long timeStamp1 = System.currentTimeMillis() * 1000;
    long timeStamp2 = timeStamp1 + 10_000;
    String logMessage = "test-log";
    span.log(timeStamp1, logMessage);
    Map<String, String> eventsMap = new HashMap<String, String>() {{
      put("event.name", "foo");
      put("event.kind", "error");
      put("event.metadata", null);
    }};
    span.log(timeStamp2, eventsMap);
    List<SpanLog> spanLogs = span.getSpanLogs();
    assertEquals(2, spanLogs.size());
    assertEquals(timeStamp1, spanLogs.get(0).getTimestamp());
    assertEquals(logMessage, spanLogs.get(0).getFields().get(Fields.EVENT));
    assertEquals(timeStamp2, spanLogs.get(1).getTimestamp());
    assertEquals(3, spanLogs.get(1).getFields().size());
    assertEquals("foo", spanLogs.get(1).getFields().get("event.name"));
    assertEquals("error", spanLogs.get(1).getFields().get("event.kind"));
    assertEquals("", spanLogs.get(1).getFields().get("event.metadata"));
  }
}
