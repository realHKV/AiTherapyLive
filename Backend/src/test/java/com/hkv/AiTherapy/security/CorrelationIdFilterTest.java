package com.hkv.AiTherapy.security;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CorrelationIdFilterTest {
    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void acceptsSafeIdAndRemovesItFromMdcAfterRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "web-client.42");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> observed = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> observed.set(MDC.get("correlationId")));

        assertEquals("web-client.42", observed.get());
        assertEquals("web-client.42", response.getHeader(CorrelationIdFilter.HEADER));
        assertNull(MDC.get("correlationId"));
    }

    @Test
    void replacesUnsafeIdWithGeneratedOpaqueValue() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "bad value\r\nforged=true");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        String generated = response.getHeader(CorrelationIdFilter.HEADER);
        assertNotNull(generated);
        assertEquals(36, generated.length());
        assertTrue(generated.matches("[0-9a-f-]+"));
    }
}
