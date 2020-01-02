package Client;

import Server.Consts;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class UDPClient extends Thread {

    private DatagramSocket socket;
    private Boolean waitingResponse = false; //Is true if a challenge request was sent and it is waiting for a response

    //shared objects
    private AtomicBoolean incomingChallenge;
    private AtomicBoolean outgoingChallenge;
    private AtomicInteger challengeResponse; // = 0 no response, > 0 accepted, < 0 refused
    private StringBuffer thisUser;
    private StringBuffer otherUser;

    public UDPClient(AtomicBoolean incomingChallenge, AtomicBoolean outgoingChallenge, AtomicInteger challengeResponse, StringBuffer thisUser, StringBuffer otherUser){
        try {
            socket = new DatagramSocket();
            socket.setSoTimeout(Consts.UDP_TIMEOUT);
        } catch (SocketException e) {
            e.printStackTrace();
        }

        this.incomingChallenge = incomingChallenge;
        this.outgoingChallenge = outgoingChallenge;
        this.challengeResponse = challengeResponse;
        this.thisUser = thisUser;
        this.otherUser = otherUser;
    }


    @Override
    public void run() {
        byte[] buffer = new byte[Consts.MAX_MESSAGE_LENGTH];
        DatagramPacket datagramPacket = new DatagramPacket(buffer, buffer.length);
        while (!interrupted()) {
            try {
                if(outgoingChallenge.get()){
                    outgoingChallenge.set(false);
                    byte[] challengeRequest = Consts.getRequestChallenge(thisUser.toString(), otherUser.toString()).getBytes(StandardCharsets.UTF_8);
                    datagramPacket = new DatagramPacket(challengeRequest, challengeRequest.length, InetAddress.getByName(Consts.SERVER_ADDRESS), Consts.SERVER_UDP_PORT);
                    waitingResponse = true;
                }

                socket.receive(datagramPacket);
                String[] message = new String(datagramPacket.getData(), 0, datagramPacket.getLength(), StandardCharsets.UTF_8).split(" ");
                String command = message[0];

                if(command.equals(Consts.REQUEST_CHALLENGE)){
                    incomingChallenge.set(true);
                    otherUser.replace(0, otherUser.length(), message[1]);
                }else if(waitingResponse && command.equals(Consts.RESPONSE_OK)){
                    challengeResponse.set(1);
                    waitingResponse = false;
                }else if(waitingResponse && command.equals(Consts.RESPONSE_CHALLENGE_REFUSED)){
                    challengeResponse.set(-1);
                    waitingResponse = false;
                }

            } catch(SocketTimeoutException e){

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
