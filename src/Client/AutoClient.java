package Client;

import Commons.WQRegisterInterface;
import Server.Consts;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import static Client.AutoClientTesting.startChallenge;

public class AutoClient implements Runnable{

    @Override
    public void run() {

        AtomicBoolean incomingChallenge = new AtomicBoolean(false);
        AtomicBoolean userBusy = new AtomicBoolean(false);
        Random random = new Random(System.currentTimeMillis());

        //socket init
        SocketAddress address = new InetSocketAddress(Consts.TCP_PORT);

        String currentLoggedUser = Consts.BASE_USERNAME;

        try (
                UDPClient udpClient = new UDPClient(incomingChallenge, userBusy, (String otherUser) -> {
                    //function to be used when a new challenge message arrives
                    //returns whether or not the challenge has been accepted
                    return true;
                });
                ClientSocket clientSocket = new ClientSocket(address, udpClient, null)
        ) {
            Registry r = LocateRegistry.getRegistry(Consts.RMI_PORT);
            WQRegisterInterface serverObject = (WQRegisterInterface) r.lookup(Consts.WQ_STUB_NAME); //get remote object

            String pass = "password";

            try {
                serverObject.registerUser(currentLoggedUser, pass);
            } catch (WQRegisterInterface.UserAlreadyRegisteredException ignored) {
            } //no problem

            clientSocket.handler(Command.LOGIN, currentLoggedUser, "", pass);

            while (!Thread.interrupted()) {
                if (incomingChallenge.get()) {
                    incomingChallenge.set(false);
                    startChallenge(clientSocket.getWordIterator(currentLoggedUser), userBusy, random);
                }
            }

            clientSocket.handler(Command.LOGOUT, currentLoggedUser, "", pass);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
