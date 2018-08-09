package com.wavefront.opentracing.reporting;

import com.wavefront.opentracing.Reference;
import com.wavefront.opentracing.WavefrontSpan;
import com.wavefront.opentracing.WavefrontSpanContext;
import com.wavefront.sdk.entities.tracing.WavefrontTracingSpanSender;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Abstract base class for reporting spans to Wavefront.
 *
 * @author Vikram Raman (vikram@wavefront.com)
 */
public abstract class BaseWavefrontReporter implements Reporter {

  private final WavefrontTracingSpanSender sender;
  private final String source;

  BaseWavefrontReporter(WavefrontTracingSpanSender sender, String source) {
    this.sender = sender;
    this.source = source;
  }

  protected void sendSpan(WavefrontSpan span) throws IOException {
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

    sender.sendSpan(span.getOperationName(), span.getStartTimeMicros()/1000,
        span.getDurationMicroseconds()/1000, source, ctx.getTraceId(), ctx.getSpanId(), parents,
        follows, span.getTagsAsList(),null);
  }
}
