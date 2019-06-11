package com.wavefront.opentracing.propagation;

import com.wavefront.opentracing.WavefrontSpanContext;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import io.opentracing.propagation.TextMap;

/**
 * Bridge for extracting/injecting Jaeger headers to/from a WavefrontSpanContext.
 *
 * Essentially allows for extracting Jaeger HTTP headers and creating a WavefrontSpanContext or
 * injecting Jaeger aware HTTP headers from a WavefrontSpanContext.
 *
 * @author akuncham@vmware.com
 *
 * <p>
 * Example usage:
 *
 * <pre>{@code
 * JaegerWavefrontPropagator propogator = new JaegerWavefrontPropagator.Builder()
 *                                         .withBaggagePrefix("uberctx-")
 *                                         .withTraceIdHeader("uber-trace-id").build();
 * tracerBuilder = new WavefrontTracer.Builder(..);
 * tracerBuilder.registerPropogator(Format.Builtin.HTTP_HEADERS, propogator);
 * tracerBuilder.registerPropogator(Format.Builtin.TEXT_MAP, propogator);
 * WavefrontTracer tracer = tracerBuilder.build();
 * ...
 * }</pre>
 *
 * <p>
 */
public class JaegerWavefrontPropagator implements Propagator<TextMap> {

  private static final String BAGGAGE_PREFIX = "baggage-";
  private static final String TRACE_ID_KEY = "trace-id";
  private static final String PARENT_ID_KEY = "parent-id";
  private static final String SAMPLING_DECISION_KEY = "sampling-decision";

  private final String traceIdHeader;
  private final String baggagePrefix;

  private JaegerWavefrontPropagator(Builder builder) {
    this.traceIdHeader = builder.traceIdHeader;
    this.baggagePrefix = builder.baggagePrefix;
  }

  @Nullable
  @Override
  public WavefrontSpanContext extract(TextMap carrier) {
    UUID traceId = null;
    UUID spanId = null;
    String parentId = null;
    Boolean samplingDecision = null;
    Map<String, String> baggage = new HashMap<>();

    for (Map.Entry<String, String> entry : carrier) {
      String k = entry.getKey().toLowerCase();
      if (k.equalsIgnoreCase(traceIdHeader)) {
        String[] traceData = contextFromTraceIdHeader(entry.getValue());
        if (traceData == null) {
          continue;
        }
        traceId = traceIdToUuid(traceData[0]);
        spanId = spanIdToUuid(traceData[1]);
        parentId = traceData[2];
        samplingDecision = traceData[3].equals("1");
      } else if (k.startsWith(baggagePrefix)) {
        baggage.put(strippedPrefix(entry.getKey()), entry.getValue());
      }
    }

    if (traceId == null || spanId == null) {
      return null;
    }
    baggage.put(PARENT_ID_KEY, parentId);
    return new WavefrontSpanContext(traceId, spanId, baggage, samplingDecision);
  }

  @Override
  public void inject(WavefrontSpanContext spanContext, TextMap carrier) {
    carrier.put(traceIdHeader, contextToTraceIdHeader(spanContext));
    for (Map.Entry<String, String> entry : spanContext.baggageItems()) {
      carrier.put(baggagePrefix + entry.getKey(), entry.getValue());
    }
    if (spanContext.isSampled()) {
      carrier.put(SAMPLING_DECISION_KEY, spanContext.getSamplingDecision().toString());
    }
  }

  /**
   * Extracts traceId, spanId, parentId and samplingDecision from the 'uber-trace-id' HTTP header
   * value that's in the format traceId:spanId:parentId:samplingDecision.
   *
   * @param value 'uber-trace-id' header value
   * @return extracted string array with ids
   */
  @Nullable
  private String[] contextFromTraceIdHeader(String value) {
    if (value == null || value.equals("")) {
      return null;
    }
    String[] toks = value.split(":");
    if (toks.length != 4) {
      return null;
    }
    if (toks[0] == null || toks[0].length() == 0) {
      return null;
    }
    return toks;
  }

