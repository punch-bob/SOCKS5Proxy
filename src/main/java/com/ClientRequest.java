package main.java.com;

public final class ClientRequest {
    // 0 BYTE
    public static final short SOCKS5_VERSION = 0x05;

    // 1 BYTE
    public static final short TCP_CONNECTION = 0x01;

    // 3 BYTE
    public static final short IPv4_CONNECTION = 0x01;
    public static final short DOMAIN_CONNECTION = 0x03;
}
