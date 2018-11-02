package com.wavefront.opentracing;

import com.wavefront.opentracing.propagation.Propagator;
import com.wavefront.opentracing.propagation.PropagatorRegistry;
import com.wavefront.opentracing.reporting.Reporter;
import com.wavefront.sdk.common.Pair;
import com.wavefront.sdk.common.application.ApplicationTags;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.util.ThreadLocalScopeManager;

import static com.wavefront.sdk.common.Constants.APPLICATION_TAG_KEY;
import static com.wavefront.sdk.common.Constants.CLUSTER_TAG_KEY;
import static com.wavefront.sdk.common.Constants.NULL_TAG_VAL;
import static com.wavefront.sdk.common.Constants.SERVICE_TAG_KEY;
import static com.wavefront.sdk.common.Constants.SHARD_TAG_KEY;

/**
 * The Wavefront OpenTracing tracer for sending distributed traces to Wavefront.
 *
 * @author Vikram Raman (vikram@wavefront.com)
 */
public class WavefrontTracer implements Tracer, Closeable {

  private static final Logger logger = Logger.getLogger(WavefrontTracer.class.getName());
  private final ScopeManager scopeManager;
  private final PropagatorRegistry registry;
  private final Reporter reporter;
  private final List<Pair<String, String>> tags;

  private WavefrontTracer(Reporter reporter, List<Pair<String, String>> tags) {
    scopeManager = new ThreadLocalScopeManager();
    registry = new PropagatorRegistry();
    this.reporter = reporter;
    this.tags = tags;
    //TODO: figure out sampling
  }

  @Override
  public ScopeManager scopeManager() {
    return scopeManager;
  }

  @Override
  public Span activeSpan() {
    Scope scope = this.scopeManager.active();
    return scope == null ? null : scope.span();
  }

  @Override
  public SpanBuilder buildSpan(String operationName) {
    return new WavefrontSpanBuilder(operationName, this);
  }

  @Override
  public <T> void inject(SpanContext spanContext, Format<T> format, T carrier) {
    Propagator<T> propagator = registry.get(format);
    if (propagator == null) {
      throw new IllegalArgumentException("invalid format: " + format.toString());
    }
    propagator.inject((WavefrontSpanContext) spanContext, carrier);
  }

  @Override
  public <T> SpanContext extract(Format<T> format, T carrier) {
    Propagator<T> propagator = registry.get(format);
    if (propagator == null) {
      throw new IllegalArgumentException("invalid format: " + format.toString());
    }
    return propagator.extract(carrier);
  }

  void reportSpan(WavefrontSpan span) {
    // reporter will flush it to Wavefront/proxy
    try {
      reporter.report(span);
    } catch (IOException ex) {
      logger.log(Level.WARNING, "Error reporting span", ex);
    }
  }

  long currentTimeMicros() {
    return System.currentTimeMillis() * 1000;
  }

  /**
   * Gets the list of global tags to be added to all spans.
   *
   * @return the list of global tags
   */
  List<Pair<String, String>> getTags() {
    return tags;
  }

  /**
   * A builder for {@link WavefrontTracer} instances.
   */
  public static class Builder {
    private final List<Pair<String, String>> tags;
    private final Reporter reporter;
    private final ApplicationTags applicationTags;

    /**
     * Constructor.
     */
    public Builder(Reporter reporter, ApplicationTags applicationTags) {
      this.reporter = reporter;
      this.applicationTags = applicationTags;
      this.tags = new ArrayList<>();
    }

    /**
     * Global tag included with every reported span.
     *
     * @param key the tag key
     * @param value the tag value
     * @return {@code this}
     */
    public Builder withGlobalTag(String key, String value) {
      if (key != null && !key.isEmpty() && value != null && !value.isEmpty()) {
        this.tags.add(Pair.of(key, value));
      }
      return this;
    }

    /**
     * Global tags included with every reported span.
     *
     * @param tags Map of tags
     * @return {@code this}
     */
    public Builder withGlobalTags(Map<String, String> tags) {
      if (tags != null && !tags.isEmpty()) {
        for (Map.Entry<String, String> tag : tags.entrySet()) {
          withGlobalTag(tag.getKey(), tag.getValue());
        }
      }
      return this;
    }

    /**
     * Global multi-valued tags included with every reported span.
     *
     * @param tags Map of multi-valued tags
     * @return {@code this}
     */
    public Builder withGlobalMultiValuedTags(Map<String, Collection<String>> tags) {
      if (tags != null && !tags.isEmpty()) {
        for (Map.Entry<String, Collection<String>> tag : tags.entrySet()) {
          for (String value : tag.getValue()) {
            withGlobalTag(tag.getKey(), value);
          }
        }
      }
      return this;
    }

    /**
     * Apply ApplicationTags as global span tags.
     *
     * @param applicationTags Metadata about your application.
     */
    private void withApplicationTags(ApplicationTags applicationTags) {
      withGlobalTag(APPLICATION_TAG_KEY, applicationTags.getApplication());
      withGlobalTag(SERVICE_TAG_KEY, applicationTags.getService());
      withGlobalTag(CLUSTER_TAG_KEY,
          applicationTags.getCluster() == null ? NULL_TAG_VAL : applicationTags.getCluster());
      withGlobalTag(SHARD_TAG_KEY,
          applicationTags.getShard() == null ? NULL_TAG_VAL : applicationTags.getShard());
      withGlobalTags(applicationTags.getCustomTags());
    }

    /**
     * Builds and returns the WavefrontTracer instance based on the provided configuration.
     *
     * @return a {@link WavefrontTracer}
     */
    public WavefrontTracer build() {
      withApplicationTags(applicationTags);
      return new WavefrontTracer(reporter, tags);
    }
  }

  @Override
  public void close() throws IOException {
    this.reporter.close();
  }
}
