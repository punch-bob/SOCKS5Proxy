package main.java.com;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;

public class Attachment {
    public ByteBuffer in;
    public ByteBuffer out;

    public SelectionKey key;

    public Status status = Status.DISCONNETED;
}
