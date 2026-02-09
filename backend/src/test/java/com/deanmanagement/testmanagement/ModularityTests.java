package com.deanmanagement.testmanagement;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {

    @Test
    void verifyModularStructure() {
        ApplicationModules.of(TestmanagementApplication.class).verify();
    }
}
