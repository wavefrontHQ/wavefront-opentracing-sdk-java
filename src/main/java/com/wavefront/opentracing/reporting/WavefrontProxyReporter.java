package com.wavefront.opentracing.reporting;

import com.wavefront.opentracing.WavefrontSpan;
import com.wavefront.sdk.common.NamedThreadFactory;
import com.wavefront.sdk.proxy.WavefrontProxyClient;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The reporter which publishes traces to Wavefront via a Wavefront proxy.
 *
 * @author Vikram Raman (vikram@wavefront.com)
 */
public class WavefrontProxyReporter extends BaseWavefrontReporter implements Reporter, Runnable {

  private static final Logger LOGGER = Logger.getLogger(WavefrontProxyReporter.class.getName());
  private static final int DEFAULT_FLUSH_INTERVAL = 1000;
  private static final int MAX_QUEUE_SIZE = 10000;

  private final WavefrontProxyClient proxyClient;
  private final LinkedBlockingQueue<WavefrontSpan> buffer;
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
      new NamedThreadFactory("wavefrontProxyTracingReporter"));

  private WavefrontProxyReporter(WavefrontProxyClient proxyClient, String source,
                                 int flushInterval, int maxQueueSize) throws IOException {
    super(proxyClient, source);
    this.proxyClient = proxyClient;
    this.proxyClient.connect();

    buffer = new LinkedBlockingQueue<>(maxQueueSize);
    scheduler.scheduleAtFixedRate(this, DEFAULT_FLUSH_INTERVAL, flushInterval,
        TimeUnit.MILLISECONDS);
  }

  @Override
  public void report(WavefrontSpan span) throws IOException {
    if (!buffer.offer(span)) {
      if (LOGGER.isLoggable(Level.FINER)) {
        LOGGER.finer("Dropping span: " + span);
      }
    }
  }

  private void internalFlush() throws IOException {
    if (!proxyClient.isConnected()) {
      proxyClient.connect();
    }

    List<WavefrontSpan> spans = new ArrayList<>(MAX_QUEUE_SIZE);
    buffer.drainTo(spans, MAX_QUEUE_SIZE);
    for (WavefrontSpan span : buffer) {
      sendSpan(span);
    }
    proxyClient.flush();
  }

  @Override
  public void run() {
    try {
      internalFlush();
    } catch (IOException ex) {
      LOGGER.warning("Error flushing spans to Wavefront proxy");
    }
  }

  @Override
  public void close() throws IOException {
    proxyClient.close();
  }

  public static final class Builder {

    private int flushInterval = DEFAULT_FLUSH_INTERVAL;
    private int maxQueueSize = MAX_QUEUE_SIZE;
    private String source;

    public Builder() {
      this.source = getDefaultSource();
    }

    private static String getDefaultSource() {
      try {
        return InetAddress.getLocalHost().getHostName();
      } catch (UnknownHostException ex) {
        return "wavefront-tracer-proxy-reporter";
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
     * Set the flush interval for this reporter.
     *
     * @param interval the flush interval for reporting spans to a Wavefront proxy
     * @return {@code this}
     */
    public Builder withFlushInterval(int interval) {
      this.flushInterval = interval;
      return this;
    }

    /**
     * Set the maximum queue size for buffering spans in memory between flush intervals.
     *
     * @param maxQueueSize the maximum queue size
     * @return {@code this}
     */
    public Builder withMaxQueueSize(int maxQueueSize) {
      this.maxQueueSize = maxQueueSize;
      return this;
    }

    /**
     * Builds a  {@link WavefrontProxyReporter} for sending spans to a Wavefront proxy.
     *
     * @param host The host name of the Wavefront proxy
     * @param port The tracing port of the Wavefront proxy
     * @return {@link WavefrontProxyReporter}
     * @throws IOException If an error occurs creating the reporter
     */
    public WavefrontProxyReporter build(String host, int port) throws IOException {
      WavefrontProxyClient client = new WavefrontProxyClient.Builder(host).tracingPort(port).build();
      return new WavefrontProxyReporter(client, source, flushInterval, maxQueueSize);
    }
  }
}
