package rocketgateway.config.objects;

@SuppressWarnings("ALL")
public class TLS {
    private boolean enableTls;
    private String[] tlsVersions;
    private String certificatechainFile;
    private String privatekeyFile;
    private String privatekeyPassword;
    private String trustedcertificateFile;


    public boolean enableTls() {
        return enableTls;
    }

    public String[] getTlsVersions() {
        return tlsVersions;
    }

    public String getCertificatechainFile() {
        return certificatechainFile;
    }

    public String getPrivatekeyFile() {
        return privatekeyFile;
    }

    public String getPrivatekeyPassword() {
        return privatekeyPassword;
    }

    public String getTrustedcertificateFile() {
        return trustedcertificateFile;
    }
}
