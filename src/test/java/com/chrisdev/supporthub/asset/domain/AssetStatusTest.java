package com.chrisdev.supporthub.asset.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.chrisdev.supporthub.asset.domain.AssetStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

public class AssetStatusTest {

    private static final Map<AssetStatus, Set<AssetStatus>> EXPECTED = Map.of(
            AVAILABLE, EnumSet.of(ASSIGNED, MAINTENANCE, RETIRED),
            ASSIGNED, EnumSet.of(AVAILABLE),
            MAINTENANCE, EnumSet.of(AVAILABLE, RETIRED),
            RETIRED, EnumSet.noneOf(AssetStatus.class));

    @ParameterizedTest
    @EnumSource(AssetStatus.class)
    void allowedTransitionsMatchTheSpecification(AssetStatus from) {
        assertThat(from.allowedTransitions()).containsExactlyInAnyOrderElementsOf(EXPECTED.get(from));
    }
}
