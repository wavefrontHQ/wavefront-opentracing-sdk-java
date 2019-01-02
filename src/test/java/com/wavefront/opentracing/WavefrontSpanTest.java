package com.wavefront.opentracing;

import com.wavefront.opentracing.reporting.WavefrontSpanReporter;
import com.wavefront.sdk.common.WavefrontSender;
import com.wavefront.sdk.entities.histograms.HistogramGranularity;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

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

  @Disabled
  @Test
  public void testValidWavefrontSpan() throws IOException, InterruptedException {
    String operationName = "dummyOp";
    Map<String, String> pointTags = new HashMap<String, String>() {{
      put("application", "myApplication");
      put("service", "myService");
      put("cluster", "none");
      put("shard", "none");
      put("operationName", operationName);
    }};
    WavefrontSender wfSender = createMock(WavefrontSender.class);
    wfSender.sendSpan(eq(operationName), anyLong(), anyLong(), eq(DEFAULT_SOURCE),
        anyObject(), anyObject(), eq(Collections.emptyList()), eq(Collections.emptyList()),
        anyObject(), eq(null));
    expectLastCall();
    wfSender.sendMetric(eq("~component.heartbeat"), eq(1.0) , anyLong(), eq
            (DEFAULT_SOURCE), anyObject());
    expectLastCall();

    wfSender.sendMetric(eq(
        "tracing.derived.myApplication.myService.dummyOp.invocation.count"),
        eq(1.0), anyLong(), eq(DEFAULT_SOURCE), eq(pointTags));
    expectLastCall();

    wfSender.sendDistribution(eq(
        "tracing.derived.myApplication.myService.dummyOp.duration.micros"),
        anyObject(), eq(new HashSet<>(Arrays.asList(HistogramGranularity.MINUTE))), anyLong(),
        eq(DEFAULT_SOURCE), eq(pointTags));
    expectLastCall();

    replay(wfSender);
    WavefrontTracer tracer = new WavefrontTracer.Builder(
        new WavefrontSpanReporter.Builder().withSource(DEFAULT_SOURCE).build(wfSender),
        buildApplicationTags()).build();
    tracer.buildSpan("dummyOp").startActive(true).close();
    // Sleep for 60+ seconds
    System.out.println("Sleeping for 75 seconds zzzzz .....");
    Thread.sleep(1000 * 75);
    System.out.println("Resuming execution .....");
    verify(wfSender);
  }
}
