package Server;

import java.io.IOException;
import java.rmi.AlreadyBoundException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Scanner;

import static Commons.Constants.*;


/**
 * Main class of server
 */
public class Main {

    public static void main(String[] args) {

        boolean test = false; //true if a test is in progress, will improve termination
        try {
            test = !args[0].isEmpty();
        }catch (IndexOutOfBoundsException ignored){}


        try(Reactor reactor = new Reactor(TCP_PORT)) {

            //activate and bind the remote object
            WQRegister wqRegister = new WQRegister();
            UnicastRemoteObject.exportObject(wqRegister, 0);
            Registry r = LocateRegistry.createRegistry(RMI_PORT);
            r.bind(WQ_STUB_NAME, wqRegister);

            //start UDP server
            UDPServer udpServer = new UDPServer();
            udpServer.start();

            //start TCP server
            reactor.start();

            //wait for user input for termination
            Scanner scanner = new Scanner(System.in);
            String response = "";
            while (!response.equals("quit") && !Thread.interrupted())
                if (!test)
                    response = scanner.nextLine();

            //terminate
            udpServer.interrupt();
            UnicastRemoteObject.unexportObject(wqRegister, true);
        } catch (AlreadyBoundException | IOException | InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("Server shutdown");
    }
}
