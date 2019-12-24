package Client;

import Commons.WQRegisterInterface;
import Server.Consts;
import Server.WQRegister;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.rmi.AccessException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class Main {
    public static void main(String[] args) {
        try {
            Registry r = LocateRegistry.getRegistry(Consts.RMI_PORT);
            WQRegisterInterface serverObject = (WQRegisterInterface) r.lookup(Consts.WQ_STUB_NAME); //get remote object


            DatagramSocket datagramSocket = new DatagramSocket();

            //socket init
            SocketAddress address = new InetSocketAddress(Consts.TCP_PORT);

            try(BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
                SocketChannel client = SocketChannel.open(address)){

                try {
                    while (true) {
                        String message = input.readLine();
                        String[] messageFragments = message.split(" ");
                        switch (messageFragments[0]) {
                            case "register":
                                serverObject.registerUser(messageFragments[1], messageFragments[2]);
                                break;
                            case "login":
                                String toWrite = Consts.getRequestLogin(messageFragments[1], messageFragments[2]);
                                ByteBuffer byteBuffer = ByteBuffer.wrap(toWrite.getBytes(StandardCharsets.UTF_8));
                                client.write(byteBuffer);
                                break;
                            case "logout":
                                toWrite = Consts.getRequestLogout(messageFragments[1]);
                                byteBuffer = ByteBuffer.wrap(toWrite.getBytes(StandardCharsets.UTF_8));
                                client.write(byteBuffer);
                                break;
                            case "addFriend":
                                toWrite = Consts.getRequestAddFriend(messageFragments[1], messageFragments[2]);
                                byteBuffer = ByteBuffer.wrap(toWrite.getBytes(StandardCharsets.UTF_8));
                                client.write(byteBuffer);
                                break;
                            case "challenge":
                                toWrite = Consts.getRequestChallenge(messageFragments[1], messageFragments[2]);
                                DatagramPacket packet = new DatagramPacket(toWrite.getBytes(), toWrite.getBytes().length, InetAddress.getByName("localhost"), Consts.UDP_PORT);
                                datagramSocket.send(packet);
                                datagramSocket.receive(packet);
                                System.out.println(new String(packet.getData()));
                                break;
                                //TODO: add missing commands
                            default:
                                System.out.println("Sorry, not recognized");
                        }
                    }
                } catch (WQRegister.UserAlreadyRegisteredException e) {
                    e.printStackTrace();
                } catch (WQRegister.InvalidPasswordException e) {
                    e.printStackTrace();
                }

            }catch (IOException e) {
                e.printStackTrace();
            }


        } catch (NotBoundException e) {
            e.printStackTrace();
        } catch (SocketException e) {
            e.printStackTrace();
        } catch (AccessException e) {
            e.printStackTrace();
        } catch (RemoteException e) {
            e.printStackTrace();
        }

    }
}
