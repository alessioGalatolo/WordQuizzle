package Server;


import java.io.IOException;
import java.net.*;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class Main {

    public static void main(String[] args) {

        ChallengeHandler challengeHandler = new ChallengeHandler();
        System.out.println(Arrays.toString(challengeHandler.getDictionary()));

        try {

            //activate the remote object
            WQRegister wqRegister = new WQRegister();
            UnicastRemoteObject.exportObject(wqRegister, 0);
            Registry r = LocateRegistry.createRegistry(Consts.RMI_PORT);
            r.bind(Consts.WQ_STUB_NAME, wqRegister);

            UDPServer udpServer = new UDPServer();
            udpServer.start();

            ThreadPoolExecutor threadPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(Consts.SERVER_THREADS);



            //try with resources
            try(ServerSocketChannel serverChannel = ServerSocketChannel.open();
                Selector selector = Selector.open()) {

                //init server
                ServerSocket serverSocket = serverChannel.socket(); //TODO: close?
                InetSocketAddress address = new InetSocketAddress(Server.Consts.TCP_PORT); //local host
                serverSocket.bind(address); //binds address
                serverChannel.configureBlocking(false);
                serverChannel.register(selector, SelectionKey.OP_ACCEPT);

                //TODO: no termination is provided (yet)
                while (true) {
                    selector.select(); //blocking request

                    //check every ready key.
                    for(SelectionKey currentKey: selector.selectedKeys()){

                        try {
                            if (currentKey.isAcceptable()) {
                                //accept new connection

                                ServerSocketChannel server = (ServerSocketChannel) currentKey.channel();
                                SocketChannel clientSocketChannel = server.accept();
                                clientSocketChannel.configureBlocking(false);
                                clientSocketChannel.register(selector, SelectionKey.OP_READ); //expecting a write from the client as the new operation

                            } else if (currentKey.isReadable()) {
                                /*
                                    socket reads at most Server.Consts.ARRAY_INIT_SIZE bytes and puts the bytes read as an
                                    attachment to the the selectionKey. If an array of byte was already attached, they
                                    are joined and put back in the selectionKey.
                                */

                                threadPool.execute(new ReadTask(currentKey));

                                currentKey.interestOps(SelectionKey.OP_WRITE | SelectionKey.OP_READ);

                            } else if (currentKey.isWritable()) {
                                /*
                                    Checks the attachment of the selectionKey.
                                    if a byte[] is found -> allocates a byteBuffer and tries to write it all. if an incomplete write happens, the remaining buffer is stored as an attachment
                                    if a ByteBuffer is found -> an incomplete write happened, tries to complete it.
                                 */
                                threadPool.execute(new WriteTask(currentKey));

                            } else {
                                System.err.println("Key has not been recognised");
                            }
                        }catch (IOException e) {
                            currentKey.cancel();
                            try{
                                currentKey.channel().close();
                            }catch (IOException ex) {
                                ex.printStackTrace();
                            }
                        }
                    }

                    selector.selectedKeys().clear();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            threadPool.shutdown();
            udpServer.interrupt();
            UnicastRemoteObject.unexportObject(wqRegister, true);
        } catch (RemoteException | AlreadyBoundException e) {
            e.printStackTrace();
        }
    }
}
