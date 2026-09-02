package com.tepeu.os.bus.memory;

import com.tepeu.os.bus.CapabilityBus;
import com.tepeu.os.bus.local.LocalCapabilityBus;
import com.tepeu.os.bus.conformance.BusConformance;
import com.tepeu.os.conformance.ConformanceCase;
import com.tepeu.os.policy.ApprovalStore;
import com.tepeu.os.policy.memory.InMemoryApprovalStore;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * bus 组件 conformance 桥 — 本模块可独立 {@code mvn -pl bus test}。
 */
class BusPortsTest {

    @TestFactory
    Stream<DynamicTest> busConformance() {
        BusConformance.BusFactory factory = new BusConformance.BusFactory() {
            @Override
            public CapabilityBus newBus() {
                return new LocalCapabilityBus();
            }

            @Override
            public ApprovalStore newApprovalStore() {
                return new InMemoryApprovalStore();
            }
        };
        return toDynamicTests(BusConformance.suite(factory));
    }

    private static Stream<DynamicTest> toDynamicTests(Iterable<ConformanceCase> cases) {
        return StreamSupport.stream(cases.spliterator(), false)
                .map(c -> DynamicTest.dynamicTest(c.displayName(), c::run));
    }
}
