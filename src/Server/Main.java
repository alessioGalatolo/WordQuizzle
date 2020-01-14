package Server;

import java.io.IOException;
import java.rmi.AlreadyBoundException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {


        try(Reactor reactor = new Reactor(Consts.TCP_PORT)) {

            //activate and bind the remote object
            WQRegister wqRegister = new WQRegister();
            UnicastRemoteObject.exportObject(wqRegister, 0);
            Registry r = LocateRegistry.createRegistry(Consts.RMI_PORT);
            r.bind(Consts.WQ_STUB_NAME, wqRegister);

            //start UDP server
            UDPServer udpServer = new UDPServer();
            udpServer.start();

            //start TCP server
            reactor.start();

            //wait for user input for termination
            Scanner scanner = new Scanner(System.in);
            String response = "";
            while(!response.equals("quit"))
                response = scanner.nextLine();


            udpServer.interrupt();
            UnicastRemoteObject.unexportObject(wqRegister, true);
        } catch (AlreadyBoundException | IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
