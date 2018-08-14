package com.wavefront.opentracing.reporting;

import com.wavefront.opentracing.WavefrontSpan;
import com.wavefront.sdk.proxy.WavefrontProxyClient;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The reporter which publishes traces to Wavefront via a Wavefront proxy.
 *
 * @author Vikram Raman (vikram@wavefront.com)
 */
public class WavefrontProxyReporter extends BaseWavefrontReporter implements Reporter {

  private static final Logger LOGGER = Logger.getLogger(WavefrontProxyReporter.class.getName());

  private final WavefrontProxyClient wavefrontProxyClient;

  public static final class Builder {

    private final String proxyHost;
    private final int tracingPort;
    private int flushIntervalSeconds = 5;
    private String source;

    public Builder(String proxyHost, int tracingPort) {
      this.proxyHost = proxyHost;
      this.tracingPort = tracingPort;
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
     * Override the flush interval for this client (default is 5 seconds)
     *
     * @param flushIntervalSeconds flush interval in seconds
     * @return {@code this}
     */
    public Builder flushIntervalSeconds(int flushIntervalSeconds) {
      this.flushIntervalSeconds = flushIntervalSeconds;
      return this;
    }

    /**
     * Builds a  {@link WavefrontProxyReporter} for sending opentracing spans to a Wavefront proxy.
     *
     * @return {@link WavefrontProxyReporter}
     * @throws IOException If an error occurs creating the reporter
     */
    public WavefrontProxyReporter build() throws IOException {
      WavefrontProxyClient wavefrontProxyClient = new WavefrontProxyClient.Builder(this.proxyHost).
              tracingPort(this.tracingPort).flushIntervalSeconds(this.flushIntervalSeconds).build();
      return new WavefrontProxyReporter(wavefrontProxyClient, this.source);
    }
  }

  private WavefrontProxyReporter(WavefrontProxyClient wavefrontProxyClient, String source) throws IOException {
    super(wavefrontProxyClient, source);
    this.wavefrontProxyClient = wavefrontProxyClient;
  }

  @Override
  public void report(WavefrontSpan span) {
    try {
      sendSpan(span);
    } catch (IOException e) {
      if (LOGGER.isLoggable(Level.FINER)) {
        LOGGER.finer("Dropping span: " + span);
      }
    }
  }

  @Override
  public int getFailureCount() {
    return wavefrontProxyClient.getFailureCount();
  }

  @Override
  public void close() throws IOException {
    // flush buffer & close client
    wavefrontProxyClient.close();
  }
}
