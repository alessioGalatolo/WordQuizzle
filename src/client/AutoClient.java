package client;

import commons.WQRegisterInterface;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import static client.AutoClientTesting.startChallenge;
import static commons.Constants.*;

/**
 * A randomized client that will login, wait and accept any challenge it receives
 * It will simulate the course of the challenge
 */
public class AutoClient implements Runnable{

    @Override
    public void run() {

        AtomicBoolean incomingChallenge = new AtomicBoolean(false);
        AtomicBoolean userBusy = new AtomicBoolean(false);
        Random random = new Random(System.currentTimeMillis());

        //socket init
        SocketAddress address = new InetSocketAddress(TCP_PORT);

        String currentLoggedUser = BASE_USERNAME;

        try (
                UDPClient udpClient = new UDPClient(incomingChallenge, userBusy, (String otherUser) -> {
                    //function to be used when a new challenge message arrives
                    return true; //accept every challenge
                });
                ClientNetworkHandler clientSocket = new ClientNetworkHandler(address, udpClient, null)
        ) {
            Registry r = LocateRegistry.getRegistry(RMI_PORT);
            WQRegisterInterface serverObject = (WQRegisterInterface) r.lookup(WQ_STUB_NAME); //get remote object

            String pass = "password";

            try {
                serverObject.registerUser(currentLoggedUser, pass);
            } catch (WQRegisterInterface.UserAlreadyRegisteredException ignored) {
            } //no problem

            clientSocket.handler(Command.LOGIN, currentLoggedUser, "", pass);

            while (!Thread.interrupted()) {

                //Wait for a challenge
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
