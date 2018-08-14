package com.wavefront.opentracing.propagation;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import io.opentracing.propagation.Format;

/**
 * Registry of available propagators.
 *
 * @author Vikram Raman (vikram@wavefront.com)
 */
public class PropagatorRegistry {

  private final Map<Format<?>, Propagator<?>> propagators = new HashMap<>();

  public PropagatorRegistry() {
    register(Format.Builtin.TEXT_MAP, new TextMapPropagator());
    register(Format.Builtin.HTTP_HEADERS, new HTTPPropagator());
  }

  @SuppressWarnings("unchecked")
  @Nullable
  public <T> Propagator<T> get(Format<T> format) {
    return (Propagator<T>) propagators.get(format);
  }

  public <T> void register(final Format<T> format, final Propagator<T> propagator) {
    propagators.put(format, propagator);
  }
}
