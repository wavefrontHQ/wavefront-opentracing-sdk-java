package com.wavefront.opentracing;

import com.wavefront.internal_reporter_java.io.dropwizard.metrics5.Counter;
import com.wavefront.internal_reporter_java.io.dropwizard.metrics5.MetricName;
import com.wavefront.sdk.common.Constants;
import com.wavefront.sdk.common.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

import io.opentracing.Span;
import io.opentracing.tag.Tags;

import static com.wavefront.sdk.common.Constants.COMPONENT_TAG_KEY;
import static com.wavefront.sdk.common.Constants.NULL_TAG_VAL;

/**
 * Represents a thread-safe Wavefront trace span based on OpenTracing's {@link Span}.
 *
 * @author Vikram Raman (vikram@wavefront.com)
 */
@ThreadSafe
public class WavefrontSpan implements Span {

  private final WavefrontTracer tracer;
  private final long startTimeMicros;
  private final long startTimeNanos;
  private final List<Pair<String, String>> tags;
  private final Map<String, Pair<String, String>> singleValuedTags;
  private final List<Reference> parents;
  private final List<Reference> follows;
  @Nullable
  private final Counter spansDiscarded;

  private String operationName;
  private long durationMicroseconds;
  private WavefrontSpanContext spanContext;
  private Boolean forceSampling = null;
  private boolean finished = false;
  private boolean isError = false;

  // Store it as a member variable so that we can efficiently retrieve the component tag.
  private String componentTagValue = NULL_TAG_VAL;

  private static Set<String> SINGLE_VALUED_TAG_KEYS = new HashSet<>(Arrays.asList(
      Constants.APPLICATION_TAG_KEY, Constants.SERVICE_TAG_KEY, Constants.CLUSTER_TAG_KEY,
      Constants.SHARD_TAG_KEY));

  WavefrontSpan(WavefrontTracer tracer, String operationName, WavefrontSpanContext spanContext,
                long startTimeMicros, long startTimeNanos, List<Reference> parents,
                List<Reference> follows, List<Pair<String, String>> tags,
                List<Pair<String, String>> globalTags) {
    this.tracer = tracer;
    this.operationName = operationName;
    this.spanContext = spanContext;
    this.startTimeMicros = startTimeMicros;
    this.startTimeNanos = startTimeNanos;
    this.parents = parents;
    this.follows = follows;

    spansDiscarded = tracer.getWfInternalReporter() == null ? null :
        tracer.getWfInternalReporter().newCounter(
            new MetricName("spans.discarded", Collections.emptyMap()));

    this.tags = (globalTags == null || globalTags.isEmpty()) && (tags == null || tags.isEmpty()) ?
      null : new ArrayList<>();
    this.singleValuedTags = new HashMap<>();
    if (globalTags != null) {
      for (Pair<String, String> tag : globalTags) {
        setTagObject(tag._1, tag._2);
      }
    }
    if (tags != null) {
      for (Pair<String, String> tag : tags) {
        setTagObject(tag._1, tag._2);
      }
    }
  }

  @Override
  public synchronized WavefrontSpanContext context() {
    return spanContext;
  }

  @Override
  public WavefrontSpan setTag(String key, String value) {
    return setTagObject(key, value);
  }

  @Override
  public WavefrontSpan setTag(String key, boolean value) {
    return setTagObject(key, value);
  }

  @Override
  public WavefrontSpan setTag(String key, Number value) {
    return setTagObject(key, value);
  }

  private synchronized WavefrontSpan setTagObject(String key, Object value) {
    if (key != null && !key.isEmpty() && value != null) {
      Pair<String, String> tag = Pair.of(key, value.toString());

      // if tag should be single-valued, replace the previous value if it exists
      if (isSingleValuedTagKey(key)) {
        if (singleValuedTags.containsKey(key)) {
          tags.remove(singleValuedTags.get(key));
        }
        singleValuedTags.put(key, tag);
      }

      tags.add(tag);

      if (key.equals(COMPONENT_TAG_KEY)) {
        componentTagValue = value.toString();
      }

      // allow span to be reported if sampling.priority is > 0.
      if (Tags.SAMPLING_PRIORITY.getKey().equals(key) && value instanceof Number) {
        int priority = ((Number) value).intValue();
        forceSampling = priority > 0 ? Boolean.TRUE : Boolean.FALSE;
        spanContext = spanContext.withSamplingDecision(forceSampling);
      }

      if (Tags.ERROR.getKey().equals(key)) {
        isError = true;
      }

      // allow span to be reported if error tag is set.
      if (forceSampling == null && Tags.ERROR.getKey().equals(key)) {
        if (value instanceof Boolean && (Boolean) value) {
          forceSampling = Boolean.TRUE;
          spanContext = spanContext.withSamplingDecision(forceSampling);
        }
      }
    }
    return this;
  }

