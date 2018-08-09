package com.wavefront.opentracing.propagation;

import com.wavefront.opentracing.WavefrontSpanContext;

/**
 * Interface for injecting spanContext's into carriers.
 *
 * @author Vikram Raman (vikram@wavefront.com)
 */
public interface Injector<T> {

  /**
   * Inject the given context into the given carrier.
   *
   * @param spanContext The span context to serialize
   * @param carrier The carrier to inject the span context into
   */
  void inject(WavefrontSpanContext spanContext, T carrier);
}
