package dev.ajaymatta.agentic.shortener;

import java.util.Set;

/** The generated variant enables only capabilities justified by its approved criteria. */
public final class UrlCapabilities {
    private UrlCapabilities() {}
    private static final Set<String> ENABLED = Set.of("create", "redirect", "alias", "expiry", "inspect", "deactivate", "analytics-total", "analytics-daily", "rate-limit");
    public static final int REDIRECT_STATUS = 302;
    public static boolean enabled(String capability) { return ENABLED.contains(capability); }
}
