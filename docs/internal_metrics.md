# Internal Diagnostic Metrics

This SDK automatically collects a set of diagnostic metrics that allow you to monitor your `WavefrontTracer` instance. These metrics are collected once every minute and are reported to Wavefront using your `WavefrontSpanReporter` instance.

The list of the diagnostic metrics that are collected are given below:

|Metric Name|Metric Type|Description|
|:---|:---:|:---|
|~sdk.java.opentracing.reporter.queue.size                  |Gauge      |Spans in the in-memory reporting buffer.|
|~sdk.java.opentracing.reporter.queue.remaining_capacity    |Gauge      |Remaining capacity of the in-memory reporting buffer.|
|~sdk.java.opentracing.reporter.spans.received.count        |Delta Counter    |Spans received by the reporter.|
|~sdk.java.opentracing.reporter.spans.dropped.count         |Delta Counter    |Spans dropped during reporting.|
|~sdk.java.opentracing.reporter.errors.count                |Delta Counter    |Exceptions encountered while reporting spans.|
|~sdk.java.opentracing.spans.discarded.count                |Delta Counter    |Spans that are discarded as a result of sampling.|

The above metrics are reported with the same source and application tags that are specified for your `WavefrontTracer` and `WavefrontSpanReporter`.

For information regarding diagnostic metrics for your `WavefrontSender` instance, [see RED metrics](https://github.com/wavefrontHQ/wavefront-sdk-doc-sources/blob/master/common/metrics.md#red-metrics).
