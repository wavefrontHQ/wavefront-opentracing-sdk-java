# RED Metrics

In order to provide full [3D Observability](https://www.wavefront.com/wavefront-enhances-application-observability-with-distributed-tracing/) for your application, the `WavefrontTracer` automatically collects and reports RED metrics based off of your tracing spans. There is no additional configuration or instrumentation involved. Once you are reporting traces to Wavefront using the `WavefrontTracer`, you will be able to visualize these out-of-the-box metrics and histograms in Wavefront:

1. Navigate to `Applications -> Inventory` in Wavefront. You will see your application and services auto-populated.
2. Click on the `wavefront-generated` icon for any service. You will see a dashboard that is generated from the RED metrics for your service. This dashboard includes visualizations for Invocation Rate, Error Rate, Duration (P95), Top Operations, Top Failed Operations, and Slowest Operations.

The following RED metrics are collected and reported:

| Entity Name       | Entity Type | Description       |
| ----------------- | ----------- | ----------------- |
| `tracing.derived.<application>.<service>.<operationName>.invocation.count`        | Counter            | The number of times that the operation is invoked. |
| `tracing.derived.<application>.<service>.<operationName>.error.count`             | Counter            | The number of invocations that are errors (i.e., spans with `error=true`). |
| `tracing.derived.<application>.<service>.<operationName>.total_time.millis.count` | Counter            | The total duration of the operation invocations, in milliseconds. |
| `tracing.derived.<application>.<service>.<operationName>.duration.micros.m`       | WavefrontHistogram | The duration of each operation invocation, in microseconds. |
