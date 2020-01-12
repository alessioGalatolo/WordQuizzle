package Server;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Class that handles the UDP socket, it keeps waiting for
 * incoming messages and splits them depending on the request
 */
class UDPServer extends Thread {

    /*
        Stores the challenges waiting to be accepted

        The key is the address of the user who have been challenged, the value
        is the address of the user who started the challenge
     */
    private ConcurrentHashMap<SocketAddress, SocketAddress> pendingChallenges = new ConcurrentHashMap<>();


    @Override
    public void run() {
        try (DatagramSocket socket = new DatagramSocket(Consts.SERVER_UDP_PORT)){

            socket.setSoTimeout(Consts.UDP_SERVER_TIMEOUT);

            byte[] buffer = new byte[Consts.ARRAY_INIT_SIZE];
            DatagramPacket request = new DatagramPacket(buffer, buffer.length);

            while (!interrupted()) {
                try {
                    //wait for challenge request
                    socket.receive(request);

                    //get message string
                    String message = new String(buffer, 0, request.getLength(), StandardCharsets.UTF_8);
                    String[] messageFragments = message.split(" ");
                    System.out.println("UDP received " + message);

                    SocketAddress challengedAddress;
                    SocketAddress challengerAddress;

                    switch (messageFragments[0]) {
                        case Consts.REQUEST_CHALLENGE:
                            String challenger = messageFragments[1];
                            String challenged = messageFragments[2];

                            String errorMessage = null;//stores, eventually, the error message

                            try {
                                DatagramPacket challengePacket = UserDB.instance.challengeFriend(challenger, challenged);
                                socket.send(challengePacket);

                                challengedAddress = challengePacket.getSocketAddress();
                                challengerAddress = request.getSocketAddress();

                                pendingChallenges.put(challengedAddress, challengerAddress);

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
                            challengedAddress = request.getSocketAddress();
                            challengerAddress = pendingChallenges.get(challengedAddress);
                            if (challengerAddress == null) {
                                System.err.println("No matches found for address " + challengedAddress);
                                continue; //TODO: handle error
                            }
                            pendingChallenges.remove(challengedAddress);

                            try {
                                //send ok message to both user
                                byte[] confirmationResponse = UserDB.instance.getChallengeConfirm(challengerAddress, challengedAddress);

                                DatagramPacket challengeConfirmationPacket = new DatagramPacket(confirmationResponse, confirmationResponse.length, challengedAddress);
                                System.out.println(new String(challengeConfirmationPacket.getData(), 0, challengeConfirmationPacket.getLength(), StandardCharsets.UTF_8));
                                socket.send(challengeConfirmationPacket);
                                challengeConfirmationPacket.setSocketAddress(challengerAddress);
                                socket.send(challengeConfirmationPacket);

                            } catch (UserDB.ChallengeRequestTimeoutException e) {
                                sendErrorMessage(socket, Consts.RESPONSE_CHALLENGE_TIMEOUT, challengerAddress);
                                sendErrorMessage(socket, Consts.RESPONSE_CHALLENGE_TIMEOUT, challengerAddress);
                            } catch (UserDB.UserNotFoundException e) {
                                sendErrorMessage(socket, Consts.RESPONSE_CHALLENGE_REFUSED, challengerAddress);
                                sendErrorMessage(socket, Consts.RESPONSE_CHALLENGE_REFUSED, challengerAddress);
                            }
                            break;

                        case Consts.CHALLENGE_REFUSED:
                            challengedAddress = request.getSocketAddress();
                            challengerAddress = pendingChallenges.get(challengedAddress);
                            if (challengerAddress == null) {
                                System.err.println("No matches found for address " + challengedAddress);
                                continue; //TODO: handle error
                            }
                            pendingChallenges.remove(challengedAddress);

                            DatagramPacket challengeRefusedPacket = UserDB.instance.discardChallenge(challengerAddress, challengedAddress);
                            socket.send(challengeRefusedPacket);

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

    /**
     * Sends an error message through the UDP socket to the given address
     * @param datagramSocket The UDP socket
     * @param errorMessage The error message to be sent
     * @param address The socket address to send the error to
     */
    private void sendErrorMessage(DatagramSocket datagramSocket, String errorMessage, SocketAddress address) throws IOException {
        byte[] response = errorMessage.getBytes(StandardCharsets.UTF_8);
        DatagramPacket errorPacket = new DatagramPacket(response, response.length, address);
        System.out.println("Sending " + errorMessage);
        datagramSocket.send(errorPacket);
    }

    /**
     * Sends an error message through the UDP socket to the given address
     * @param datagramSocket The UDP socket
     * @param errorMessage The error message to be sent
     * @param address The inet address to  send the message to
     * @param port The port of the address
     */
    private void sendErrorMessage(DatagramSocket datagramSocket, String errorMessage, InetAddress address, int port) throws IOException {
        byte[] response = errorMessage.getBytes(StandardCharsets.UTF_8);
        DatagramPacket errorPacket = new DatagramPacket(response, response.length, address, port);
        System.out.println("Sending " + errorMessage);
        datagramSocket.send(errorPacket);
    }


}
