package main.java.com;

public final class ClientRequest {
    // 0 BYTE
    public static final Byte SOCKS5_VERSION = 0x05;

    // 1 BYTE
    public static final Byte NO_AUTH_CONNECTION = 0x00;
    public static final Byte TCP_CONNECTION = 0x01;

    // 3 BYTE
    public static final Byte IPv4_CONNECTION = 0x01;
    public static final Byte DOMAIN_CONNECTION = 0x03;
}
