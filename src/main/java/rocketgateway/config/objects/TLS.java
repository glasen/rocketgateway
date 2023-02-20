package rocketgateway.config.objects;

@SuppressWarnings("ALL")
public record TLS(boolean enableTls, String[] tlsVersions, String certificatechainFile, String privatekeyFile,
                  String privatekeyPassword, String trustedcertificateFile) {
}
