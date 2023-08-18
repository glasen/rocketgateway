package rocketgateway.smtp;

import nl.altindag.ssl.SSLFactory;
import nl.altindag.ssl.pem.util.PemUtils;

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509ExtendedTrustManager;
import java.nio.file.Paths;

public class SSLLoader {
    private final String certificateChainFile;
    private final String privateKeyFile;
    private final String privateKeyPassword;
    private final String trustedCertificateFile;
    private final X509ExtendedKeyManager extendedKeyManager;
    private final X509ExtendedTrustManager trustManager;
    private final String[] protocols;
    private SSLContext sslContext;
    public static SSLLoader instance;

    private SSLLoader(String certificateChainFile, String privateKeyFile,
                      String privateKeyPassword, String trustedCertificateFile, String[] protocols) {
        this.certificateChainFile = certificateChainFile;
        this.privateKeyFile = privateKeyFile;
        this.privateKeyPassword = privateKeyPassword;
        this.trustedCertificateFile = trustedCertificateFile;
        this.protocols = protocols;

        this.extendedKeyManager = this.getExtendedKeyManager();
        this.trustManager = this.getTrustManager();

        if (this.extendedKeyManager != null & this.trustManager != null) {
            this.generateSSLContent();
        }
    }

    public static void init(String certificateChainFile, String privateKeyFile,
                            String privateKeyPassword, String trustedCertificate, String[] protocols) {
        if (instance == null) {
            instance = new SSLLoader(certificateChainFile, privateKeyFile,
                    privateKeyPassword, trustedCertificate, protocols);
        }
    }

    private void generateSSLContent() {
        SSLFactory sslFactory = SSLFactory.builder()
        .withIdentityMaterial(this.extendedKeyManager)
        .withTrustMaterial(this.trustManager)
        .withProtocols(this.protocols)
        .build();

       this.sslContext = sslFactory.getSslContext();
    }

    private X509ExtendedKeyManager getExtendedKeyManager() {
        if (this.privateKeyPassword.isEmpty()) {
            return PemUtils.loadIdentityMaterial(
                    Paths.get(this.certificateChainFile),
                    Paths.get(this.privateKeyFile));

        } else {
            return PemUtils.loadIdentityMaterial(
                    Paths.get(this.certificateChainFile),
                    Paths.get(this.privateKeyFile), this.privateKeyPassword.toCharArray());
        }
    }

    private X509ExtendedTrustManager getTrustManager() {
        return PemUtils.loadTrustMaterial(Paths.get(this.trustedCertificateFile));
    }

    public static SSLContext getSslContext() {
        return instance.sslContext;
    }
}
