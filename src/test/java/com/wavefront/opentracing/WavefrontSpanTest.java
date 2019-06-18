package com.wavefront.opentracing;

import com.wavefront.opentracing.reporting.WavefrontSpanReporter;
import com.wavefront.sdk.common.WavefrontSender;
import com.wavefront.sdk.entities.histograms.HistogramGranularity;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import io.opentracing.tag.Tags;

import static com.wavefront.opentracing.Utils.buildApplicationTags;
import static com.wavefront.opentracing.common.Constants.DEFAULT_SOURCE;
import static org.easymock.EasyMock.anyLong;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

/**
 * WavefrontSpanTest to test spans, generated metrics and component heartbeat.
 *
 * @author Sushant Dewan (sushant@wavefront.com).
 */
public class WavefrontSpanTest {

  @Test
  public void testValidWavefrontSpan() throws IOException, InterruptedException {
    String operationName = "dummyOp";
    Map<String, String> pointTags = pointTags(operationName, new HashMap<>());
    WavefrontSender wfSender = createMock(WavefrontSender.class);
    wfSender.sendSpan(eq(operationName), anyLong(), anyLong(), eq(DEFAULT_SOURCE),
        anyObject(), anyObject(), eq(Collections.emptyList()), eq(Collections.emptyList()),
        anyObject(), eq(Collections.emptyList()));
    expectLastCall();

    wfSender.sendMetric(eq(
        "tracing.derived.myApplication.myService.dummyOp.invocation.count"),
        eq(1.0), anyLong(), eq(DEFAULT_SOURCE), eq(pointTags));
    expectLastCall().atLeastOnce();

    // TODO - change WavefrontInternalReporter.newWavefrontHistogram to pass in a clock to
    // advance minute bin and change the below call to expectLastCall().atLeastOnce();
    wfSender.sendDistribution(eq(
        "tracing.derived.myApplication.myService.dummyOp.duration.micros"),
        anyObject(), eq(new HashSet<>(Arrays.asList(HistogramGranularity.MINUTE))), anyLong(),
        eq(DEFAULT_SOURCE), eq(pointTags));
    expectLastCall().anyTimes();

    wfSender.sendMetric(eq(
        "tracing.derived.myApplication.myService.dummyOp.total_time.millis.count"),
        anyLong(), anyLong(), eq(DEFAULT_SOURCE), eq(pointTags));
    expectLastCall().atLeastOnce();

    replay(wfSender);
    WavefrontTracer tracer = new WavefrontTracer.Builder(
        new WavefrontSpanReporter.Builder().withSource(DEFAULT_SOURCE).build(wfSender),
        buildApplicationTags()).setReportFrequenceMillis(50).build();
    tracer.buildSpan("dummyOp").startActive(true).close();
    // Sleep for 1 seconds
    System.out.println("Sleeping for 1 second zzzzz .....");
    Thread.sleep(1000);
    System.out.println("Resuming execution .....");
    verify(wfSender);
  }

  @Test
  public void testErrorWavefrontSpan() throws IOException, InterruptedException {
    String operationName = "dummyOp";
    Map<String, String> pointTags = pointTags(operationName, new HashMap<>());
    WavefrontSender wfSender = createMock(WavefrontSender.class);
    wfSender.sendSpan(eq(operationName), anyLong(), anyLong(), eq(DEFAULT_SOURCE),
        anyObject(), anyObject(), eq(Collections.emptyList()), eq(Collections.emptyList()),
        anyObject(), eq(Collections.emptyList()));
    expectLastCall();

    wfSender.sendMetric(eq(
        "tracing.derived.myApplication.myService.dummyOp.invocation.count"),
        eq(1.0), anyLong(), eq(DEFAULT_SOURCE), eq(pointTags));
    expectLastCall().atLeastOnce();

    wfSender.sendMetric(eq(
        "tracing.derived.myApplication.myService.dummyOp.error.count"),
        eq(1.0), anyLong(), eq(DEFAULT_SOURCE), eq(pointTags));
    expectLastCall().atLeastOnce();

    // TODO - change WavefrontInternalReporter.newWavefrontHistogram to pass in a clock to
    // advance minute bin and change the below call to expectLastCall().atLeastOnce();
    wfSender.sendDistribution(eq(
        "tracing.derived.myApplication.myService.dummyOp.duration.micros"),
        anyObject(), eq(new HashSet<>(Arrays.asList(HistogramGranularity.MINUTE))), anyLong(),
        eq(DEFAULT_SOURCE), eq(pointTags));
    expectLastCall().anyTimes();

    wfSender.sendMetric(eq(
        "tracing.derived.myApplication.myService.dummyOp.total_time.millis.count"),
        anyLong(), anyLong(), eq(DEFAULT_SOURCE), eq(pointTags));
    expectLastCall().atLeastOnce();

    replay(wfSender);
    WavefrontTracer tracer = new WavefrontTracer.Builder(
        new WavefrontSpanReporter.Builder().withSource(DEFAULT_SOURCE).build(wfSender),
        buildApplicationTags()).setReportFrequenceMillis(50).build();
    tracer.buildSpan("dummyOp").withTag(Tags.ERROR.getKey(), true).
        startActive(true).close();
    // Sleep for 60+ seconds
    System.out.println("Sleeping for 1 second zzzzz .....");
    Thread.sleep(1000);
    System.out.println("Resuming execution .....");
    verify(wfSender);
  }

