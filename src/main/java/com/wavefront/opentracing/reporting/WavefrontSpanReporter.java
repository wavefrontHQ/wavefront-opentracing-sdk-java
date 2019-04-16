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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.wavefront.opentracing.common.Constants.DEFAULT_SOURCE;

/**
 * The reporter which reports tracing spans to Wavefront via WavefrontSender.
 *
 * @author Vikram Raman (vikram@wavefront.com)
 */
public class WavefrontSpanReporter implements Reporter, Runnable {
  private static final Logger logger =
      Logger.getLogger(WavefrontSpanReporter.class.getName());

  private final WavefrontSender wavefrontSender;
  private final String source;
  private final LinkedBlockingQueue<WavefrontSpan> spanBuffer;
  private final Thread sendingThread;

  private volatile boolean stop = false;

  public static final class Builder {
    private String source;
    private int maxQueueSize = 50000;

    public Builder() {
      this.source = getDefaultSource();
    }

    private static String getDefaultSource() {
      try {
        return InetAddress.getLocalHost().getHostName();
      } catch (UnknownHostException ex) {
        return DEFAULT_SOURCE;
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
     * Set max queue size of in-memory buffer. Incoming spans are dropped if buffer is full.
     *
     * @param maxQueueSize Max queue size of in-memory buffer
     * @return {@code this}
     */
    public Builder withMaxQueueSize(int maxQueueSize) {
      this.maxQueueSize = maxQueueSize;
      return this;
    }

    /**
     * Builds a {@link WavefrontSpanReporter} for sending opentracing spans to a
     * WavefrontSender that can send those spans either be a via proxy or direct ingestion.
     *
     * @return {@link WavefrontSpanReporter}
     */
    public WavefrontSpanReporter build(WavefrontSender wavefrontSender) {
      return new WavefrontSpanReporter(wavefrontSender, this.source, this.maxQueueSize);
    }
  }

  private WavefrontSpanReporter(WavefrontSender wavefrontSender, String source, int maxQueueSize) {
    this.wavefrontSender = wavefrontSender;
    this.source = source;
    this.spanBuffer = new LinkedBlockingQueue<>(maxQueueSize);

    sendingThread = new Thread(this, "wavefrontSpanReporter");
    sendingThread.setDaemon(true);
    sendingThread.start();
  }

  @Override
  public void run() {
    while (!stop) {
      try {
        WavefrontSpan span = spanBuffer.take();
        send(span);
      } catch (InterruptedException ex) {
        if (logger.isLoggable(Level.INFO)) {
          logger.info("reporting thread interrupted");
        }
      } catch (Exception ex) {
        logger.log(Level.WARNING, "Error processing buffer", ex);
      }
    }
  }

  @Override
  public void report(WavefrontSpan span) {
    if (!spanBuffer.offer(span)) {
      logger.warning("Dropping span: " + span);
    }
  }

  private void send(WavefrontSpan span) {
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

  public String getSource() {
    return source;
  }

  public WavefrontSender getWavefrontSender() {
    return wavefrontSender;
  }

  @Override
  public int getFailureCount() {
    return wavefrontSender.getFailureCount();
  }

  @Override
  public void close() throws IOException {
    stop = true;
    try {
      // wait for 5 secs max
      sendingThread.join(5000);
    } catch (InterruptedException ex) {
      // no-op
    }
    // flush buffer & close client
    wavefrontSender.close();
  }
}
