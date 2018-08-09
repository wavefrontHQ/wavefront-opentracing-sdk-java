package com.wavefront.opentracing.propagation;

import com.wavefront.opentracing.WavefrontSpanContext;

/**
 * The interface for extracting SpanContext's from carriers.
 *
 * @author Vikram Raman (vikram@wavefront.com)
 */
public interface Extractor<T> {

  /**
   * Extracts a span context from the given carrier.
   *
   * @param carrier The carrier to extract the span context from
   * @return The extracted span context
   */
  WavefrontSpanContext extract(T carrier);
}