  @Test
  public void testCustomRedMetricsTagsWavefrontSpan() throws IOException, InterruptedException {
    String operationName = "dummyOp";
    Map<String, String> pointTags = pointTags(operationName, new HashMap<String, String>() {{
      put("tenant", "tenant1");
      put("env", "Staging");
    }});
    WavefrontSender wfSender = createMock(WavefrontSender.class);
    wfSender.sendSpan(eq(operationName), anyLong(), anyLong(), eq(DEFAULT_SOURCE),
        anyObject(), anyObject(), eq(Collections.emptyList()), eq(Collections.emptyList()),
        anyObject(), eq(null));
    expectLastCall();

    wfSender.sendMetric(eq(
        "tracing.derived.myApplication.myService.dummyOp.invocation.count"),
        eq(1.0), anyLong(), eq(DEFAULT_SOURCE), eq(pointTags));
    expectLastCall().atLeastOnce();

    // TODO - change WavefrontInternalReporter.newWavefrontHistogram to pass in a clock to
    // advance minute bin and change the below call to expectLastCall().atLeastOnce();
    wfSender.sendDistribution(eq(
        "tracing.derived.myApplication.myService.dummyOp.duration.micros"),
        anyObject(), eq(new HashSet<>(Arrays.asList(HistogramGranularity.MINUTE))), anyLong(),
        eq(DEFAULT_SOURCE), eq(pointTags));
    expectLastCall().anyTimes();

    wfSender.sendMetric(eq(
        "tracing.derived.myApplication.myService.dummyOp.total_time.millis.count"),
        anyLong(), anyLong(), eq(DEFAULT_SOURCE), eq(pointTags));
    expectLastCall().atLeastOnce();

    replay(wfSender);
    WavefrontTracer tracer = new WavefrontTracer.Builder(
        new WavefrontSpanReporter.Builder().withSource(DEFAULT_SOURCE).build(wfSender),
        buildApplicationTags()).setReportFrequenceMillis(50).
        redMetricsCustomTagKeys(new HashSet<>(Arrays.asList("tenant", "env"))).
        build();
    tracer.buildSpan("dummyOp").withTag("tenant", "tenant1").
        withTag("env", "Staging").startActive(true).close();
    // Sleep for 1 seconds
    System.out.println("Sleeping for 1 second zzzzz .....");
    Thread.sleep(1000);
    System.out.println("Resuming execution .....");
    verify(wfSender);
  }

  @Test
  public void testNoCustomRedMetricsTagsWavefrontSpan() throws IOException, InterruptedException {
    String operationName = "dummyOp";
    Map<String, String> pointTags = pointTags(operationName, new HashMap<>());
    WavefrontSender wfSender = createMock(WavefrontSender.class);
    wfSender.sendSpan(eq(operationName), anyLong(), anyLong(), eq(DEFAULT_SOURCE),
        anyObject(), anyObject(), eq(Collections.emptyList()), eq(Collections.emptyList()),
        anyObject(), eq(null));
    expectLastCall();

    wfSender.sendMetric(eq(
        "tracing.derived.myApplication.myService.dummyOp.invocation.count"),
        eq(1.0), anyLong(), eq(DEFAULT_SOURCE), eq(pointTags));
    expectLastCall().atLeastOnce();

    // TODO - change WavefrontInternalReporter.newWavefrontHistogram to pass in a clock to
    // advance minute bin and change the below call to expectLastCall().atLeastOnce();
    wfSender.sendDistribution(eq(
        "tracing.derived.myApplication.myService.dummyOp.duration.micros"),
        anyObject(), eq(new HashSet<>(Arrays.asList(HistogramGranularity.MINUTE))), anyLong(),
        eq(DEFAULT_SOURCE), eq(pointTags));
    expectLastCall().anyTimes();

    wfSender.sendMetric(eq(
        "tracing.derived.myApplication.myService.dummyOp.total_time.millis.count"),
        anyLong(), anyLong(), eq(DEFAULT_SOURCE), eq(pointTags));
    expectLastCall().atLeastOnce();

    replay(wfSender);
    WavefrontTracer tracer = new WavefrontTracer.Builder(
        new WavefrontSpanReporter.Builder().withSource(DEFAULT_SOURCE).build(wfSender),
        buildApplicationTags()).setReportFrequenceMillis(50).
        build();
    tracer.buildSpan("dummyOp").withTag("tenant", "tenant1").
        withTag("env", "Staging").startActive(true).close();
    // Sleep for 1 seconds
    System.out.println("Sleeping for 1 second zzzzz .....");
    Thread.sleep(1000);
    System.out.println("Resuming execution .....");
    verify(wfSender);
  }

  private Map<String, String> pointTags(String operationName, Map<String, String> customTags) {
    return new HashMap<String, String>() {{
      put("application", "myApplication");
      put("service", "myService");
      put("cluster", "none");
      put("shard", "none");
      put("component", "none");
      put("operationName", operationName);
      putAll(customTags);
    }};
  }
}
