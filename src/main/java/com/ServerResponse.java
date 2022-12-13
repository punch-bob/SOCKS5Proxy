package main.java.com;

public final class ServerResponse {
    // 0 BYTE
    public static final Byte SOCKS5_VERSION = 0x05;

    // 1 BYTE
    public static final Byte SUCCESSFUL_REQUEST = 0x00;
    public static final Byte INVALID_CONNECTION = 0x02;
    public static final Byte NETWORK_UNAVAILABLE = 0x03;
    public static final Byte HOST_UNAVAILABLE = 0x04;
    public static final Byte CONNECTION_FAILURE = 0x05;
    public static final Byte PROTOCOL_ERROR = 0x07;
    public static final Byte ADDRESS_TYPE_NOT_SUPPORTED = 0x08; 
    public static final Byte NO_ACCEPTABLE_METHODS = (byte) 0xFF;

    // 3 BYTE
    public static final Byte IPv4_CONNECTION = 0x01;
    public static final Byte DOMAIN_CONNECTION = 0x03;

    public static final Byte[] ACCEPTABLE_METHODS = {0x00};
}
