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
    private final String host;
    private ServerSocketChannel serverChannel; 
    private Selector selector;

    private static final int BUF_SIZE = 1048576;
    private static final int IPv4_LENGTH = 4;
    private static final int PORT_LENGTH = 2;
    private int domainNameLength = 0;

    public SOCKS5Server(String host, int port) {
        this.port = port;
        this.host = host;
        try {
            serverChannel = ServerSocketChannel.open();
            selector = Selector.open();
            
            serverChannel.socket().bind(new InetSocketAddress(host, port));
            serverChannel.configureBlocking(false);
            serverChannel.register(selector, serverChannel.validOps());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void accept(SelectionKey key) throws IOException {
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
            if (channel.read(attachment.in) < 0) {
                close(key);
            }
            if (attachment.status == Status.DISCONNETED) {
                connectToServer(key);
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
                                System.out.println("Unable to connect to host");
                                response[1] = ServerResponse.HOST_UNAVAILABLE;
                                channel.write(ByteBuffer.wrap(response));
                                close(key);
                                return;
                            }
                            short clientPort = ByteBuffer.wrap(Arrays.copyOfRange(clientRequest, 4 + IPv4_LENGTH, 4 + IPv4_LENGTH + PORT_LENGTH)).getShort();
                            try {
                                setConnection(address, clientPort, key, attachment);
                                System.out.println("Requested connection through ip: " + address);
                            } catch (IOException e) {
                                System.out.println("The host: " + address + " dropped the connection");
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
                                System.out.println("Failed to find domain");
                                return;
                            }
                            short clientPort = ByteBuffer.wrap(Arrays.copyOfRange(clientRequest, 5 + domainNameLength, 5 + domainNameLength + PORT_LENGTH)).getShort();
                            try {
                                setConnection(address, clientPort, key, attachment);
                                System.out.println("Requested connection through domain: " + address);
                            } catch (IOException e) {
                                response[1] = ServerResponse.CONNECTION_FAILURE;
                                channel.write(ByteBuffer.wrap(response));
                                close(key);
                                System.out.println("The host: " + address + " dropped the connection");
                            }
                        } else {
                            response[1] = ServerResponse.ADDRESS_TYPE_NOT_SUPPORTED;
                            channel.write(ByteBuffer.wrap(response));
                            close(key);
                            System.out.println("IPv6 not supported");
                        }
                    } else {
                        response[1] = ServerResponse.INVALID_CONNECTION;
                        channel.write(ByteBuffer.wrap(response));
                        close(key);
                        System.out.println("Command not supported");
                    }
                } else {
                    response[1] = ServerResponse.PROTOCOL_ERROR;
                    channel.write(ByteBuffer.wrap(response));
                    close(key);
                    System.out.println("SOCKS < SOCKS5 not supported");
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

    private void connectToServer(SelectionKey key) throws IOException {
        Attachment attachment = (Attachment) key.attachment();
        byte[] clientGreeting = attachment.in.array();
        if (clientGreeting[0] == ClientRequest.SOCKS5_VERSION) {
            int numbOfMethods = clientGreeting[1];
            int lastMethodAddr = numbOfMethods + 2;
            boolean acceptableMethod = false;
            for (int i = 2; i < lastMethodAddr; ++i) {
                if (Arrays.asList(ServerResponse.ACCEPTABLE_METHODS).contains(clientGreeting[i])) {
                    acceptableMethod = true;
                    sendGreeting(key, clientGreeting[i]);
                    attachment.status = Status.CONNECTED;
                    attachment.in.clear();
                    System.out.println("A new client has connected to the server");
                    break;
                }
            }

            if (!acceptableMethod) {
                sendGreeting(key, ServerResponse.NO_ACCEPTABLE_METHODS);
                close(key);
                System.out.println("No supported method found");
            }
        } else {
            sendGreeting(key, ServerResponse.NO_ACCEPTABLE_METHODS);
            close(key);
            System.out.println("SOCKS < SOCKS5 not supported");
        }
    }

    private void write(SelectionKey key) {
        Attachment attachment = (Attachment) key.attachment();
        SocketChannel channel = (SocketChannel) key.channel();
        if (key.isValid()) {
            try {
                channel.write(attachment.out);
                if (attachment.out.remaining() == 0) {
                    if (attachment.key == null) {
                        close(key);
                    } else {
                        attachment.out.clear();
                        attachment.key.interestOps(attachment.key.interestOps() | SelectionKey.OP_READ);
                        key.interestOps(key.interestOps() ^ SelectionKey.OP_WRITE);
                    }
                }
            } catch (IOException e) {
                close(key);
                e.printStackTrace();
            }
            
        }
    }

    private void connect(SelectionKey key) {
        Attachment attachment = (Attachment) key.attachment();
        SocketChannel channel = (SocketChannel) key.channel();

        try {
            channel.finishConnect();
        } catch (IOException e) {
            e.printStackTrace();
        }
        attachment.in = ByteBuffer.allocate(BUF_SIZE);
        attachment.in.put(createResponse()).flip();

        attachment.out = ((Attachment)attachment.key.attachment()).in;
        ((Attachment)attachment.key.attachment()).out = attachment.in;

        ((Attachment)attachment.key.attachment()).status = Status.TRANSATING;
        attachment.status = Status.TRANSATING;

        attachment.key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
        key.interestOps(0);

        System.out.println("Connected to host");
    }

    private void close(SelectionKey key) {
        try {
            key.channel().close();
            SelectionKey selectionKey = ((Attachment) key.attachment()).key;
            if (selectionKey != null) {
                ((Attachment) selectionKey.attachment()).key = null;
                if (selectionKey.isValid()) {
                    if ((selectionKey.interestOps() & SelectionKey.OP_WRITE) == 0) {
                        ((Attachment) selectionKey.attachment()).out.flip();
                    }
                    selectionKey.interestOps(SelectionKey.OP_WRITE);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        key.cancel();
        System.out.println("Connection close");
    }

    private byte[] createResponse() {
        byte[] response = new byte[4 + IPv4_LENGTH + PORT_LENGTH];
        byte[] ip = null;
        ip = this.host.getBytes();
        // try {
            
        // } catch (UnknownHostException e) {}

        byte[] port = ByteBuffer.allocate(PORT_LENGTH).order(ByteOrder.BIG_ENDIAN).putShort((short)this.port).array();
        response[0] = ServerResponse.SOCKS5_VERSION;
        response[1] = ServerResponse.SUCCESSFUL_REQUEST;
        response[2] = 0x00; // reserved byte
        response[3] = ServerResponse.IPv4_CONNECTION;
        System.arraycopy(ip, 0, response, 4, IPv4_LENGTH);
        System.arraycopy(port, 0, response, 4 + IPv4_LENGTH, PORT_LENGTH);
        return response;
    }

    private void sendGreeting(SelectionKey key, byte responseByte) throws IOException {
        byte[] response = new byte[2];
        response[0] = ServerResponse.SOCKS5_VERSION;
        response[1] = responseByte;
        ((SocketChannel) key.channel()).write(ByteBuffer.allocate(2).put(response).flip());
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
            byte[] rowAddress = Arrays.copyOfRange(clientRequest, startByte, startByte + addressLength);
            if (third == ClientRequest.IPv4_CONNECTION) {
                address = InetAddress.getByAddress(rowAddress).getHostAddress();
            } else if (third == ClientRequest.DOMAIN_CONNECTION) {
                String ip = new String(rowAddress);
                address = InetAddress.getByName(ip).getHostAddress();
            }
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
        System.out.println("Server started at " + this.port + " port");
        while (true) {
            try {
                selector.select();
                if(!selector.isOpen()){
                    continue;
                }
                Iterator<SelectionKey> keysIterator = selector.selectedKeys().iterator();
                while (keysIterator.hasNext()) {
                    SelectionKey key = keysIterator.next();
                    keysIterator.remove();
                    
                    if (key.isValid()) {
                        try {
                            if (key.isAcceptable()) {
                                accept(key);
                            } else if (key.isReadable()) {
                                read(key);
                            } else if (key.isConnectable()) {
                                connect(key);
                            } else if (key.isWritable()) {
                                write(key);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            close(key);
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    } 
}
