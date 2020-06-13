package com.wavefront.opentracing.reporting;

import com.wavefront.opentracing.WavefrontSpan;

import java.io.Closeable;
import java.io.IOException;

/**
 * Interface for reporting finished spans.
 *
 * @author Vikram Raman (vikram@wavefront.com)
 */
public interface Reporter extends Closeable {

  /**
   * Report opentracing span to Wavefront
   *
   * @param span OpenTracing span
   * @throws IOException
   */
  void report(WavefrontSpan span) throws IOException;

  /**
   * Get total failure count reported by this reporter
   *
   * @return total failure count
   */
  int getFailureCount();

  /**
   * Close the reporter. WIll flush in-flight buffer before closing.
   *
   * @throws IOException
   */
  void close() throws IOException;

  /**
   * Flush the data of reporter.
   */
  void flush();
}
