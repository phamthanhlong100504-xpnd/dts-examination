package dts.com.examination.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class ExaminationTimeConfiguration {
    @Bean public Clock examinationClock() { return Clock.systemUTC(); }
}
