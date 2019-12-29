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
                    //wait for challenge request
                    datagramSocket.receive(request);

                    //get message string
                    String[] messageFragments = new String(buffer, 0, request.getLength(), StandardCharsets.UTF_8).split(" ");
                    if(!messageFragments[0].equals(Consts.REQUEST_CHALLENGE)){
                        //wrong request
                        sendErrorMessage(datagramSocket, Consts.RESPONSE_UNKNOWN_REQUEST, request.getAddress(), request.getPort());
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

                        //there was an error
                        if(errorMessage != null) {
                            sendErrorMessage(datagramSocket, errorMessage, request.getAddress(), request.getPort());
                        }
                    }
                }catch (SocketTimeoutException ignored){
                    //no messages retrieved
                }
            }


        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    private void sendErrorMessage(DatagramSocket datagramSocket, String errorMessage, InetAddress address, int port) throws IOException {
        byte[] response = errorMessage.getBytes(StandardCharsets.UTF_8);
        DatagramPacket errorPacket = new DatagramPacket(response, response.length, address, port);
        datagramSocket.send(errorPacket);

    }
}
