package dev.ajaymatta.agentic.shortener;

import java.net.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class UrlSecurityTest {
    @ParameterizedTest @ValueSource(strings={"file:///tmp/a","https://localhost","https://host.local","https://host.internal","https://host.lan","https://host.home","https://example.com.","https://user:secret@example.com","https://example.com:8080","https://127.0.0.1","http://0.0.0.0","https://10.0.0.1","https://172.16.0.1","https://192.168.1.2","https://169.254.169.254","https://100.64.1.1","https://224.0.0.1","https://198.18.0.1","https://192.0.2.1","https://198.51.100.1","https://203.0.113.1","https://2130706433","https://0177.0.0.1","https://0x7f000001","https://[::1]","https://[fe80::1]","https://[fc00::1]","https://[2001:db8::1]","https://[2002:7f00:1::]","https://example.com/a\n","https://example.com\\evil","https://singlelabel","https://%65xample.com"})
    void unsafeDestinationsAreRejected(String target) throws Exception {
        try(var security=new UrlSecurity(host->new InetAddress[]{InetAddress.getByAddress(new byte[]{8,8,8,8})})) {
            assertThatThrownBy(()->security.validate(target)).isInstanceOf(UrlFailure.class);
        }
    }
    @Test void everyDnsAnswerMustBePublicAndFailuresAreClosed() throws Exception {
        try(var mixed=new UrlSecurity(host->new InetAddress[]{InetAddress.getByAddress(new byte[]{8,8,8,8}),InetAddress.getByAddress(new byte[]{10,0,0,1})});
            var empty=new UrlSecurity(host->new InetAddress[0]);
            var failing=new UrlSecurity(host->{ throw new UnknownHostException(); })) {
            for(var security:java.util.List.of(mixed,empty,failing)) assertThatThrownBy(()->security.validate("https://example.com")).isInstanceOf(UrlFailure.class);
        }
    }
    @Test void preservesExactPublicTargetAndBoundsInput() throws Exception {
        try(var security=new UrlSecurity(host->new InetAddress[]{InetAddress.getByAddress(new byte[]{8,8,8,8})})) {
            assertThat(security.validate("https://example.com:443/path?q=1#part")).isEqualTo("https://example.com:443/path?q=1#part");
            assertThat(security.validate("http://8.8.8.8/path")).isEqualTo("http://8.8.8.8/path");
            assertThatThrownBy(()->security.validate(null)).isInstanceOf(UrlFailure.class);
            assertThatThrownBy(()->security.validate(" ")).isInstanceOf(UrlFailure.class);
            assertThatThrownBy(()->security.validate("https://example.com/"+"x".repeat(2048))).isInstanceOf(UrlFailure.class);
        }
    }
}
