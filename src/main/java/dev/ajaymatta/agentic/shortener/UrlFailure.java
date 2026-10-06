package dev.ajaymatta.agentic.shortener;

public class UrlFailure extends RuntimeException {
    private final int status;
    public UrlFailure(int status,String message) { super(message); this.status=status; }
    public int status() { return status; }
}
