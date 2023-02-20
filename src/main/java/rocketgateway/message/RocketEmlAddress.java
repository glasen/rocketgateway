package rocketgateway.message;

/**
 * Data class for e-mail addresses.
 */
public record RocketEmlAddress(String name, String address) implements Comparable<RocketEmlAddress> {
    @Override
    public int compareTo(RocketEmlAddress other) {
        return this.address.compareTo(other.address);
    }
}
