# Sampling

A cloud-scale web application generates a large number of traces. You can set up sampling strategies in your application to reduce the volume of trace data that it sends to Wavefront.

You set up a sampling strategy by configuring the `WavefrontTracer` builder with an implementation of the `Sampler` interface.

For example, suppose you want to approximately report 1 out of every 5 traces to Wavefront. The following snippet shows how to configure a `WavefrontTracer` with a `RateSampler`:

```java
// Create a WavefrontTracer builder
WavefrontTracer.Builder wfTracerBuilder = ...  

// Create a RateSampler with a rate of 20% and add it to the WavefrontTracer builder
wfTracerBuilder.withSampler(new RateSampler(0.2));

... // (Optional) Further configure the WavefrontTracer builder

// Build the WavefrontTracer
Tracer tracer = wfTracerBuilder.build();
```
## Supported Sampling Strategies

The following table lists the supported sampling strategies. You create and configure a sampling strategy by configuring a `Sampler` implementation:

| Sampler              | Description                            |
| --------------------- | -------------------------------------- |
| ConstantSampler       | Allows either all traces or no traces. Specify `true` to sample all traces, or `false` to sample no traces. |
| DurationSampler       | Allows a span if its duration exceeds a specified threshold. Specify the duration threshold as a number of milliseconds. |
| RateSampler           | Allows a specified probabilistic rate of traces to be reported. Specify the rate of allowed traces as a number between 0.0 and 1.0. |
| CompositeSampler      | Delegates the sampling decision to multiple other samplers and allows a span if any delegate decides to allows it. Specify a list of samplers to delegate. |


**Note:** Regardless of the sampling strategy, the `WavefrontTracer`: 
* Allows all error spans (`error=true` span tag).
* Allows all spans that have a sampling priority greater than 0 (`sampling.priority` span tag).
* Includes all spans in the [RED metrics](https://github.com/wavefrontHQ/wavefront-opentracing-sdk-java/blob/master/README.md#red-metrics) that are automatically collected and reported.

## Using Multiple Sampling Strategies

You can configure a `WavefrontTracer` with multiple sampling strategies. To do so, you use a `CompositeSampler`, which delegates the sampling decision to multiple other samplers and decides to allow a span if any of the delegate samplers decide to allow it. 

For instance, suppose you want to approximately report 10% of traces but you also don't want to lose any spans that are over 60 seconds long. The following code snippet shows how to configure a `WavefrontTracer` with a `RateSampler` and a `DurationSampler`:


```java
// Create a WavefrontTracer builder
WavefrontTracer.Builder wfTracerBuilder = ...  

// Create and configure the RateSampler and DurationSampler
Sampler rateSampler = new RateSampler(0.1);
Sampler durationSampler = new DurationSampler(60_000);

// Create and configure the CompositeSampler with a list of samplers
Sampler compositeSampler = new CompositeSampler(Arrays.asList(rateSampler, durationSampler));

// Add the CompositeSampler to the WavefrontTracer builder
wfTracerBuilder.withSampler(compositeSampler);

... // (Optional) Further configure the WavefrontTracer builder

// Build the WavefrontTracer
Tracer tracer = wfTracerBuilder.build();
```