  /**
   * Extracts traceId and spanId from a WavefrontSpanContext and constructs a Jaeger client
   * compatible header of the form traceId:spanId:parentId:samplingDecision.
   *
   * @param context a WavefrontSpanContext
   * @return formatted header as string
   */
  private String contextToTraceIdHeader(WavefrontSpanContext context) {
    BigInteger traceId = uuidToBigInteger(context.getTraceId());
    BigInteger spanId = uuidToBigInteger(context.getSpanId());
    Boolean samplingDecision = context.getSamplingDecision();
    String parentId = context.getBaggageItem(PARENT_ID_KEY);
    if (samplingDecision == null) {
      samplingDecision = false;
    }

    StringBuilder outCtx = new StringBuilder();
    outCtx.append(traceId.toString(16)).append(":").
        append(spanId.toString(16)).append(":").
        append(parentId).append(":").
        append(samplingDecision ? "1" : "0");
    return outCtx.toString();
  }

  private String strippedPrefix(String val) {
    return val.substring(baggagePrefix.length());
  }

  /**
   * Parses a UUID and converts to BigInteger.
   *
   * @param id UUID of the traceId or spanId
   * @return BigInteger for UUID.
   */
  private BigInteger uuidToBigInteger(UUID id) {
    ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
    bb.putLong(id.getMostSignificantBits());
    bb.putLong(id.getLeastSignificantBits());
    return new BigInteger(1, bb.array());
  }

  /**
   * Parses a full (low + high) traceId, trimming the lower 64 bits.
   * Inspired by {@code io.jaegertracing.internal.propagation}
   *
   * @param hexString a full traceId
   * @return the long value of the higher 64 bits for a 128 bit traceId or 0 for 64 bit traceIds
   */
  private static long high(String hexString) {
    if (hexString.length() <= 16) {
      return 0L;
    }
    int highLength = hexString.length() - 16;
    String highString = hexString.substring(0, highLength);
    return new BigInteger(highString, 16).longValue();
  }

  /**
   * Constructs a UUID for the spanId represented as hexString.
   *
   * UUID is constructed with the long value of the lower 64 bits of the spanId to be consistent
   * with Jaeger client's approach.
   *
   * @param id spanId as hexString
   * @return UUID of the spanId as expected by WavefrontSpanContext
   */
  private UUID spanIdToUuid(String id) {
    long num = new BigInteger(id, 16).longValue();
    return new UUID(0, num);
  }

  /**
   * Constructs UUID for traceId represented as hexString consisting  of (low + high) 64 bits.
   *
   * UUID is generated with long value of  high 64 bits(if any) and long value of low 64 bits and is
   * consistent with the Wavefront approach.
   *
   * @param id hexString form of traceId
   * @return UUID for traceId as expected by WavefrontSpanContext
   */
  private UUID traceIdToUuid(String id) {
    long traceIdLow = new BigInteger(id, 16).longValue();
    long traceIdHigh = high(id);
    return new UUID(traceIdHigh, traceIdLow);
  }

  /**
   * Gets a new {@link JaegerWavefrontPropagator.Builder} instance.
   *
   * @return a {@link JaegerWavefrontPropagator.Builder}
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * A builder for {@link JaegerWavefrontPropagator} instances.
   */
  public static class Builder {
    private String traceIdHeader = TRACE_ID_KEY;
    private String baggagePrefix = BAGGAGE_PREFIX;

    public Builder withTraceIdHeader(String traceIdHeader) {
      this.traceIdHeader = traceIdHeader;
      return this;
    }

    public Builder withBaggagePrefix(String baggagePrefix) {
      this.baggagePrefix = baggagePrefix;
      return this;
    }

    /**
     * Builds and returns a JaegerWavefrontPropagator instance based on the given configuration.
     *
     * @return a {@link JaegerWavefrontPropagator}
     */
    public JaegerWavefrontPropagator build() {
      return new JaegerWavefrontPropagator(this);
    }
  }
}
