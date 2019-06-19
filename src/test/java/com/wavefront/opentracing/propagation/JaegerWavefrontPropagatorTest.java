package com.wavefront.opentracing.propagation;

import com.wavefront.opentracing.WavefrontSpanContext;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import io.opentracing.propagation.TextMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This class will primarily test inject() and extract() functions of
 * JaegerWavefrontPropagator which is a custom propagator.
 */

public class JaegerWavefrontPropagatorTest {

    private final String jaegerHeader = "uber-trace-id";

    JaegerWavefrontPropagator wfJaegerPropagator = new JaegerWavefrontPropagator.Builder().
            withBaggagePrefix("uberctx-")
            .withTraceIdHeader(jaegerHeader).build();


    @Test
    public void testBuilder()  {
        JaegerWavefrontPropagator codec = new JaegerWavefrontPropagator.Builder()
                .withTraceIdHeader(jaegerHeader)
                .build();
        assertNotNull(codec);
    }

    @Test
    public void testTraceIdExtract() {
        String val = "3871de7e09c53ae8:7499dd16d98ab60e:3771de7e09c55ae8:1";
        DelegatingTextMap headersTextMap = new DelegatingTextMap();
        headersTextMap.put(jaegerHeader, val);
        WavefrontSpanContext ctx = wfJaegerPropagator.extract(headersTextMap);
        assertNotNull(ctx);
        assertEquals(ctx.getTraceId().toString(), "00000000-0000-0000-3871-de7e09c53ae8");
        assertEquals(ctx.getSamplingDecision(), true);
    }

    @Test
    public void testInvalidTraceIdExtract() {
        String val = ":7499dd16d98ab60e:3771de7e09c55ae8:1";
        DelegatingTextMap headersTextMap = new DelegatingTextMap();
        headersTextMap.put(jaegerHeader, val);
        WavefrontSpanContext ctx = wfJaegerPropagator.extract(headersTextMap);
        assertNull(ctx);
    }


    @Test
    public void testTraceIdInject() {
        DelegatingTextMap textMap = new DelegatingTextMap();
        UUID traceId = UUID.fromString("00000000-0000-0000-3871-de7e09c53ae8");
        UUID spanId = UUID.fromString("00000000-0000-0000-7499-dd16d98ab60e");
        wfJaegerPropagator.inject(new WavefrontSpanContext(traceId, spanId, null, true), textMap);
        assertTrue(textMap.containsKey(jaegerHeader));
    }

    @Test
    public void testJaegerIdToWavefrontUuid() {
        String hexStrId = "ef27b4b9f6e946f5ab2b47bbb24746c5";
        UUID out = wfJaegerPropagator.toUuid(hexStrId);
        assertEquals(out.toString(), "ef27b4b9-f6e9-46f5-ab2b-47bbb24746c5");
    }

    @Test
    public void testWavefrontUuidToJaegerIdConversion() {
        UUID in = UUID.randomUUID();
        BigInteger temp = wfJaegerPropagator.uuidToBigInteger(in);
        String hexStr = temp.toString(16);
        UUID out = wfJaegerPropagator.toUuid(hexStr);
        assertEquals(in, out);
    }

    @Test
    public void testJaegerToWavefrontIdConversion() {
        String hexStrIn = "3871de7e09c53ae8";
        UUID in = wfJaegerPropagator.toUuid(hexStrIn);
        BigInteger temp = wfJaegerPropagator.uuidToBigInteger(in);
        String hexStrOut = temp.toString(16);
        assertEquals(hexStrIn, hexStrOut);
    }



    static class DelegatingTextMap implements TextMap {
        final Map<String, String> delegate = new LinkedHashMap<>();

        @Override
        public Iterator<Map.Entry<String, String>> iterator() { return delegate.entrySet().iterator(); }

        @Override
        public void put(String key, String value) { delegate.put(key, value); }

        public boolean containsKey(String key) { return delegate.containsKey(key); }

        public String get(String key) { return delegate.get(key); }
    }
}



