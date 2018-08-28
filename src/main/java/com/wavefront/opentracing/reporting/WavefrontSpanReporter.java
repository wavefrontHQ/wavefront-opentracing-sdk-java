package com.wavefront.opentracing.reporting;

import com.wavefront.opentracing.Reference;
import com.wavefront.opentracing.WavefrontSpan;
import com.wavefront.opentracing.WavefrontSpanContext;
import com.wavefront.sdk.common.WavefrontSender;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * The reporter which reports tracing spans to Wavefront via WavefrontSender.
 *
 * @author Vikram Raman (vikram@wavefront.com)
 */
public class WavefrontSpanReporter implements Reporter {
  private static final Logger logger =
      Logger.getLogger(WavefrontSpanReporter.class.getName());

  private final WavefrontSender wavefrontSender;
  private final String source;

  public static final class Builder {
    private String source;

    public Builder() {
      this.source = getDefaultSource();
    }

    private static String getDefaultSource() {
      try {
        return InetAddress.getLocalHost().getHostName();
      } catch (UnknownHostException ex) {
        return "wavefront-tracer-reporter";
      }
    }

    /**
     * Set the source for this reporter.
     *
     * @param source the source for all spans
     * @return {@code this}
     */
    public Builder withSource(String source) {
      this.source = source;
      return this;
    }

    /**
     * Builds a {@link WavefrontSpanReporter} for sending opentracing spans to a
     * WavefrontSender that can send those spans either be a via proxy or direct ingestion.
     *
     * @return {@link WavefrontSpanReporter}
     * @throws IOException If an error occurs creating the reporter
     */
    public WavefrontSpanReporter build(WavefrontSender wavefrontSender)
        throws IOException {
      return new WavefrontSpanReporter(wavefrontSender, this.source);
    }
  }

  private WavefrontSpanReporter(WavefrontSender wavefrontSender, String source) {
    this.wavefrontSender = wavefrontSender;
    this.source = source;
  }

  @Override
  public void report(WavefrontSpan span) {
    try {
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

      wavefrontSender.sendSpan(span.getOperationName(), span.getStartTimeMicros() / 1000,
          span.getDurationMicroseconds() / 1000, source, ctx.getTraceId(), ctx.getSpanId(),
          parents, follows, span.getTagsAsList(), null);
    } catch (IOException e) {
      if (logger.isLoggable(Level.FINER)) {
        logger.finer("Dropping span: " + span);
      }
    }
  }

  @Override
  public int getFailureCount() {
    return wavefrontSender.getFailureCount();
  }

  @Override
  public void close() throws IOException {
    // flush buffer & close client
    wavefrontSender.close();
  }
}
