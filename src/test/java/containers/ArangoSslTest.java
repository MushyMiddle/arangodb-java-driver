package containers;


import com.arangodb.ArangoDB;
import com.arangodb.ArangoDBException;
import com.arangodb.Protocol;
import com.arangodb.async.ArangoDBAsync;
import com.arangodb.entity.ArangoDBVersion;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * @author Mark Vollmary
 * @author Michele Rastelli
 * FIXME: hosts are merged to the ones coming from arangodb.properties
 */
@Ignore
@RunWith(Parameterized.class)
public class ArangoSslTest {

    /*-
     * a SSL trust store
     *
     * create the trust store for the self signed certificate:
     * keytool -import -alias "my arangodb server cert" -file UnitTests/server.pem -keystore example.truststore
     *
     * Documentation:
     * https://hc.apache.org/httpcomponents-client-ga/httpclient/apidocs/org/apache/http/conn/ssl/SSLSocketFactory.html
     */
    private static final String SSL_TRUSTSTORE = "/example.truststore";
    private static final String SSL_TRUSTSTORE_PASSWORD = "12345678";

    private final Protocol protocol;

    @Parameterized.Parameters
    public static List<Protocol> builders() {
        return Arrays.asList(
                Protocol.VST,
                Protocol.HTTP_VPACK
        );
    }

    public ArangoSslTest(final Protocol protocol) {
        this.protocol = protocol;
    }

    private SSLContext getSslContext() throws Exception {
        final KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(this.getClass().getResourceAsStream(SSL_TRUSTSTORE), SSL_TRUSTSTORE_PASSWORD.toCharArray());

        final KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, SSL_TRUSTSTORE_PASSWORD.toCharArray());

        final TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);

        final SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        return sc;
    }

    @Test
    public void connectSync() throws Exception {
        final ArangoDB arangoDB = new ArangoDB.Builder()
                .host(
                        SingleServerSslContainer.INSTANCE.container.getContainerIpAddress(),
                        SingleServerSslContainer.INSTANCE.container.getFirstMappedPort())
                .useProtocol(protocol)
                .useSsl(true)
                .sslContext(getSslContext())
                .build();
        final ArangoDBVersion version = arangoDB.getVersion();
        assertThat(version, is(notNullValue()));
    }

    @Test(expected = ArangoDBException.class)
    public void connectWithoutValidSslContextSync() {
        final ArangoDB arangoDB = new ArangoDB.Builder()
                .host(
                        SingleServerSslContainer.INSTANCE.container.getContainerIpAddress(),
                        SingleServerSslContainer.INSTANCE.container.getFirstMappedPort())
                .useProtocol(protocol)
                .useSsl(true)
                .build();
        arangoDB.getVersion();
        fail();
    }

    @Test
    public void connectAsync() throws Exception {
        final ArangoDBAsync arangoDB = new ArangoDBAsync.Builder()
                .host(
                        SingleServerSslContainer.INSTANCE.container.getContainerIpAddress(),
                        SingleServerSslContainer.INSTANCE.container.getFirstMappedPort())
                .useProtocol(protocol)
                .useSsl(true)
                .sslContext(getSslContext())
                .build();
        final ArangoDBVersion version = arangoDB.getVersion().join();
        assertThat(version, is(notNullValue()));
    }

}
