package Server;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;


public class UDPServer extends Thread {


    private ConcurrentHashMap<SocketAddress, SocketAddress> pendingChallenges = new ConcurrentHashMap<>();


    @Override
    public void run() {
        try (DatagramSocket socket = new DatagramSocket()){

            socket.setSoTimeout(Consts.UDP_SERVER_TIMEOUT);

            byte[] buffer = new byte[Consts.ARRAY_INIT_SIZE];
            DatagramPacket request = new DatagramPacket(buffer, buffer.length);

            while (!interrupted()) {
                try {
                    //wait for challenge request
                    socket.receive(request);

                    /**
                     * may receive:
                     *
                     *      challenge request
                     *      ok statement from challenged
                     *      not ok statement from challenged
                     */

                    //get message string
                    String message = new String(buffer, 0, request.getLength(), StandardCharsets.UTF_8);
                    String[] messageFragments = message.split(" ");
                    System.out.println("Received " + Arrays.toString(messageFragments));

                    SocketAddress addressUser1;
                    SocketAddress addressUser2;

                    switch (messageFragments[0]) {
                        case Consts.REQUEST_CHALLENGE:
                            String name1 = messageFragments[1];
                            String name2 = messageFragments[2];

                            String errorMessage = null;//stores, eventually, the error message

                            try {
                                DatagramPacket challengePacket = UserDB.challengeFriend(name1, name2);
                                socket.send(challengePacket);

                                pendingChallenges.put(request.getSocketAddress(), challengePacket.getSocketAddress());

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
                            if (errorMessage != null) {
                                sendErrorMessage(socket, errorMessage, request.getAddress(), request.getPort());
                            }
                            break;

                        case Consts.CHALLENGE_OK:
                            //retrieve user addresses involved in the challenge
                            addressUser1 = request.getSocketAddress();
                            addressUser2 = pendingChallenges.get(addressUser1);
                            if (addressUser2 == null)
                                continue; //TODO: handle error

                            pendingChallenges.remove(addressUser1);

                            //send ok message to both user
                            byte[] confirmationResponse = new byte[0];
                            try {
                                confirmationResponse = UserDB.getChallengeConfirm(addressUser1, addressUser2);
                            } catch (UserDB.ChallengeRequestTimeoutException e) {
                                sendErrorMessage(socket, Consts.RESPONSE_CHALLENGE_TIMEOUT, addressUser2);
                                sendErrorMessage(socket, Consts.RESPONSE_CHALLENGE_TIMEOUT, addressUser2);
                            } catch (UserDB.UserNotFoundException e) {
                                sendErrorMessage(socket, Consts.RESPONSE_CHALLENGE_REFUSED, addressUser2);
                                sendErrorMessage(socket, Consts.RESPONSE_CHALLENGE_REFUSED, addressUser2);
                            }

                            DatagramPacket challengeConfirmationPacket = new DatagramPacket(confirmationResponse, confirmationResponse.length, addressUser1);

                            socket.send(challengeConfirmationPacket);
                            challengeConfirmationPacket.setSocketAddress(addressUser2);
                            socket.send(challengeConfirmationPacket);
                            break;

                        case Consts.CHALLENGE_REFUSED:
                            addressUser1 = request.getSocketAddress();
                            addressUser2 = pendingChallenges.get(addressUser1);
                            if (addressUser2 == null)
                                continue; //TODO: handle error

                            pendingChallenges.remove(addressUser1);

                            try {
                                DatagramPacket challengeRefusedPacket = UserDB.discardChallenge(addressUser1, addressUser2);
                            } catch (UserDB.UserNotFoundException e) {
                                sendErrorMessage(socket, Consts.RESPONSE_CHALLENGE_REFUSED, addressUser2);
                                sendErrorMessage(socket, Consts.RESPONSE_CHALLENGE_REFUSED, addressUser2);
                            }
                            break;


                        default:
                            //wrong request
                            sendErrorMessage(socket, Consts.RESPONSE_UNKNOWN_REQUEST, request.getAddress(), request.getPort());
                            break;
                    }
                } catch (SocketTimeoutException ignored) {
                    //no messages retrieved, continue
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    private void sendErrorMessage(DatagramSocket datagramSocket, String errorMessage, SocketAddress address) throws IOException {
        byte[] response = errorMessage.getBytes(StandardCharsets.UTF_8);
        DatagramPacket errorPacket = new DatagramPacket(response, response.length, address);
        System.out.println("Sending " + errorMessage);
        datagramSocket.send(errorPacket);
    }

    private void sendErrorMessage(DatagramSocket datagramSocket, String errorMessage, InetAddress address, int port) throws IOException {
        byte[] response = errorMessage.getBytes(StandardCharsets.UTF_8);
        DatagramPacket errorPacket = new DatagramPacket(response, response.length, address, port);
        System.out.println("Sending " + errorMessage);
        datagramSocket.send(errorPacket);
    }


}
