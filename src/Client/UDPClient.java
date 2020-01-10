package Client;

import Server.Consts;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

import static java.lang.Thread.interrupted;


/**
 * Has a thread always running to check for incoming messages, when one arrives
 * it invokes the function passed at construction time and it will send the answer
 * to the server
 *
 * Handles all the UDP requests
 *
 * Implements autoclosable to allow for an automatic close
 */
public class UDPClient implements AutoCloseable{

    private Thread readerThread; //thread that reads all the incoming messages
    private DatagramSocket socket; //UDP socket

    //a list and the lock and condition to use it
    private ArrayList<String> receivedMessages = new ArrayList<>();
    private ReentrantLock messagesLock = new ReentrantLock();
    private Condition newMessage = messagesLock.newCondition();

    private Boolean waitingChallengeStart = false; //true if the UDP socket is waiting to get final confirmation of a challenge

    private int latestMatchId = 0; //collects latest match id


    public UDPClient(AtomicBoolean startChallenge, Predicate<String> challengeRequestFun) throws SocketException {
        socket = new DatagramSocket();
        socket.setSoTimeout(Consts.UDP_CLIENT_TIMEOUT);

        //one thread always waiting for incoming messages
        readerThread = new Thread(() -> {
            while (!interrupted()){
                byte[] buffer = new byte[Consts.MAX_MESSAGE_LENGTH];
                DatagramPacket datagramPacket = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(datagramPacket);
                    String message = new String(datagramPacket.getData(), 0, datagramPacket.getLength(), StandardCharsets.UTF_8);
                    String[] messageFragments = message.split(" ");

                    //if message contains a challenge request
                    if(messageFragments[0].equals(Consts.REQUEST_CHALLENGE)){

                        //checks if user accepts challenge
                        if(challengeRequestFun.test(messageFragments[1])){
                            //challenge accepted
                            byte[] okResponse = Consts.RESPONSE_OK.getBytes(StandardCharsets.UTF_8);
                            datagramPacket = new DatagramPacket(okResponse, okResponse.length, InetAddress.getByName(Consts.SERVER_ADDRESS), Consts.SERVER_UDP_PORT);
                            socket.send(datagramPacket);

                            waitingChallengeStart = true;

                        } else {
                            //TODO: send error, user refused challenge
                        }

                    } else if(waitingChallengeStart && messageFragments[0].equals(Consts.RESPONSE_OK)) {
                        latestMatchId = Integer.parseInt(messageFragments[1]);
                        startChallenge.set(true);
                    }else{
                        messagesLock.lock();
                        receivedMessages.add(message);
                        newMessage.signal();
                        messagesLock.unlock();
                    }
                } catch (SocketTimeoutException ignored){

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        readerThread.start();

    }


    //TODO: user is allowed to request a challenge only if no incoming challenges
    /**
     * Forwards the challenge request to the server. May suspend the thread
     * during wait for answer (even for long time)
     * @param thisUser The user requesting the challenge
     * @param otherUser The user who is being challenged
     * @return The answer of the other user
     */
    public Boolean requestChallenge(String thisUser, String otherUser){
        try {
            byte[] challengeRequest = Consts.getRequestChallenge(thisUser, otherUser).getBytes(StandardCharsets.UTF_8);
            DatagramPacket datagramPacket = new DatagramPacket(challengeRequest, challengeRequest.length, InetAddress.getByName(Consts.SERVER_ADDRESS), Consts.SERVER_UDP_PORT);
            socket.send(datagramPacket);
            System.out.println("Sending " + new String(datagramPacket.getData(), datagramPacket.getOffset(), datagramPacket.getLength(), StandardCharsets.UTF_8));
            messagesLock.lock();
            int currentLength = receivedMessages.size();
            while (currentLength == receivedMessages.size())
                newMessage.await();

            String message = receivedMessages.get(currentLength);
            String[] messageFragments = message.split(" ");
            receivedMessages.remove(currentLength);
            messagesLock.unlock();
            if(messageFragments[0].equals(Consts.RESPONSE_OK)){
                latestMatchId = Integer.parseInt(messageFragments[1]);
                return true;
            }else {
                System.out.println(message);
                return false;
            }
        } catch (IOException | InterruptedException e){
            e.printStackTrace();
        }
        return false;
    }


    /**
     * Terminates the reader thread and closes the socket
     */
    public void close() {
        readerThread.interrupt();
        socket.close();
    }

    public int getLatestMatchId() {
        return latestMatchId;
    }

    public int getUDPPort() {
        return socket.getLocalPort();
    }
}
