package com.wavefront.opentracing.propagation;

/**
 * Interface for propagating span contexts over carriers.
 *
 * @author Vikram Raman (vikram@wavefront.com)
 */
public interface Propagator<T> extends Injector<T>, Extractor<T> {
}
