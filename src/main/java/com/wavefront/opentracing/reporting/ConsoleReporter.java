package com.wavefront.opentracing.reporting;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.wavefront.opentracing.Reference;
import com.wavefront.opentracing.WavefrontSpan;
import com.wavefront.opentracing.WavefrontSpanContext;
import com.wavefront.sdk.common.Utils;
import com.wavefront.sdk.entities.tracing.SpanLog;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.wavefront.sdk.common.Utils.spanLogsToLineData;

/**
 * Console reporter that logs finished spans to the console. Useful for debugging.
 *
 * @author Vikram Raman (vikram@wavefront.com)
 */
public class ConsoleReporter implements Reporter {

  private final String source;

  public ConsoleReporter(String source) {
    this.source = source;
  }

  @Override
  public void report(WavefrontSpan span) {
    WavefrontSpanContext ctx = span.context();
    List<Reference> parentRefs = span.getParents();
    List<Reference> followsRefs = span.getFollows();

    List<UUID> parents = parentRefs == null ? null : parentRefs.stream().
        map(Reference::getSpanContext).
        map(WavefrontSpanContext::getSpanId).
        collect(Collectors.toList());

    List<UUID> follows = followsRefs == null ? null : followsRefs.stream().
        map(Reference::getSpanContext).
        map(WavefrontSpanContext::getSpanId).
        collect(Collectors.toList());

    List<SpanLog> spanLogs = span.getSpanLogs();

    String spanLine = Utils.tracingSpanToLineData(span.getOperationName(),
        span.getStartTimeMicros(), span.getDurationMicroseconds(), source, ctx.getTraceId(),
        ctx.getSpanId(), parents, follows, span.getTagsAsList(), spanLogs, "unknown");
    System.out.println("Finished span: sampling=" + ctx.getSamplingDecision() + " " + spanLine);
    if (!spanLogs.isEmpty()) {
      try {
        System.out.println("SpanLogs: " +
            spanLogsToLineData(ctx.getTraceId(), ctx.getSpanId(), spanLogs));
      } catch (JsonProcessingException e) {
        System.out.println("Error processing the span logs " + e);
      }
    }
  }

  @Override
  public int getFailureCount() {
    // no-op
    return 0;
  }

  @Override
  public void close() {
    // no-op
  }
}