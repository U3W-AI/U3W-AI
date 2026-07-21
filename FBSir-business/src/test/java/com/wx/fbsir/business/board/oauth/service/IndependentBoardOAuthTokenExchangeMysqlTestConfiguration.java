package com.wx.fbsir.business.board.oauth.service;

import com.wx.fbsir.business.board.oauth.BoardOAuthTokenMaterialGenerator;
import com.wx.fbsir.business.board.oauth.mapper.IndependentBoardOAuthMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Test-source-only wiring for the production OAuth token-exchange transaction chain. */
@Configuration(proxyBeanMethods = false)
public class IndependentBoardOAuthTokenExchangeMysqlTestConfiguration {
    public static final String RUNNER_BEAN_NAME =
            "independentBoardOAuthTokenExchangeTransactionRunnerForMysqlIt";

    @Bean
    BoardOAuthTokenMaterialGenerator boardOAuthTokenMaterialGeneratorForMysqlIt() {
        return new BoardOAuthTokenMaterialGenerator();
    }

    @Bean
    SwitchableClock independentBoardOAuthTokenExchangeClockForMysqlIt() {
        return new SwitchableClock();
    }

    @Bean
    IndependentBoardOAuthTokenExchangeService independentBoardOAuthTokenExchangeServiceForMysqlIt(
            IndependentBoardOAuthMapper mapper,
            BoardOAuthTokenExchangeAuthorityPort authorityPort,
            BoardOAuthTokenMaterialGenerator materialGenerator,
            SwitchableClock clock) {
        return new IndependentBoardOAuthTokenExchangeService(
                mapper, authorityPort, materialGenerator, clock);
    }

    @Bean(name = RUNNER_BEAN_NAME)
    IndependentBoardOAuthTokenExchangeTransactionRunner
            independentBoardOAuthTokenExchangeTransactionRunnerForMysqlIt(
                    IndependentBoardOAuthTokenExchangeService service) {
        return new IndependentBoardOAuthTokenExchangeTransactionRunner(service);
    }

    @Bean
    IndependentBoardOAuthTokenExchangeFacade independentBoardOAuthTokenExchangeFacadeForMysqlIt(
            IndependentBoardOAuthTokenExchangeTransactionRunner runner) {
        return new IndependentBoardOAuthTokenExchangeFacade(runner);
    }

    /**
     * Test-source clock that follows the system clock unless a concurrency test
     * explicitly freezes it after observing a real InnoDB row-lock wait.
     */
    public static final class SwitchableClock extends Clock {
        private final Clock systemClock;
        private final AtomicReference<Instant> fixedInstant;
        private final ZoneId zone;

        public SwitchableClock() {
            this(Clock.systemUTC(), new AtomicReference<>(), ZoneOffset.UTC);
        }

        private SwitchableClock(
                Clock systemClock,
                AtomicReference<Instant> fixedInstant,
                ZoneId zone) {
            this.systemClock = Objects.requireNonNull(systemClock, "systemClock");
            this.fixedInstant = Objects.requireNonNull(fixedInstant, "fixedInstant");
            this.zone = Objects.requireNonNull(zone, "zone");
        }

        public void setInstant(Instant instant) {
            fixedInstant.set(Objects.requireNonNull(instant, "instant"));
        }

        public void reset() {
            fixedInstant.set(null);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId requestedZone) {
            ZoneId requiredZone = Objects.requireNonNull(requestedZone, "requestedZone");
            if (zone.equals(requiredZone)) {
                return this;
            }
            return new SwitchableClock(
                    systemClock.withZone(requiredZone), fixedInstant, requiredZone);
        }

        @Override
        public Instant instant() {
            Instant selected = fixedInstant.get();
            return selected == null ? systemClock.instant() : selected;
        }
    }
}
