package com.wavefront.opentracing;

import com.wavefront.sdk.common.application.ApplicationTags;

/**
 * Utils class for various test methods to leverage.
 *
 * @author Sushant Dewan (sushant@wavefront.com).
 */
public class Utils {
  public static ApplicationTags buildApplicationTags() {
    return new ApplicationTags.Builder("myApplication", "myService").build();
  }
}
