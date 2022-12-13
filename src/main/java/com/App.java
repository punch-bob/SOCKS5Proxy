package main.java.com;

public class App {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Port not specified");
            return;
        }

        int port = Integer.parseInt(args[0]);
        SOCKS5Server server = new SOCKS5Server("127.0.0.1", port);
        server.run();
    }
}
