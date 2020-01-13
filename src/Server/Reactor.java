package Server;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;


public class Reactor extends Thread implements AutoCloseable {

    private final Selector selector;
    private final ServerSocketChannel serverSocket;

    public Reactor(int port) throws IOException {
        selector = Selector.open();
        serverSocket = ServerSocketChannel.open();
        serverSocket.socket().bind(new InetSocketAddress(port));
        serverSocket.configureBlocking(false);
        SelectionKey selectionKey = serverSocket.register(selector, SelectionKey.OP_ACCEPT);
        selectionKey.attach(new Acceptor());
    }


    @Override
    public void run() {
        try {
            while (!interrupted()) {
                selector.select();

                Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                while (iterator.hasNext()) {
                    SelectionKey selectedKey = iterator.next();
                    Runnable r = (Runnable) (selectedKey.attachment());
                    if (r instanceof Acceptor){
                        SocketChannel channel = serverSocket.accept();
                        if (channel != null)
                            new Handler(selector, channel);
                    }else
                        r.run(); //TODO: start?

                    iterator.remove();
                }
                selector.selectedKeys().clear();
            }
            Handler.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void close() throws IOException {
        this.interrupt();
//        threadPool.shutdown();
//        threadPool.awaitTermination(Integer.MAX_VALUE, TimeUnit.MILLISECONDS);
        selector.close();
        serverSocket.close();
    }


    private class Acceptor implements Runnable {

        @Override
        public void run() {
            try {
                SocketChannel channel = serverSocket.accept();
                if (channel != null)
                    new Handler(selector, channel);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

}
