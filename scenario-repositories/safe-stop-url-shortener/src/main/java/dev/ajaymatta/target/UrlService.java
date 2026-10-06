package dev.ajaymatta.target;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class UrlService {
    private final Map<String,String> targets = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    public String create(String target) {
        for (int attempt=0; attempt<10; attempt++) {
            byte[] bytes = new byte[8]; random.nextBytes(bytes);
            String code = HexFormat.of().formatHex(bytes);
            if (targets.putIfAbsent(code, target) == null) return code;
        }
        throw new IllegalStateException("Code generation exhausted");
    }
    public String resolve(String code) { return targets.get(code); }
}
