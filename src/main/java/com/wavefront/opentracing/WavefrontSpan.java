package com.wavefront.opentracing;

import com.wavefront.sdk.common.Pair;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

import io.opentracing.Span;

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
  private final List<Reference> parents;
  private final List<Reference> follows;

  private String operationName;
  private long durationMicroseconds;
  private WavefrontSpanContext spanContext;
  private boolean finished = false;

  WavefrontSpan(WavefrontTracer tracer, String operationName, WavefrontSpanContext spanContext,
                long startTimeMicros, long startTimeNanos, List<Reference> parents,
                List<Reference> follows, List<Pair<String, String>> tags) {
    this.tracer = tracer;
    this.operationName = operationName;
    this.spanContext = spanContext;
    this.startTimeMicros = startTimeMicros;
    this.startTimeNanos = startTimeNanos;
    this.parents = parents;
    this.follows = follows;
    this.tags = tags;
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
      tags.add(Pair.of(key, value.toString()));
    }
    return this;
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
    tracer.reportSpan(this);
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
}
