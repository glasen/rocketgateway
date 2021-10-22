package rocketgateway.message;

/**
 * Data class for e-mail addresses.
 */
public class RocketEmlAddress implements Comparable<RocketEmlAddress> {
    private final String name;
    private final String address;
    
    public RocketEmlAddress(String name, String address) {
        this.name = name;
        this.address = address;
    }

    /**
     * Returns stored name
     * @return String with name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns stored e-mail address
     * @return String with e-mail address
     */
    public String getAddress() {
        return address;
    }

    @Override
    public int compareTo(RocketEmlAddress other) {
        return this.address.compareTo(other.address);
    }
}
