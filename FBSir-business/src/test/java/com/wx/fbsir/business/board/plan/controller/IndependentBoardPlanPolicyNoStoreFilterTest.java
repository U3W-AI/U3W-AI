package com.wx.fbsir.business.board.plan.controller;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertTrue;

class IndependentBoardPlanPolicyNoStoreFilterTest {
    @Test
    void writesNoStoreForCandidatePathWithTrailingSlash() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/business/independent-board/plan-policy-revisions/");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new IndependentBoardPlanPolicyNoStoreFilter().doFilter(
                request, response, new MockFilterChain());

        assertTrue(response.getHeader("Cache-Control").contains("no-store"));
    }

    @Test
    void writesNoStoreForOperationAuditRead() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/business/independent-board/operations/");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new IndependentBoardPlanPolicyNoStoreFilter().doFilter(
                request, response, new MockFilterChain());

        assertTrue(response.getHeader("Cache-Control").contains("no-store"));
    }
}
