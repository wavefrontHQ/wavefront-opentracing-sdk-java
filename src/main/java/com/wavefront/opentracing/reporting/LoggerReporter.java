package com.wavefront.opentracing.reporting;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.wavefront.opentracing.Reference;
import com.wavefront.opentracing.WavefrontSpan;
import com.wavefront.opentracing.WavefrontSpanContext;
import com.wavefront.sdk.common.Utils;
import com.wavefront.sdk.entities.tracing.SpanLog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.wavefront.sdk.common.Utils.spanLogsToLineData;

/**
 * Logger reporter that logs finished spans to the slf4j logger. Useful for debugging.
 *
 * @author Scott Feldstein (sfeldstein@vmware.com)
 */
public class LoggerReporter implements Reporter {
  private final Logger logger = LoggerFactory.getLogger(LoggerReporter.class);

  private final String source;

  public LoggerReporter(String source) {
    this.source = source;
  }

  @Override
  public void report(WavefrontSpan span) {
    if (!logger.isTraceEnabled()) {
      return;
    }
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
    logger.trace("Finished span: sampling={} {}", ctx.getSamplingDecision(), spanLine);
    if (!spanLogs.isEmpty()) {
      try {
        logger.trace("SpanLogs: {}", spanLogsToLineData(ctx.getTraceId(), ctx.getSpanId(), spanLogs));
      } catch (JsonProcessingException e) {
        logger.trace("Error processing the span logs: {}", e.getMessage(), e);
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

  @Override
  public void flush() {
    // no-op
  }
}