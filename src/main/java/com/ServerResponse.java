package main.java.com;

public final class ServerResponse {
    // 0 BYTE
    public static final short SOCKS5_VERSION = 0x05;

    // 1 BYTE
    public static final short SUCCESSFUL_REQUEST = 0x00;
    public static final short INVALID_CONNECTION = 0x02;
    public static final short NETWORK_UNAVAILABLE = 0x03;
    public static final short HOST_UNAVAILABLE = 0x04;
    public static final short CONNECTION_FAILURE = 0x05;
    public static final short PROTOCOL_ERROR = 0x07;
    public static final short ADDRESS_TYPE_NOT_SUPPORTED = 0x08; 

    // 3 BYTE
    public static final short IPv4_CONNECTION = 0x01;
    public static final short DOMAIN_CONNECTION = 0x03;
}
