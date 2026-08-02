package com.tepeu.controller;

import com.tepeu.agent.hook.ApprovalStore;
import com.tepeu.dto.ApiResponse;
import com.tepeu.dto.ApprovalDecisionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

/** ApprovalController 裁决与校验。 */
class ApprovalControllerTest {

    private ApprovalStore store;
    private ApprovalController controller;

    @BeforeEach
    void setUp() {
        store = new ApprovalStore(0L);
        controller = new ApprovalController(store);
    }

    @Test
    void decide_approve_ok() {
        var pending = store.createPending("sess-1", "run_command", "{\"command\":\"dir\"}");
        ApprovalDecisionRequest req = new ApprovalDecisionRequest();
        req.setDecision("approve");
        ResponseEntity<ApiResponse<?>> res = controller.decide(pending.id(), req);
        assertEquals(200, res.getStatusCode().value());
        assertTrue(store.hasSessionGrant("sess-1", "run_command", "{\"command\":\"dir\"}"));
    }

    @Test
    void decide_invalidDecision_400() {
        var pending = store.createPending("sess-1", "run_command", "{}");
        ApprovalDecisionRequest req = new ApprovalDecisionRequest();
        req.setDecision("maybe");
        assertEquals(400, controller.decide(pending.id(), req).getStatusCode().value());
    }

    @Test
    void decide_missing_404() {
        ApprovalDecisionRequest req = new ApprovalDecisionRequest();
        req.setDecision("deny");
        assertEquals(404, controller.decide("no-such", req).getStatusCode().value());
    }
}
