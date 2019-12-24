package Server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

/**
 * Class that manages the UDP server
 * Handles all the challenge requests
 */
public class UDPServer extends Thread {
    @Override
    public void run() {
        //TODO: remember termination
        //TODO: maybe use nio

        try {
            DatagramSocket datagramSocket = new DatagramSocket(Consts.UDP_PORT);
            datagramSocket.setSoTimeout(Consts.UDP_TIMEOUT);

            byte[] buffer = new byte[Consts.ARRAY_INIT_SIZE];
            DatagramPacket request = new DatagramPacket(buffer, buffer.length);


            while (!interrupted()) {
                try {
                    datagramSocket.receive(request);

                    //TODO: check message validity
                    String[] messageFragments = new String(buffer, StandardCharsets.UTF_8).split(" ");
                    if(!messageFragments[0].equals(Consts.REQUEST_CHALLENGE)){
                        sendErrorMessage(datagramSocket, request.getAddress(), request.getPort());
                    }else{
                        String name1 = messageFragments[1];
                        String name2 = messageFragments[2];

                        String errorMessage = null;//stores, eventually, the error message

                        try {
                            UserDB.challengeFriend(name1, name2, datagramSocket);
                        } catch (UserDB.UserNotFoundException e) {
                            errorMessage = Consts.RESPONSE_USER_NOT_FOUND;
                        } catch (UserDB.NotFriendsException e) {
                            errorMessage = Consts.RESPONSE_NOT_FRIENDS;
                        } catch (UserDB.NotLoggedException e) {
                            errorMessage = Consts.RESPONSE_NOT_LOGGED;
                        } catch (UserDB.SameUserException e) {
                            errorMessage = Consts.RESPONSE_SAME_USER;
                        }

                        if(errorMessage != null) {
                            byte[] messageBytes = errorMessage.getBytes(StandardCharsets.UTF_8);
                            DatagramPacket response = new DatagramPacket(messageBytes, messageBytes.length, request.getAddress(), request.getPort());
                            datagramSocket.send(response);
                        }
                    }
                }catch (SocketTimeoutException ignored){
                }
            }


        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    private void sendErrorMessage(DatagramSocket datagramSocket, InetAddress address, int port) throws IOException {
        byte[] response = Consts.RESPONSE_UNKNOWN_REQUEST.getBytes(StandardCharsets.UTF_8);
        DatagramPacket errorMessage = new DatagramPacket(response, response.length, address, port);
        datagramSocket.send(errorMessage);

    }
}
