package rocks.inspectit.oce.core.config.model.instrumentation.rules;

import org.assertj.core.util.Maps;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.Spy;
import rocks.inspectit.oce.core.config.model.InspectitConfig;
import rocks.inspectit.oce.core.config.model.instrumentation.InstrumentationSettings;
import rocks.inspectit.oce.core.config.model.instrumentation.InternalSettings;
import rocks.inspectit.oce.core.config.model.instrumentation.SpecialSensorSettings;
import rocks.inspectit.oce.core.config.model.instrumentation.dataproviders.DataProviderCallSettings;
import rocks.inspectit.oce.core.config.model.metrics.MetricsSettings;
import rocks.inspectit.oce.core.config.model.metrics.definition.MetricDefinitionSettings;
import rocks.inspectit.oce.core.config.model.validation.Violation;
import rocks.inspectit.oce.core.config.model.validation.ViolationBuilder;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;


public class InstrumentationRuleSettingsTest {

    InspectitConfig root;

    InstrumentationSettings instr;

    MetricsSettings metrics;

    InstrumentationRuleSettings rule;

    @Spy
    ViolationBuilder vios;

    @BeforeEach
    void setupDefaultSettings() {
        instr = new InstrumentationSettings();
        instr.setSpecial(new SpecialSensorSettings());
        instr.setInternal(new InternalSettings());

        rule = new InstrumentationRuleSettings();
        instr.setRules(Maps.newHashMap("my-rule", rule));

        metrics = new MetricsSettings();

        root = new InspectitConfig();
        root.setInstrumentation(instr);
        root.setMetrics(metrics);
    }


    @Nested
    class PerformValidation {

        @Test
        void testRenamedMetricsFound() {
            MetricDefinitionSettings m1 = MetricDefinitionSettings.builder().build();
            MetricDefinitionSettings m2 =
                    MetricDefinitionSettings.builder()
                            .name("renamed")
                            .build();
            Map<String, MetricDefinitionSettings> metricDefs = new HashMap<>();
            metricDefs.put("m1", m1);
            metricDefs.put("m2", m2);
            metrics.setDefinitions(metricDefs);

            Map<String, String> metricUsages = new HashMap<>();
            metricUsages.put("m1", "42");
            metricUsages.put("renamed", "42");

            rule.setMetrics(metricUsages);

            List<Violation> violations = new ArrayList<>();
            instr.performValidation(root, new ViolationBuilder(violations));

            assertThat(violations).hasSize(0);
        }

        @Test
        void testNonExistingMetric() {
            Map<String, String> metricUsages = new HashMap<>();
            metricUsages.put("m1", "42");

            rule.setMetrics(metricUsages);

            List<Violation> violations = new ArrayList<>();
            instr.performValidation(root, new ViolationBuilder(violations));

            assertThat(violations).hasSize(1);
            assertThat(violations.get(0).getMessage()).containsIgnoringCase("metric");
        }

        @Test
        void testNonExistingScope() {
            rule.setScopes(Collections.singletonMap("my-scope", true));

            List<Violation> violations = new ArrayList<>();
            instr.performValidation(root, new ViolationBuilder(violations));

            assertThat(violations).hasSize(1);
            assertThat(violations.get(0).getMessage()).containsIgnoringCase("scope");
            assertThat(violations.get(0).getMessage()).containsIgnoringCase("exist");
        }

        @Test
        void ensureAllCallsValidated() {
            DataProviderCallSettings entryCall = Mockito.spy(DataProviderCallSettings.class);
            entryCall.setProvider("someProvider");
            doNothing().when(entryCall).performValidation(any(), any());
            DataProviderCallSettings exitCall = Mockito.spy(DataProviderCallSettings.class);
            exitCall.setProvider("someProvider");
            doNothing().when(exitCall).performValidation(any(), any());

            rule.setEntry(Collections.singletonMap("entry_data", entryCall));
            rule.setExit(Collections.singletonMap("exit_data", exitCall));

            List<Violation> violations = new ArrayList<>();
            instr.performValidation(root, new ViolationBuilder(violations));

            assertThat(violations).hasSize(0);
            verify(entryCall, times(1)).performValidation(same(instr), any());
            verify(exitCall, times(1)).performValidation(same(instr), any());
        }
    }

}