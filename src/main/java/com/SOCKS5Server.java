package main.java.com;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Iterator;

public class SOCKS5Server implements Runnable{
    private final int port;
    private int numberOfClients = 0;
    private ServerSocketChannel serverChannel; 
    private Selector selector;

    private static final int BUF_SIZE = 1048576;
    private static final int IPv4_LENGTH = 4;
    private static final int PORT_LENGTH = 2;
    private int domainNameLength;

    public SOCKS5Server(int port) {
        this.port = port;
        try {
            serverChannel = ServerSocketChannel.open();
            serverChannel.socket().bind(new InetSocketAddress(port));
            serverChannel.configureBlocking(false);
            serverChannel.register(selector, serverChannel.validOps());

            selector = Selector.open();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void accept(SelectionKey key) throws IOException {
        this.numberOfClients++;
        SocketChannel clientChannel = ((ServerSocketChannel) key.channel()).accept();
        clientChannel.configureBlocking(false);
        clientChannel.register(key.selector(), SelectionKey.OP_READ);
    }

    private void read(SelectionKey key) {
        Attachment attachment = (Attachment) key.attachment();
        SocketChannel channel = (SocketChannel) key.channel();
        if (attachment == null) {
            attachment = new Attachment();
            attachment.in = ByteBuffer.allocate(BUF_SIZE);
            key.attach(attachment);
        }
        try {
            channel.read(attachment.in);
            if (attachment.status == Status.DISCONNETED) {
                connect(key);
            } else if (attachment.status == Status.CONNECTED) {
                byte[] clientRequest = attachment.in.array();
                byte zeroByte = clientRequest[0];
                byte firstByte = clientRequest[1];
                byte thirdByte = clientRequest[3];
                byte[] response = createResponse();

                if (zeroByte == ClientRequest.SOCKS5_VERSION) {
                    if (firstByte == ClientRequest.TCP_CONNECTION) {
                        if (thirdByte == ClientRequest.IPv4_CONNECTION) {
                            String address = getAddress(clientRequest, thirdByte);
                            if (address == null) {
                                response[1] = ServerResponse.HOST_UNAVAILABLE;
                                channel.write(ByteBuffer.wrap(response));
                                close(key);
                            }
                            short clientPort = ByteBuffer.wrap(Arrays.copyOfRange(clientRequest, 4 + IPv4_LENGTH, 4 + IPv4_LENGTH + PORT_LENGTH)).getShort();
                            try {
                                setConnection(address, clientPort, key, attachment);
                            } catch (IOException e) {
                                response[1] = ServerResponse.CONNECTION_FAILURE;
                                channel.write(ByteBuffer.wrap(response));
                                close(key);
                            }
                        } else if (thirdByte == ClientRequest.DOMAIN_CONNECTION) {
                            String address = getAddress(clientRequest, thirdByte);
                            if (address == null) {
                                response[1] = ServerResponse.NETWORK_UNAVAILABLE;
                                channel.write(ByteBuffer.wrap(response));
                                close(key);
                            }
                            short clientPort = ByteBuffer.wrap(Arrays.copyOfRange(clientRequest, 5 + domainNameLength, 5 + domainNameLength + PORT_LENGTH)).getShort();
                            try {
                                setConnection(address, clientPort, key, attachment);
                            } catch (IOException e) {
                                response[1] = ServerResponse.CONNECTION_FAILURE;
                                channel.write(ByteBuffer.wrap(response));
                                close(key);
                            }
                        } else {
                            response[1] = ServerResponse.ADDRESS_TYPE_NOT_SUPPORTED;
                            channel.write(ByteBuffer.wrap(response));
                            close(key);
                        }
                    } else {
                        response[1] = ServerResponse.INVALID_CONNECTION;
                        channel.write(ByteBuffer.wrap(response));
                        close(key);
                    }
                } else {
                    response[1] = ServerResponse.PROTOCOL_ERROR;
                    channel.write(ByteBuffer.wrap(response));
                    close(key);
                }
            } else if (attachment.status == Status.TRANSATING) {
                attachment.key.interestOps(attachment.key.interestOps() | SelectionKey.OP_WRITE);
                key.interestOps(key.interestOps() ^ SelectionKey.OP_READ);
                attachment.in.flip();
            }
        } catch (IOException e) {
            close(key);
            e.printStackTrace();
        }
    }

    private void write(SelectionKey key) {
        // TODO
    }

    private void connect(SelectionKey key) {
        //  TODO
    }

    private void close(SelectionKey key) {
        // TODO
    }

    private byte[] createResponse() {
        byte[] response = new byte[4 + IPv4_LENGTH + PORT_LENGTH];
        byte[] ip = null;
        try {
            ip = InetAddress.getLocalHost().getAddress();
        } catch (UnknownHostException e) {}
        byte[] port = ByteBuffer.allocate(PORT_LENGTH).order(ByteOrder.BIG_ENDIAN).putShort((short)this.port).array();
        response[0] = ServerResponse.SOCKS5_VERSION;
        response[1] = ServerResponse.SUCCESSFUL_REQUEST;
        response[2] = 0x00; // reserved byte
        response[3] = ServerResponse.IPv4_CONNECTION;
        System.arraycopy(ip, 0, response, 4, IPv4_LENGTH);
        System.arraycopy(port, 0, response, 4 + IPv4_LENGTH, PORT_LENGTH);
        return response;
    }

    private String getAddress(byte[] clientRequest, byte third) {
        String address = null;
        int addressLength = IPv4_LENGTH;
        int startByte = 4;
        if (third == ClientRequest.IPv4_CONNECTION) {
            addressLength = IPv4_LENGTH;
        } else if (third == ClientRequest.DOMAIN_CONNECTION) {
            addressLength = clientRequest[4];
            domainNameLength = addressLength;
            startByte++;
        }
        try {
            String ip = new String(Arrays.copyOfRange(clientRequest, startByte, startByte + addressLength));
            address = InetAddress.getByName(ip).getHostAddress();
        } catch (UnknownHostException e) {
            address = null;
            e.printStackTrace();
        }
        return address;
    }

    private void setConnection(String address, int clientPort, SelectionKey key, Attachment attachment) throws IOException {
        SocketAddress socketAddress = new InetSocketAddress(address, clientPort);
        SocketChannel socketChannel;
        socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(false);
        socketChannel.connect(socketAddress);
        SelectionKey targetKey = socketChannel.register(selector, SelectionKey.OP_CONNECT);
        ((Attachment)key.attachment()).key = targetKey;
        key.interestOps(0);
        Attachment target = new Attachment();
        target.key = key;
        targetKey.attach(target);
        attachment.in.clear();
    }

    @Override
    public void run() {
        while (true) {
            try {
                selector.select();
                Iterator<SelectionKey> keysIterator = selector.selectedKeys().iterator();
                while (keysIterator.hasNext()) {
                    SelectionKey key = keysIterator.next();
                    keysIterator.remove();

                    if (key.isValid()) {
                        if (key.isAcceptable()) {
                            accept(key);
                        } else if (key.isReadable()) {
                            read(key);
                        } else if (key.isWritable()) {
                            write(key);
                        } else if (key.isConnectable()) {
                            connect(key);
                        }
                    }
                }
            } catch (IOException e) {
                // TODO
                e.printStackTrace();
            }
        }
    } 
}
