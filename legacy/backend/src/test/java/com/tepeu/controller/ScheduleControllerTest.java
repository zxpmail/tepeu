package com.tepeu.controller;

import com.tepeu.dto.ScheduleRequest;
import com.tepeu.model.AgentSchedule;
import com.tepeu.service.ScheduleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** ScheduleController 参数校验与状态码。 */
class ScheduleControllerTest {

    private ScheduleService scheduleService;
    private ScheduleController controller;

    @BeforeEach
    void setUp() {
        scheduleService = mock(ScheduleService.class);
        controller = new ScheduleController(scheduleService);
    }

    @Test
    void create_unknownProvider_returns400() {
        when(scheduleService.create(any(), any(), any(), any(), anyInt(), anyBoolean()))
                .thenThrow(new IllegalArgumentException("Unknown providerId: nope"));
        ScheduleRequest req = new ScheduleRequest();
        req.setWorkspaceId("ws-1");
        req.setName("t");
        req.setPrompt("p");
        req.setProviderId("nope");
        req.setIntervalMinutes(10);

        ResponseEntity<?> res = controller.create(req);
        assertEquals(400, res.getStatusCode().value());
    }

    @Test
    void runNow_conflict_returns409() {
        when(scheduleService.runNow("sch-1"))
                .thenThrow(new IllegalStateException("Schedule is already running"));
        ResponseEntity<?> res = controller.runNow("sch-1");
        assertEquals(409, res.getStatusCode().value());
    }

    @Test
    void get_missing_returns404() {
        when(scheduleService.get("x")).thenReturn(Optional.empty());
        assertEquals(404, controller.get("x").getStatusCode().value());
    }

    @Test
    void list_ok() {
        when(scheduleService.list("ws-1")).thenReturn(List.of(new AgentSchedule()));
        assertEquals(200, controller.list("ws-1").getStatusCode().value());
    }
}
