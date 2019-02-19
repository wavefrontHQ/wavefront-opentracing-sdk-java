package com.wavefront.opentracing;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Cached TraceInfo that stores the earliest span start time and latest span end time to
 * calculate the entire trace duration. Also, atomically report error only once if at least one
 * span in the given trace results in an error. TraceInfo supports multiple root spans.
 *
 * @author Sushant Dewan (sushant@wavefront.com).
 */
public class TraceInfo {
  final AtomicBoolean isError;
  // multiple roots possible
  private final List<String> roots = new ArrayList<>();
  private long startTimeMicros;
  private long finishTimeMicros;
  public TraceInfo() {
    this.isError = new AtomicBoolean(false);
    this.startTimeMicros = Long.MAX_VALUE;
    this.finishTimeMicros = Long.MIN_VALUE;
  }

  synchronized void addRoot(String root) {
    roots.add(root);
  }

  List<String> getRoots() {
    return roots;
  }

  synchronized void setStartAndFinishTime(long newStartTimeMicros, long newFinishTimeMicros) {
    startTimeMicros = Math.min(startTimeMicros, newStartTimeMicros);
    finishTimeMicros = Math.max(finishTimeMicros, newFinishTimeMicros);
  }

  long getStartTimeMicros() {
    return startTimeMicros;
  }

  long getFinishTimeMicros() {
    return finishTimeMicros;
  }
}
