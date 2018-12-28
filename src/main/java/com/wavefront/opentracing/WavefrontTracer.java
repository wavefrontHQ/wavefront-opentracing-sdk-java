package com.wavefront.opentracing;

import com.wavefront.internal.reporter.WavefrontInternalReporter;
import com.wavefront.internal_reporter_java.io.dropwizard.metrics5.MetricName;
import com.wavefront.opentracing.propagation.Propagator;
import com.wavefront.opentracing.propagation.PropagatorRegistry;
import com.wavefront.opentracing.reporting.CompositeReporter;
import com.wavefront.opentracing.reporting.Reporter;
import com.wavefront.opentracing.reporting.WavefrontSpanReporter;
import com.wavefront.sdk.common.Pair;
import com.wavefront.sdk.common.application.ApplicationTags;
import com.wavefront.sdk.common.application.HeartbeaterService;
import com.wavefront.sdk.entities.tracing.sampling.Sampler;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

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
  private final List<Sampler> samplers;
  @Nullable
  private final WavefrontInternalReporter wfInternalReporter;
  @Nullable
  private final HeartbeaterService heartbeaterService;
  private final static String WAVEFRONT_GENERATED_COMPONENT = "wavefront-generated";
  private final static String INVOCATION_SUFFIX = ".invocation";
  private final static String ERROR_SUFFIX = ".error";
  private final static String DURATION_SUFFIX = ".duration.micros";
  private final static String OPERATION_NAME_TAG = "operationName";
  private final String applicationServicePrefix;

  private WavefrontTracer(Reporter reporter, List<Pair<String, String>> tags,
                          ApplicationTags applicationTags, List<Sampler> samplers) {
    scopeManager = new ThreadLocalScopeManager();
    registry = new PropagatorRegistry();
    this.reporter = reporter;
    this.tags = tags;
    this.samplers = samplers;
    applicationServicePrefix = applicationTags.getApplication() + "." +
        applicationTags.getService() + ".";

    /**
     * Tracing spans will be converted to metrics and histograms and will be reported to Wavefront
     * only if you use the WavefrontSpanReporter
     */
    if (reporter instanceof WavefrontSpanReporter) {
      Pair<WavefrontInternalReporter, HeartbeaterService> pair =
          instantiateWavefrontMetricsHistogramsReporter((WavefrontSpanReporter) reporter,
              applicationTags);
      wfInternalReporter = pair._1;
      heartbeaterService = pair._2;
    } else if (reporter instanceof CompositeReporter) {
      CompositeReporter compositeReporter = (CompositeReporter) reporter;
      Pair<WavefrontInternalReporter, HeartbeaterService> tmp = null;
      for (Reporter item : compositeReporter.getReporters()) {
        if (item instanceof WavefrontSpanReporter) {
          tmp = instantiateWavefrontMetricsHistogramsReporter((WavefrontSpanReporter) item,
              applicationTags);
          // only one item from the list if WavefrontSpanReporter
          break;
        }
      }
      if (tmp != null) {
        wfInternalReporter = tmp._1;
        heartbeaterService = tmp._2;
      } else {
        wfInternalReporter = null;
        heartbeaterService = null;
      }
    } else {
      wfInternalReporter = null;
      heartbeaterService = null;
    }
  }

  private Pair<WavefrontInternalReporter, HeartbeaterService>
  instantiateWavefrontMetricsHistogramsReporter(WavefrontSpanReporter wfSpanReporter,
                                                ApplicationTags applicationTags) {
    // TODO - this helper method should go in Tier 1 SDK
    Map<String, String> pointTags = new HashMap<>();
    pointTags.put(APPLICATION_TAG_KEY, applicationTags.getApplication());
    pointTags.put(SERVICE_TAG_KEY, applicationTags.getService());
    pointTags.put(CLUSTER_TAG_KEY,
        applicationTags.getCluster() == null ? NULL_TAG_VAL : applicationTags.getCluster());
    pointTags.put(SHARD_TAG_KEY,
        applicationTags.getShard() == null ? NULL_TAG_VAL : applicationTags.getShard());
    if (applicationTags.getCustomTags() != null) {
      pointTags.putAll(applicationTags.getCustomTags());
    }

    WavefrontInternalReporter wfInternalReporter = new WavefrontInternalReporter.Builder().
        prefixedWith("tracing.span.wavefront-generated").withSource(wfSpanReporter.getSource()).
        withReporterPointTags(pointTags).reportMinuteDistribution().
            build(wfSpanReporter.getWavefrontSender());
    // Start the reporter
    wfInternalReporter.start(1, TimeUnit.MINUTES);

    HeartbeaterService heartbeaterService = new HeartbeaterService(
        wfSpanReporter.getWavefrontSender(), applicationTags, WAVEFRONT_GENERATED_COMPONENT,
        wfSpanReporter.getSource());
    return Pair.of(wfInternalReporter, heartbeaterService);
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

  boolean sample(String operationName, long traceId, long duration) {
    if (samplers == null || samplers.isEmpty()) {
      return true;
    }
    boolean earlySampling = (duration == 0);
    for (Sampler sampler : samplers) {
      boolean doSample = earlySampling == sampler.isEarly();
      if (doSample && sampler.sample(operationName, traceId, duration)) {
        if (logger.isLoggable(Level.FINER)) {
          logger.finer(sampler.getClass().getSimpleName() + "=" + true + " op=" + operationName);
        }
        return true;
      }
      if (logger.isLoggable(Level.FINER)) {
        logger.finer(sampler.getClass().getSimpleName() + "=" + false + " op=" + operationName);
      }
    }
    return false;
  }

  void reportWavefrontGeneratedData(WavefrontSpan span) {
    if (wfInternalReporter == null) {
      // WavefrontSpanReporter not set, so no tracing spans will be reported as metrics/histograms.
      return;
    }
    // Need to sanitize metric name as application, service and operation names can have spaces
    // and other invalid metric name characters
    Map<String, String> pointTags = new HashMap<String, String>() {{
      put(OPERATION_NAME_TAG, span.getOperationName());
    }};
    wfInternalReporter.newCounter(new MetricName(sanitize(applicationServicePrefix +
        span.getOperationName() + INVOCATION_SUFFIX), pointTags)).inc();
    if (span.isError()) {
      wfInternalReporter.newCounter(new MetricName(sanitize(applicationServicePrefix +
          span.getOperationName() + ERROR_SUFFIX), pointTags)).inc();
    }
    // Support duration in microseconds instead of milliseconds
    wfInternalReporter.newWavefrontHistogram(new MetricName(sanitize(applicationServicePrefix +
        span.getOperationName() + DURATION_SUFFIX), pointTags)).
        update(span.getDurationMicroseconds());
  }

  private String sanitize(String s) {
    Pattern WHITESPACE = Pattern.compile("[\\s]+");
    final String whitespaceSanitized = WHITESPACE.matcher(s).replaceAll("-");
    if (s.contains("\"") || s.contains("'")) {
      // for single quotes, once we are double-quoted, single quotes can exist happily inside it.
      return whitespaceSanitized.replaceAll("\"", "\\\\\"");
    } else {
      return whitespaceSanitized;
    }
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
    // application metadata, will not have repeated tags and will be low cardinality tags
    private final ApplicationTags applicationTags;
    private final List<Sampler> samplers;

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
     * Samplers can be chained by calling this method multiple times. Sampling decisions are
     * OR'd when multiple samplers are used.
     *
     * @param sampler
     * @return {@code this}
     */
    public Builder withSampler(Sampler sampler) {
      this.samplers.add(sampler);
      return this;
    }

    /**
     * Builds and returns the WavefrontTracer instance based on the provided configuration.
     *
     * @return a {@link WavefrontTracer}
     */
    public WavefrontTracer build() {
      applyApplicationTags();
      return new WavefrontTracer(reporter, tags, applicationTags, samplers);
    }
  }

  @Override
  public void close() throws IOException {
    this.reporter.close();
    if (wfInternalReporter != null) {
      wfInternalReporter.stop();
    }
    if (heartbeaterService != null) {
      heartbeaterService.close();
    }
  }
}
