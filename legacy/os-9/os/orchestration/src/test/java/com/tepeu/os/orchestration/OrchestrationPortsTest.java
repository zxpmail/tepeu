package com.tepeu.os.orchestration;

import com.tepeu.os.conformance.ConformanceCase;
import com.tepeu.os.orchestration.conformance.CommandConformance;
import com.tepeu.os.orchestration.conformance.PromptConformance;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

class OrchestrationPortsTest {

    @TestFactory
    Stream<DynamicTest> promptAndCommand() {
        List<ConformanceCase> cases = new ArrayList<>();
        PromptConformance.suite().forEach(cases::add);
        CommandConformance.suite().forEach(cases::add);
        return StreamSupport.stream(cases.spliterator(), false)
                .map(c -> DynamicTest.dynamicTest(c.displayName(), c::run));
    }
}
