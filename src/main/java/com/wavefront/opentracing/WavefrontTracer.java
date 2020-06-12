package com.wavefront.opentracing;

import com.wavefront.internal.reporter.WavefrontInternalReporter;
import com.wavefront.opentracing.propagation.Propagator;
import com.wavefront.opentracing.propagation.PropagatorRegistry;
import com.wavefront.opentracing.reporting.CompositeReporter;
import com.wavefront.opentracing.reporting.Reporter;
import com.wavefront.opentracing.reporting.WavefrontSpanReporter;
import com.wavefront.sdk.appagent.jvm.reporter.WavefrontJvmReporter;
import com.wavefront.sdk.common.Pair;
import com.wavefront.sdk.common.application.ApplicationTags;
import com.wavefront.sdk.common.application.HeartbeaterService;
import com.wavefront.sdk.entities.tracing.sampling.Sampler;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import com.wavefront.internal.SpanDerivedMetricsUtils;

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
import static io.opentracing.tag.Tags.SPAN_KIND;

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
  private final List<Sampler> samplers;

  @Nullable
  private final WavefrontSpanReporter wfSpanReporter;
  @Nullable
  private final WavefrontInternalReporter wfInternalReporter;
  @Nullable
  private final WavefrontInternalReporter wfDerivedReporter;
  @Nullable
  private final HeartbeaterService heartbeaterService;
  @Nullable
  private final WavefrontJvmReporter wfJvmReporter;
  private final Supplier<Long> reportFrequencyMillis;
  private final ApplicationTags applicationTags;
  private final Set<String> redMetricsCustomTagKeys;

  private final static Pattern WHITESPACE = Pattern.compile("[\\s]+");

  private final static String WAVEFRONT_GENERATED_COMPONENT = "wavefront-generated";
  private final static String OPENTRACING_COMPONENT = "opentracing";
  private final static String JAVA_COMPONENT = "java";

  private WavefrontTracer(Builder builder) {
    scopeManager = builder.scopeManager;
    this.registry = builder.registry;
    this.reporter = builder.reporter;
    this.tags = builder.tags;
    this.samplers = builder.samplers;
    this.applicationTags = builder.applicationTags;
    this.reportFrequencyMillis = builder.reportingFrequencyMillis;
    this.redMetricsCustomTagKeys = builder.redMetricsCustomTagKeys;

    /**
     * Tracing spans will be converted to metrics and histograms and will be reported to Wavefront
     * only if you use the WavefrontSpanReporter
     */
    wfSpanReporter = getWavefrontSpanReporter(reporter);
    if (wfSpanReporter != null) {
      Tuple tuple = instantiateWavefrontStatsReporter(wfSpanReporter, builder.applicationTags,
          builder.includeJvmMetrics);
      wfInternalReporter = tuple.wfInternalReporter;
      wfDerivedReporter = tuple.wfDerivedReporter;
      wfJvmReporter = tuple.wfJvmReporter;
      heartbeaterService = tuple.heartbeaterService;
      wfSpanReporter.setMetricsReporter(wfInternalReporter);
    } else {
      wfInternalReporter = null;
      wfDerivedReporter = null;
      wfJvmReporter = null;
      heartbeaterService = null;
    }
  }

  @Nullable
  private WavefrontSpanReporter getWavefrontSpanReporter(Reporter reporter) {
    if (reporter instanceof WavefrontSpanReporter) {
      return (WavefrontSpanReporter) reporter;
    }

    if (reporter instanceof CompositeReporter) {
      CompositeReporter compositeReporter = (CompositeReporter) reporter;
      for (Reporter item : compositeReporter.getReporters()) {
        if (item instanceof WavefrontSpanReporter) {
          return (WavefrontSpanReporter) item;
        }
      }
    }

    // default
    return null;
  }

  private class Tuple {
    WavefrontInternalReporter wfInternalReporter;
    WavefrontInternalReporter wfDerivedReporter;
    @Nullable
    WavefrontJvmReporter wfJvmReporter;
    HeartbeaterService heartbeaterService;

    Tuple(WavefrontInternalReporter wfInternalReporter,
          WavefrontInternalReporter wfDerivedReporter,
          WavefrontJvmReporter wfJvmReporter,
          HeartbeaterService heartbeaterService) {
      this.wfInternalReporter = wfInternalReporter;
      this.wfDerivedReporter = wfDerivedReporter;
      this.wfJvmReporter = wfJvmReporter;
      this.heartbeaterService = heartbeaterService;
    }
  }

  private Tuple instantiateWavefrontStatsReporter(
      WavefrontSpanReporter wfSpanReporter, ApplicationTags applicationTags,
      boolean includeJvmMetrics) {
    Map<String, String> pointTags = new HashMap<>(applicationTags.toPointTags());

    WavefrontInternalReporter wfInternalReporter = new WavefrontInternalReporter.Builder().
        prefixedWith("~sdk.java.opentracing").withSource(wfSpanReporter.getSource()).
        withReporterPointTags(pointTags).
        build(wfSpanReporter.getWavefrontSender());
    // Start the internal metrics reporter
    wfInternalReporter.start(1, TimeUnit.MINUTES);

    WavefrontInternalReporter wfDerivedReporter = new WavefrontInternalReporter.Builder().
        prefixedWith("tracing.derived").withSource(wfSpanReporter.getSource()).
        withReporterPointTags(pointTags).reportMinuteDistribution().
        build(wfSpanReporter.getWavefrontSender());
    // Start the derived metrics reporter
    wfDerivedReporter.start(reportFrequencyMillis.get(), TimeUnit.MILLISECONDS);

    WavefrontJvmReporter wfJvmReporter = null;
    if (includeJvmMetrics) {
      wfJvmReporter = new WavefrontJvmReporter.Builder(applicationTags).
          withSource(wfSpanReporter.getSource()).build(wfSpanReporter.getWavefrontSender());
      // Start the JVM reporter
      wfJvmReporter.start();
    }

    HeartbeaterService heartbeaterService = new HeartbeaterService(
        wfSpanReporter.getWavefrontSender(), applicationTags,
        Arrays.asList(WAVEFRONT_GENERATED_COMPONENT, OPENTRACING_COMPONENT, JAVA_COMPONENT),
        wfSpanReporter.getSource());
    return new Tuple(wfInternalReporter, wfDerivedReporter, wfJvmReporter, heartbeaterService);
  }

  @Nullable
  WavefrontInternalReporter getWfInternalReporter() {
    return wfInternalReporter;
  }

  @Override
  public ScopeManager scopeManager() {
    return scopeManager;
  }

  @Override
  public Span activeSpan() {
    return scopeManager.activeSpan();
  }

  @Override
  public Scope activateSpan(Span span) {
    return this.scopeManager.activate(span);
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

  boolean sample(String operationName, long traceId, long duration) {
    return sample(operationName, traceId, duration, true);
  }

  boolean sample(String operationName, long traceId, long duration, boolean defaultValue) {
    if (samplers == null || samplers.isEmpty()) {
      return defaultValue;
    }
    boolean earlySampling = (duration == 0);
    for (Sampler sampler : samplers) {
      boolean doSample = earlySampling == sampler.isEarly();
      if (doSample && sampler.sample(operationName, traceId, duration)) {
        if (logger.isLoggable(Level.FINER)) {
          logger.finer(sampler.getClass().getSimpleName() + "=" + true +
              " op=" + operationName);
        }
        return true;
      }
      if (logger.isLoggable(Level.FINER)) {
        logger.finer(sampler.getClass().getSimpleName() + "=" + false +
            " op=" + operationName);
      }
    }
    return false;
  }

  void reportWavefrontGeneratedData(WavefrontSpan span) {
    if (wfSpanReporter == null || wfDerivedReporter == null) {
      // WavefrontSpanReporter not set, so no tracing spans will be reported as metrics/histograms.
      return;
    }

    Pair<Map<String, String>, String> heartbeatMetricKey =
        SpanDerivedMetricsUtils.reportWavefrontGeneratedData(
            wfDerivedReporter,
            span.getOperationName(),
            getSingleValuedTagValueOrDefault(span, APPLICATION_TAG_KEY, applicationTags.getApplication()),
            getSingleValuedTagValueOrDefault(span, SERVICE_TAG_KEY, applicationTags.getService()),
            getSingleValuedTagValueOrDefault(span, CLUSTER_TAG_KEY, applicationTags.getCluster()),
            getSingleValuedTagValueOrDefault(span, SHARD_TAG_KEY, applicationTags.getCluster()),
            wfSpanReporter.getSource(),
            span.getComponentTagValue(),
            span.isError(),
            span.getDurationMicroseconds(),
            redMetricsCustomTagKeys,
            span.getTagsAsList()
        );
    if (heartbeaterService != null) {
      heartbeaterService.reportCustomTags(heartbeatMetricKey._1);
    }
  }

  private String getSingleValuedTagValueOrDefault(WavefrontSpan span, String key,
                                                  String defaultValue) {
    String spanTagValue = span.getSingleValuedTagValue(key);
    return spanTagValue == null ? defaultValue : spanTagValue;
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
    // tags can be repeated and include high-cardinality tags
    private final List<Pair<String, String>> tags;
    private final Reporter reporter;
    private ScopeManager scopeManager = new ThreadLocalScopeManager();
    // application metadata, will not have repeated tags and will be low cardinality tags
    private final ApplicationTags applicationTags;
    private final List<Sampler> samplers;
    // Default to 1min
    private Supplier<Long> reportingFrequencyMillis = () -> 60000L;
    private final Set<String> redMetricsCustomTagKeys = new HashSet<>();
    private boolean includeJvmMetrics = true;
    private final PropagatorRegistry registry = new PropagatorRegistry();

    /**
     * Constructor.
     */
    public Builder(Reporter reporter, ApplicationTags applicationTags) {
      this.reporter = reporter;
      this.applicationTags = applicationTags;
      this.tags = new ArrayList<>();
      this.samplers = new ArrayList<>();
    }

    /**
     * Global tag included with every reported span.
     *
     * @param key   the tag key
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
     */
    private void applyApplicationTags() {
      withGlobalTag(APPLICATION_TAG_KEY, applicationTags.getApplication());
      withGlobalTag(SERVICE_TAG_KEY, applicationTags.getService());
      withGlobalTag(CLUSTER_TAG_KEY,
          applicationTags.getCluster() == null ? NULL_TAG_VAL : applicationTags.getCluster());
      withGlobalTag(SHARD_TAG_KEY,
          applicationTags.getShard() == null ? NULL_TAG_VAL : applicationTags.getShard());
      withGlobalTags(applicationTags.getCustomTags());
    }

    /**
     * Sampler for sampling traces.
     *
     * Samplers can be chained by calling this method multiple times. Sampling decisions are OR'd
     * when multiple samplers are used.
     *
     * @return {@code this}
     */
    public Builder withSampler(Sampler sampler) {
      this.samplers.add(sampler);
      return this;
    }

    /**
     * Scope manager to use for span management.
     *
     * @return {@code this}
     */
    public Builder withScopeManager(ScopeManager scopeManager) {
      this.scopeManager = scopeManager;
      return this;
    }

    /**
     * Invoke this method if you already are publishing JVM metrics from your app to Wavefront.
     *
     * @return {@code this}
     */
    public Builder excludeJvmMetrics() {
      includeJvmMetrics = false;
      return this;
    }

    /**
     * Register custom propagator to support various formats.
     *
     * @param format     {@link Format}
     * @param propagator {@link Propagator}
     * @param <T>        describes format type
     * @return {@code this}
     */
    public <T> Builder registerPropagator(Format<T> format, Propagator<T> propagator) {
      this.registry.register(format, propagator);
      return this;
    }

    /**
     * Set custom RED metrics tags. If the span has any of the tags, then those get reported to the
     * span generated RED metrics. span.kind tag will be promoted by default. Example - If you have
     * a span tag of 'tenant-id', that you also want to be propagated to the RED metrics then you
     * would call this method and pass in 'tenant-id' to the set. Caveat - ensure that
     * redMetricsCustomTagKeys are low cardinality tags.
     *
     * @param redMetricsCustomTagKeys set of custom tags you want to report for the span-generated
     *                                RED metrics.
     * @return {@code this}
     */
    public Builder redMetricsCustomTagKeys(Set<String> redMetricsCustomTagKeys) {
      this.redMetricsCustomTagKeys.addAll(redMetricsCustomTagKeys);
      return this;
    }

    /**
     * Visible for testing only.
     *
     * @param reportFrequenceMillis how frequently you want to report data to Wavefront.
     * @return {@code this}
     */
    Builder setReportFrequenceMillis(long reportFrequenceMillis) {
      this.reportingFrequencyMillis = () -> reportFrequenceMillis;
      return this;
    }

    /**
     * Builds and returns the WavefrontTracer instance based on the provided configuration.
     *
     * @return a {@link WavefrontTracer}
     */
    public WavefrontTracer build() {
      applyApplicationTags();
      this.redMetricsCustomTagKeys.add(SPAN_KIND.getKey());
      return new WavefrontTracer(this);
    }
  }

  @Override
  public void close() {
    this.flush();
    try {
      this.reporter.close();
    } catch (IOException e) {
      logger.log(Level.WARNING, "Error closing reporter", e);
    }

    if (wfInternalReporter != null) {
      wfInternalReporter.stop();
    }
    if (wfDerivedReporter != null) {
      wfDerivedReporter.stop();
    }
    if (wfJvmReporter != null) {
      wfJvmReporter.stop();
    }
    if (heartbeaterService != null) {
      heartbeaterService.close();
    }
  }

  /**
   * Flush data inside reporters.
   */
  public void flush() {
    this.reporter.flush();

    if (wfInternalReporter != null) {
      wfInternalReporter.report();
    }

    if (wfDerivedReporter != null) {
      wfDerivedReporter.report();
    }

    if (wfJvmReporter != null) {
      wfJvmReporter.report();
    }
  }
}
