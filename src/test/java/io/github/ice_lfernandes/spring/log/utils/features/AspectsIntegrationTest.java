package io.github.ice_lfernandes.spring.log.utils.features;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.ice_lfernandes.spring.log.utils.commons.LoggingCommonsMethods;
import io.github.ice_lfernandes.spring.log.utils.features.annotations.HttpMethodLogExecution;
import io.github.ice_lfernandes.spring.log.utils.features.annotations.LogExecution;
import io.github.ice_lfernandes.spring.log.utils.features.aspect.GlobalHttpMethodLogAspect;
import io.github.ice_lfernandes.spring.log.utils.features.aspect.HttpMethodLogExecutionAspect;
import io.github.ice_lfernandes.spring.log.utils.features.aspect.LogExecutionAspect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AspectsIntegrationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    GlobalHttpMethodLogAspect.class,
                    LogExecutionAspect.class,
                    HttpMethodLogExecutionAspect.class))
            .withUserConfiguration(TargetConfig.class);

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(LoggingCommonsMethods.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    private List<String> messages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    void shouldRegisterAspectsAndLogInitAndFinishForLogExecution() {
        runner.run(ctx -> {
            assertEquals(1, ctx.getBeansOfType(LogExecutionAspect.class).size());
            assertEquals(1, ctx.getBeansOfType(HttpMethodLogExecutionAspect.class).size());
            assertEquals("hello john", ctx.getBean(Target.class).annotated("john"));

            var msgs = messages();
            assertEquals(2, msgs.size());
            assertTrue(msgs.get(0).startsWith("stage=init, method=annotated, class=Target, parameters="));
            assertTrue(msgs.get(0).contains("john"));
            assertTrue(msgs.get(1).startsWith("stage=finish, method=annotated, class=Target"));
            assertTrue(msgs.get(1).contains("result=hello john"));
            assertTrue(msgs.get(1).contains("time-execution="));
        });
    }

    @Test
    void shouldOmitReturnWhenLogReturnIsFalse() {
        runner.run(ctx -> {
            ctx.getBean(Target.class).noReturn("secret");
            var finish = messages().get(1);
            assertFalse(finish.contains("result="));
            assertTrue(finish.matches(".*time-execution=\\d+ms"));
            assertTrue(finish.contains("parameters="));
        });
    }

    @Test
    void shouldOmitParametersWhenLogParametersIsFalse() {
        runner.run(ctx -> {
            ctx.getBean(Target.class).noParams("secret");
            var msgs = messages();
            assertFalse(msgs.get(0).contains("secret"));
            assertFalse(msgs.get(1).contains("parameters="));
            assertTrue(msgs.get(1).matches(".*time-execution=\\d+ms"));
            assertTrue(msgs.get(1).contains("result=ok"));
        });
    }

    @Test
    void shouldLogHttpMethodLogExecutionAnnotation() {
        runner.run(ctx -> {
            assertEquals("pong", ctx.getBean(Target.class).customHttp());
            var msgs = messages();
            assertEquals(2, msgs.size());
            assertTrue(msgs.get(1).contains("result=pong"));
        });
    }

    @Test
    void shouldPropagateExceptionWithoutFinishLog() {
        runner.run(ctx -> {
            var target = ctx.getBean(Target.class);
            var ex = assertThrows(IllegalStateException.class, target::fails);
            assertEquals("boom", ex.getMessage());
            assertEquals(1, messages().size());
            assertTrue(messages().get(0).startsWith("stage=init"));
        });
    }

    @Test
    void shouldNotLogUnannotatedMethod() {
        runner.run(ctx -> {
            assertEquals("plain", ctx.getBean(Target.class).plain());
            assertTrue(messages().isEmpty());
        });
    }

    @Test
    void shouldLogSpringHttpMappingMethodsByDefault() {
        runner.run(ctx -> {
            assertEquals(1, ctx.getBeansOfType(GlobalHttpMethodLogAspect.class).size());
            var target = ctx.getBean(Target.class);
            assertEquals("created", target.post());
            assertEquals("fetched", target.get());
            assertEquals(4, messages().size());
        });
    }

    @Test
    void shouldLogHttpMappingWhenPropertyEnabledTrue() {
        runner.withPropertyValues("spring.log-utils.global.httpMethod.enabled=true").run(ctx -> {
            assertEquals(1, ctx.getBeansOfType(GlobalHttpMethodLogAspect.class).size());
            ctx.getBean(Target.class).post();
            assertEquals(2, messages().size());
        });
    }

    @Test
    void shouldNotLogHttpMappingWhenPropertyDisabled() {
        runner.withPropertyValues("spring.log-utils.global.httpMethod.enabled=false").run(ctx -> {
            assertEquals(0, ctx.getBeansOfType(GlobalHttpMethodLogAspect.class).size());
            assertEquals("created", ctx.getBean(Target.class).post());
            assertTrue(messages().isEmpty());
        });
    }

    @Test
    void shouldStillLogExplicitAnnotationsWhenGlobalDisabled() {
        runner.withPropertyValues("spring.log-utils.global.httpMethod.enabled=false").run(ctx -> {
            ctx.getBean(Target.class).annotated("x");
            assertEquals(2, messages().size());
        });
    }

    @Configuration
    @EnableAspectJAutoProxy
    static class TargetConfig {
        @Bean
        Target target() {
            return new Target();
        }
    }

    static class Target {
        @LogExecution
        public String annotated(String name) {
            return "hello " + name;
        }

        @LogExecution(logReturn = false)
        public String noReturn(String value) {
            return "ret-" + value;
        }

        @LogExecution(logParameters = false)
        public String noParams(String value) {
            return "ok";
        }

        @HttpMethodLogExecution
        public String customHttp() {
            return "pong";
        }

        @LogExecution
        public String fails() {
            throw new IllegalStateException("boom");
        }

        public String plain() {
            return "plain";
        }

        @PostMapping
        public String post() {
            return "created";
        }

        @GetMapping
        public String get() {
            return "fetched";
        }
    }
}
