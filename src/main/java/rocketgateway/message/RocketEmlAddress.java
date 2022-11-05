package rocketgateway.message;

/**
 * Data class for e-mail addresses.
 */
public record RocketEmlAddress(String name, String address) implements Comparable<RocketEmlAddress> {

    /**
     * Returns stored name
     *
     * @return String with name
     */
    @Override
    public String name() {
        return name;
    }

    /**
     * Returns stored e-mail address
     *
     * @return String with e-mail address
     */
    @Override
    public String address() {
        return address;
    }

    @Override
    public int compareTo(RocketEmlAddress other) {
        return this.address.compareTo(other.address);
    }
}
