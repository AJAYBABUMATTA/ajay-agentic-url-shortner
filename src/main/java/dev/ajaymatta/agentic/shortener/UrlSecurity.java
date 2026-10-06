package dev.ajaymatta.agentic.shortener;

import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.stereotype.Component;

@Component
public class UrlSecurity implements AutoCloseable {
    private final DnsResolver resolver;
    private final ThreadPoolExecutor dns = new ThreadPoolExecutor(2,8,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(32),
            Thread.ofPlatform().daemon().name("shortener-dns-",0).factory(),new ThreadPoolExecutor.AbortPolicy());
    public UrlSecurity(DnsResolver resolver) { this.resolver=resolver; }
    public String validate(String value) {
        if(value==null || value.isBlank() || value.length()>2048 || value.chars().anyMatch(c->c<=32 || c==127 || c=='\\')) invalid();
        try {
            URI uri=URI.create(value);
            if(!Set.of("http","https").contains(Optional.ofNullable(uri.getScheme()).orElse("").toLowerCase(Locale.ROOT))
                    || uri.getHost()==null || uri.getUserInfo()!=null || uri.getRawAuthority().contains("%")
                    || uri.getPort()!=-1 && uri.getPort()!=80 && uri.getPort()!=443) invalid();
            String host=uri.getHost().toLowerCase(Locale.ROOT);
            if(host.startsWith("[")) host=host.substring(1,host.length()-1);
            if(host.endsWith(".") || host.equals("localhost") || host.endsWith(".localhost") || host.endsWith(".local")
                    || host.endsWith(".internal") || host.endsWith(".lan") || host.endsWith(".home") || host.startsWith("0x")) invalid();
            InetAddress[] addresses;
            if(host.contains(":")) addresses=new InetAddress[]{InetAddress.getByName(host)};
            else if(host.matches("[0-9.]+")) {
                String[] parts=host.split("\\.",-1);
                if(parts.length!=4 || Arrays.stream(parts).anyMatch(p->!p.matches("0|[1-9][0-9]{0,2}") || Integer.parseInt(p)>255)) invalid();
                addresses=new InetAddress[]{InetAddress.getByName(host)};
            } else {
                if(!host.contains(".") || !IDN.toASCII(host,IDN.USE_STD3_ASCII_RULES).equalsIgnoreCase(host)) invalid();
                String requestedHost=host;
                Future<InetAddress[]> lookup=dns.submit(()->resolver.resolve(requestedHost));
                try { addresses=lookup.get(2,TimeUnit.SECONDS); }
                finally { if(!lookup.isDone()) lookup.cancel(true); }
            }
            if(addresses==null || addresses.length==0 || Arrays.stream(addresses).anyMatch(a->!publicAddress(a))) invalid();
            return uri.toASCIIString();
        } catch(UrlFailure failure) { throw failure; }
        catch(InterruptedException failure) { Thread.currentThread().interrupt(); throw new UrlFailure(400,"Destination validation interrupted"); }
        catch(Exception failure) { throw new UrlFailure(400,"A resolvable public HTTP(S) destination on port 80 or 443 is required"); }
    }
    static boolean publicAddress(InetAddress address) {
        if(address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] bytes=address.getAddress();
        if(bytes.length==4) {
            int a=bytes[0]&255,b=bytes[1]&255,c=bytes[2]&255;
            return a!=0 && a!=10 && a!=127 && a<224 && !(a==100 && b>=64 && b<=127)
                    && !(a==169 && b==254) && !(a==172 && b>=16 && b<=31) && !(a==192 && (b==168 || b==0 && (c==0 || c==2)))
                    && !(a==198 && (b==18 || b==19 || b==51 && c==100)) && !(a==203 && b==0 && c==113);
        }
        // Accept global unicast; reject documentation and IPv4-embedded transition ranges.
        return (bytes[0]&0xe0)==0x20 && !((bytes[0]&255)==0x20 && (bytes[1]&255)==2)
                && !((bytes[0]&255)==0x20 && (bytes[1]&255)==1 && (bytes[2]&255)<2)
                && !((bytes[0]&255)==0x20 && (bytes[1]&255)==1 && (bytes[2]&255)==0x0d && (bytes[3]&255)==0xb8);
    }
    private static void invalid() { throw new UrlFailure(400,"A public HTTP(S) destination without credentials or unsafe addressing is required"); }
    @Override public void close() { dns.shutdownNow(); }
}
