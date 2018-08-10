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

  private static final int DEFAULT_FLUSH_INTERVAL = 1000;
  private static final int MAX_QUEUE_SIZE = 10000;

  private final WavefrontDirectIngestionClient directClient;

  private WavefrontDirectReporter(WavefrontDirectIngestionClient directClient, String source)
      throws IOException {
    super(directClient, source);
    this.directClient = directClient;
    this.directClient.connect();
  }

  @Override
  public void report(WavefrontSpan span) throws IOException {
    sendSpan(span);
  }

  @Override
  public void close() throws IOException {
    directClient.close();
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
     * @param server The Wavefront server of the form "https://serverName.wavefront.com"
     * @param token  The Wavefront API token with direct data ingestion permission
     * @return {@link WavefrontDirectReporter}
     * @throws IOException If an error occurs creating the reporter
     */
    public WavefrontDirectReporter build(String server, String token) throws IOException {

      //TODO: pass in flush interval and queue size to the direct ingestion client
      WavefrontDirectIngestionClient client = new WavefrontDirectIngestionClient(server, token);
      return new WavefrontDirectReporter(client, source);
    }
  }
}