  public boolean isError() {
    return isError;
  }

  @Override
  public WavefrontSpan log(Map<String, ?> map) {
    // no-op
    return this;
  }

  @Override
  public WavefrontSpan log(long l, Map<String, ?> map) {
    // no-op
    return this;
  }

  @Override
  public WavefrontSpan log(String s) {
    // no-op
    return this;
  }

  @Override
  public WavefrontSpan log(long l, String s) {
    // no-op
    return this;
  }

  @Override
  public synchronized WavefrontSpan setBaggageItem(String key, String value) {
    spanContext = spanContext.withBaggageItem(key, value);
    return this;
  }

  @Override
  @Nullable
  public synchronized String getBaggageItem(String key) {
    return this.spanContext.getBaggageItem(key);
  }

  @Override
  public synchronized WavefrontSpan setOperationName(String s) {
    operationName = s;
    return this;
  }

  @Override
  public void finish() {
    if (startTimeNanos != 0) {
      long duration = System.nanoTime() - startTimeNanos;
      doFinish(TimeUnit.NANOSECONDS.toMicros(duration));
    } else {
      // Ideally finish(finishTimeMicros) should be called if user provided startTimeMicros
      finish(tracer.currentTimeMicros());
    }
  }

  @Override
  public void finish(long finishTimeMicros) {
    doFinish(finishTimeMicros-startTimeMicros);
  }

  private void doFinish(long durationMicros) {
    synchronized (this) {
      if (finished) {
        return;
      }
      this.durationMicroseconds = durationMicros;
      finished = true;
    }

    // perform another sampling for duration based samplers
    if (forceSampling == null && (!spanContext.isSampled() || !spanContext.getSamplingDecision())) {
      boolean decision = tracer.sample(operationName,
          spanContext.getTraceId().getLeastSignificantBits(), durationMicros/1000);
      spanContext = decision ? spanContext.withSamplingDecision(decision) : spanContext;
    }

    // only report spans if the sampling decision allows it
    if (spanContext.isSampled() && spanContext.getSamplingDecision()) {
      tracer.reportSpan(this);
    } else if (spansDiscarded != null) {
      spansDiscarded.inc();
    }
    // irrespective of sampling, report wavefront-generated metrics/histograms to Wavefront
    tracer.reportWavefrontGeneratedData(this);
  }

  public synchronized String getOperationName() {
    return operationName;
  }

  public long getStartTimeMicros() {
    return startTimeMicros;
  }

  public synchronized long getDurationMicroseconds() {
    return durationMicroseconds;
  }

  /**
   * Gets the list of multi-valued tags.
   *
   * @return The list of tags.
   */
  public synchronized List<Pair<String, String>> getTagsAsList() {
    if (tags == null) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(tags);
  }

  /**
   * Gets the map of multi-valued tags.
   *
   * @return The map of tags
   */
  public synchronized Map<String, Collection<String>> getTagsAsMap() {
    if (tags == null) {
      return Collections.emptyMap();
    }
    return Collections.unmodifiableMap(
        tags.stream().collect(
            Collectors.groupingBy(
                p -> p._1,
                Collectors.mapping(p -> p._2, Collectors.toList())
            )
        )
    );
  }

  /**
   * Returns the tag value for the given single-valued tag key. Returns null if no such tag exists.
   *
   * @param key The single-valued tag key.
   * @return The tag value.
   */
  public synchronized String getSingleValuedTagValue(String key) {
    if (singleValuedTags.containsKey(key)) {
      return singleValuedTags.get(key)._2;
    }
    return null;
  }

  public List<Reference> getParents() {
    if (parents == null) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(parents);
  }

  public List<Reference> getFollows() {
    if (follows == null) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(follows);
  }

  public String getComponentTagValue() {
    return componentTagValue;
  }

  @Override
  public String toString() {
    return "WavefrontSpan{" +
        "operationName='" + operationName + '\'' +
        ", startTimeMicros=" + startTimeMicros +
        ", durationMicroseconds=" + durationMicroseconds +
        ", tags=" + tags +
        ", spanContext=" + spanContext +
        ", parents=" + parents +
        ", follows=" + follows +
        '}';
  }

  /**
   * Returns a boolean indicated whether the given tag key must be single-valued or not.
   *
   * @param key The tag key.
   * @return true if the key must be single-valued, false otherwise.
   */
  public static boolean isSingleValuedTagKey(String key) {
    return SINGLE_VALUED_TAG_KEYS.contains(key);
  }
}
