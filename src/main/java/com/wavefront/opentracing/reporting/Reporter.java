package com.wavefront.opentracing.reporting;

import com.wavefront.opentracing.WavefrontSpan;

import java.io.IOException;

/**
 * Interface for reporting finished spans.
 *
 * @author Vikram Raman (vikram@wavefront.com)
 */
public interface Reporter {
  void report(WavefrontSpan span) throws IOException;

  void close() throws IOException;
}
