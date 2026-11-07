package io.github.ice_lfernandes.spring.log.utils;

import io.github.ice_lfernandes.spring.log.utils.features.aspect.GlobalHttpMethodLogAspect;
import io.github.ice_lfernandes.spring.log.utils.features.aspect.HttpMethodLogExecutionAspect;
import io.github.ice_lfernandes.spring.log.utils.features.aspect.LogExecutionAspect;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoConfigurationImportsTest {

    @Test
    void shouldRegisterAllAspectsInAutoConfigurationImports() {
        List<String> candidates = ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader())
                .getCandidates();
        assertTrue(candidates.contains(GlobalHttpMethodLogAspect.class.getName()));
        assertTrue(candidates.contains(LogExecutionAspect.class.getName()));
        assertTrue(candidates.contains(HttpMethodLogExecutionAspect.class.getName()));
    }
}
