package com.videocloud.api;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class PlanControllerTest {
    @Test
    void planPricesAreRepresentedInPaiseAndNonNegative() {
        var plans = new PlanController().listPlans();
        assertThat(plans).hasSize(4);
        assertThat(plans).allSatisfy(plan -> assertThat(plan.monthlyPricePaise()).isGreaterThanOrEqualTo(0));
        assertThat(plans.stream().filter(p -> p.code().equals("STARTER")).findFirst().orElseThrow()
            .monthlyPricePaise()).isEqualTo(49_900L);
    }
}
