package Server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;


public class Reactor extends Thread implements AutoCloseable {

    private final Selector selector;
    private final ServerSocketChannel serverSocket;

    Reactor(int port) throws IOException {
        selector = Selector.open();
        serverSocket = ServerSocketChannel.open();
        serverSocket.socket().bind(new InetSocketAddress(port));
        serverSocket.configureBlocking(false);
        SelectionKey selectionKey = serverSocket.register(selector, SelectionKey.OP_ACCEPT);
        selectionKey.attach("");
    }


    @Override
    public void run() {
        try {
            while (!interrupted()) {
                selector.select();

                for (SelectionKey selectedKey : selector.selectedKeys()) {
                    Object object = (selectedKey.attachment());
                    if (object instanceof String) {
                        SocketChannel channel = serverSocket.accept();
                        if (channel != null)
                            new Handler(selector, channel);
                    } else if (object instanceof Runnable){
                        ((Runnable) object).run(); //TODO: start?
                    }
                }
                selector.selectedKeys().clear();
            }
            Handler.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void close() throws IOException, InterruptedException {
        this.interrupt();
        join();
        selector.close();
        serverSocket.close();
    }

}
