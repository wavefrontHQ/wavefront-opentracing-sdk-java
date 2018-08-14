package com.wavefront.opentracing.reporting;

import com.wavefront.opentracing.WavefrontSpan;
import com.wavefront.sdk.direct_ingestion.WavefrontDirectIngestionClient;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * The reporter that publishes traces to Wavefront via direct ingestion.
 *
 * @author Vikram Raman (vikram@wavefront.com)
 */
public class WavefrontDirectReporter extends BaseWavefrontReporter implements Reporter {

  private final WavefrontDirectIngestionClient wavefrontDirectIngestionClient;

  public static final class Builder {
    // Required parameters
    private final String server;
    private final String token;

    // Optional parameters
    private String source;
    private int maxQueueSize = 50000;
    private int flushIntervalSeconds = 1;

    public Builder(String server, String token) {
      this.server = server;
      this.token = token;
      this.source = getDefaultSource();
    }

    private static String getDefaultSource() {
      try {
        return InetAddress.getLocalHost().getHostName();
      } catch (UnknownHostException ex) {
        return "wavefront-tracer-direct-reporter";
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
    public Builder flushIntervalSeconds(int interval) {
      this.flushIntervalSeconds = interval;
      return this;
    }

    /**
     * Set the maximum queue size for buffering spans in memory between flush intervals.
     *
     * @param maxQueueSize the maximum queue size
     * @return {@code this}
     */
    public Builder maxQueueSize(int maxQueueSize) {
      this.maxQueueSize = maxQueueSize;
      return this;
    }

    /**
     * Builds a {@link WavefrontProxyReporter} for sending spans to a Wavefront proxy.
     *
     * @return {@link WavefrontDirectReporter}
     * @throws IOException If an error occurs creating the reporter
     */
    public WavefrontDirectReporter build() throws IOException {
      WavefrontDirectIngestionClient wavefrontDirectIngestionClient =
              new WavefrontDirectIngestionClient.Builder(this.server, this.token).
                      maxQueueSize(this.maxQueueSize).
                      flushIntervalSeconds(this.flushIntervalSeconds).build();
      return new WavefrontDirectReporter(wavefrontDirectIngestionClient, this.source);
    }
  }

  private WavefrontDirectReporter(WavefrontDirectIngestionClient wavefrontDirectIngestionClient,
                                  String source) throws IOException {
    super(wavefrontDirectIngestionClient, source);
    this.wavefrontDirectIngestionClient = wavefrontDirectIngestionClient;
  }

  @Override
  public void report(WavefrontSpan span) throws IOException {
    sendSpan(span);
  }

  @Override
  public int getFailureCount() {
    return wavefrontDirectIngestionClient.getFailureCount();
  }

  @Override
  public void close() throws IOException {
    wavefrontDirectIngestionClient.close();
  }
}
