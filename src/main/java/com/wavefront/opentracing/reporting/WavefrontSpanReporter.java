package com.wavefront.opentracing.reporting;

import com.wavefront.internal.reporter.WavefrontInternalReporter;
import com.wavefront.internal_reporter_java.io.dropwizard.metrics5.Counter;
import com.wavefront.internal_reporter_java.io.dropwizard.metrics5.MetricName;
import com.wavefront.opentracing.Reference;
import com.wavefront.opentracing.WavefrontSpan;
import com.wavefront.opentracing.WavefrontSpanContext;
import com.wavefront.sdk.common.WavefrontSender;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;
import java.util.Random;
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
  private static final Logger logger = Logger.getLogger(WavefrontSpanReporter.class.getName());

  private final WavefrontSender wavefrontSender;
  private final String source;
  private final LinkedBlockingQueue<WavefrontSpan> spanBuffer;
  private final Thread sendingThread;
  private final Random random;
  private final float logPercent;

  private volatile WavefrontInternalReporter metricsReporter;
  private Counter spansDropped;
  private Counter spansReceived;
  private Counter reportErrors;

  private volatile boolean stop = false;

  public static final class Builder {
    private String source;
    private int maxQueueSize = 50000;
    private float logPercent = 0.1f;

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
     * Set the percent of log messages to be logged. Defaults to 10%.
     *
     * @param percent a value between 0.0 and 1.0
     * @return {@code this}
     */
    public Builder withLoggingPercent(float percent) {
      logPercent = percent;
      return this;
    }

    /**
     * Builds a {@link WavefrontSpanReporter} for sending opentracing spans to a
     * WavefrontSender that can send those spans either be a via proxy or direct ingestion.
     *
     * @return {@link WavefrontSpanReporter}
     */
    public WavefrontSpanReporter build(WavefrontSender wavefrontSender) {
      return new WavefrontSpanReporter(wavefrontSender, this.source, this.maxQueueSize, this.logPercent);
    }
  }

  private WavefrontSpanReporter(WavefrontSender wavefrontSender, String source, int maxQueueSize,
                                float logPercent) {
    this.wavefrontSender = wavefrontSender;
    this.source = source;
    this.spanBuffer = new LinkedBlockingQueue<>(maxQueueSize);
    this.random = new Random();
    this.logPercent = logPercent;

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
      } catch (Throwable ex) {
        logger.log(Level.WARNING, "Error processing buffer", ex);
      }
    }
  }

  @Override
  public void report(WavefrontSpan span) {
    if (metricsReporter != null) {
      spansReceived.inc();
    }
    if (!spanBuffer.offer(span)) {
      if (metricsReporter != null) {
        spansDropped.inc();
      }
      if (isLoggable()) {
        logger.warning("Buffer full, dropping span: " + span);
        logger.warning("Total spans dropped: " + spansDropped.getCount());
      }
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
      if (isLoggable()) {
        logger.log(Level.WARNING, "error reporting span: " + span, e);
      }
      if (metricsReporter != null) {
        reportErrors.inc();
        spansDropped.inc();
      }
    }
  }

  private boolean isLoggable() {
    return random.nextFloat() <= logPercent;
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

  public void setMetricsReporter(WavefrontInternalReporter metricsReporter) {
    this.metricsReporter = metricsReporter;

    // init internal metrics
    metricsReporter.newGauge(new MetricName("reporter.queue.size", Collections.emptyMap()),
        () -> (double) spanBuffer.size());
    metricsReporter.newGauge(new MetricName("reporter.queue.remaining_capacity",
        Collections.emptyMap()), () -> (double) spanBuffer.remainingCapacity());
    spansReceived = metricsReporter.newCounter(new MetricName("reporter.spans.received",
        Collections.emptyMap()));
    spansDropped = metricsReporter.newCounter(new MetricName("reporter.spans.dropped",
        Collections.emptyMap()));
    reportErrors = metricsReporter.newCounter(new MetricName("reporter.errors",
        Collections.emptyMap()));
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
