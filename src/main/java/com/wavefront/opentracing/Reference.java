package com.wavefront.opentracing;

/**
 * Represents a parent context reference.
 *
 * @author Vikram Raman (vikram@wavefront.com)
 */
public class Reference {

  private final WavefrontSpanContext spanContext;
  private final String type;

  public Reference(WavefrontSpanContext spanContext, String type) {
    this.spanContext = spanContext;
    this.type = type;
  }

  public WavefrontSpanContext getSpanContext() {
    return spanContext;
  }

  public String getType() {
    return type;
  }
}
