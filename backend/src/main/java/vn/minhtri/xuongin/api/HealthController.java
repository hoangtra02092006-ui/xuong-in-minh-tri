package vn.minhtri.xuongin.api;

import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lets workers see whether the backend is up and when it last restarted (in-memory jobs are lost on restart). */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private static final long MB = 1024 * 1024;
    private final Instant startedAt = Instant.now();

    @GetMapping
    public Map<String, Object> health() {
        Runtime rt = Runtime.getRuntime();
        return Map.of(
                "status", "UP",
                "startedAt", startedAt.toString(),
                "heapUsedMb", (rt.totalMemory() - rt.freeMemory()) / MB,
                "heapMaxMb", rt.maxMemory() / MB);
    }
}
