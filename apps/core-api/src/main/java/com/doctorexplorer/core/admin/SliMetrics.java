package com.doctorexplorer.core.admin;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * SLI do painel de qualidade (RF13): latência de toda requisição da API num único timer com p95 publicado, e
 * contagem de respostas 5xx para a disponibilidade. Em produção o mesmo vem do OpenTelemetry; aqui é o retrato
 * desta instância desde a subida.
 */
@Component
public class SliMetrics extends OncePerRequestFilter {

    private final Timer latency;
    private final Counter total;
    private final Counter errors;

    /** Sem registro do Actuator (ex.: testes de fatia web), usa um registro local. */
    public SliMetrics(ObjectProvider<MeterRegistry> registries) {
        MeterRegistry registry = registries.getIfAvailable(SimpleMeterRegistry::new);
        this.latency = Timer.builder("de.api.latency").publishPercentiles(0.95).register(registry);
        this.total = Counter.builder("de.api.requests").register(registry);
        this.errors = Counter.builder("de.api.errors5xx").register(registry);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            chain.doFilter(req, res);
        } finally {
            latency.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
            total.increment();
            if (res.getStatus() >= 500) errors.increment();
        }
    }

    public double p95Millis() {
        var snap = latency.takeSnapshot();
        for (var v : snap.percentileValues()) if (v.percentile() == 0.95) return Math.round(v.value(TimeUnit.MILLISECONDS) * 10) / 10.0;
        return 0;
    }

    public double availability() {
        double t = total.count();
        return t == 0 ? 1.0 : Math.round((1 - errors.count() / t) * 100000) / 100000.0;
    }

    public long requests() {
        return (long) total.count();
    }
}
