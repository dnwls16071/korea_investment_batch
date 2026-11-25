package com.project.korea_investment_batch;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import static org.assertj.core.api.Assertions.assertThat;

class ModuleStructureTest {
    @Test
    void verifiesFeatureOwnershipAndDependencyBoundaries() {
        var modules = ApplicationModules.of(KoreaInvestmentBatchApplication.class);
        assertThat(modules.stream().map(module -> module.getBasePackage().getName().toString()))
                .containsExactlyInAnyOrderElementsOf(java.util.stream.Stream.of("dart", "kis", "operations", "shared")
                        .map(name -> KoreaInvestmentBatchApplication.class.getPackageName() + "." + name).toList());
        modules.verify();
    }
}
