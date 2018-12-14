package com.wavefront.opentracing.propagation;

import com.wavefront.opentracing.WavefrontSpanContext;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import io.opentracing.propagation.TextMap;

/**
 * Propagate contexts within TextMaps.
 *
 * @author Vikram Raman (vikram@wavefront.com)
 */
public class TextMapPropagator implements Propagator<TextMap> {

  private static final String BAGGAGE_PREFIX = "wf-ot-";
  private static final String TRACE_ID = BAGGAGE_PREFIX + "traceid";
  private static final String SPAN_ID = BAGGAGE_PREFIX + "spanid";
  private static final String SAMPLE = BAGGAGE_PREFIX + "sample";

  @Override
  public void inject(WavefrontSpanContext spanContext, TextMap carrier) {
    carrier.put(TRACE_ID, spanContext.getTraceId().toString());
    carrier.put(SPAN_ID, spanContext.getSpanId().toString());
    for (Map.Entry<String, String> entry : spanContext.baggageItems()) {
      carrier.put(BAGGAGE_PREFIX + entry.getKey(), entry.getValue());
    }
    if (spanContext.isSampled()) {
      carrier.put(SAMPLE, spanContext.getSamplingDecision().toString());
    }
  }

  @Nullable
  @Override
  public WavefrontSpanContext extract(TextMap carrier) {

    UUID traceId = null;
    UUID spanId = null;
    Map<String, String> baggage = null;
    Boolean sampling = null;

    for (Map.Entry<String, String> entry : carrier) {
      //TODO: verify locale
      String key = entry.getKey().toLowerCase(Locale.ROOT);

      if (TRACE_ID.equals(key)) {
        traceId = UUID.fromString(entry.getValue());
      } else if (SPAN_ID.equals(key)) {
        spanId = UUID.fromString(entry.getValue());
      } else if (SAMPLE.equals(key)) {
        sampling = Boolean.valueOf(entry.getValue());
      } else if (key.startsWith(BAGGAGE_PREFIX)) {
        if (baggage == null) {
          baggage = new HashMap<>();
        }
        baggage.put(stripPrefix(key), entry.getValue());
      }
    }

    if (traceId == null || spanId == null) {
      return null;
    }
    return new WavefrontSpanContext(traceId, spanId, baggage, sampling);
  }

  private static String stripPrefix(String key) {
    return key.substring(BAGGAGE_PREFIX.length());
  }
}
