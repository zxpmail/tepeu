package com.tepeu.os.policy.memory;

import com.tepeu.os.conformance.ConformanceCase;
import com.tepeu.os.policy.conformance.ApprovalConformance;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;
import java.util.stream.StreamSupport;

class PolicyPortsTest {

    @TestFactory
    Stream<DynamicTest> approvalConformance() {
        return StreamSupport.stream(ApprovalConformance.suite(InMemoryApprovalStore::new).spliterator(), false)
                .map(c -> DynamicTest.dynamicTest(c.displayName(), c::run));
    }
}
