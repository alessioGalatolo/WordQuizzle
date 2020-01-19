package client;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

import static client.Consts.ACCEPTABLE_WAIT_CHALLENGE_CONFIRM;
import static client.Consts.getRequestChallenge;
import static commons.Constants.*;
import static java.lang.Thread.interrupted;


/**
 * Has a thread always running to check for incoming messages, when one arrives
 * it invokes the function passed at construction time and it will send the answer
 * to the server
 *
 * Handles all the UDP requests
 *
 * Implements AutoClosable to allow for an automatic close
 */
class UDPClient implements AutoCloseable{

    private Thread readerThread; //thread that reads all the incoming messages
    private DatagramSocket socket; //UDP socket

    //a list and the lock and condition to use it
    private final ArrayList<String> receivedMessages = new ArrayList<>();
    private final ReentrantLock messagesLock = new ReentrantLock();
    private final Condition newMessage = messagesLock.newCondition();

    private Boolean waitingChallengeStart = false; //true if the UDP socket is waiting to get final confirmation of a challenge

    private int latestMatchId = 0; //collects latest match id

    private boolean test = false; //if true prints all incoming and outgoing messages

    /**
     * Creates the udp socket and starts the thread who reads all the incoming messages
     * @param startChallenge A boolean to be set to true when the user has to start a challenge (TCP)
     * @param userBusy A boolean to be set true when the user is already doing a challenge
     * @param challengeRequestFun Function to be called when a new challenge arrives
     */
    UDPClient(AtomicBoolean startChallenge, AtomicBoolean userBusy, Predicate<String> challengeRequestFun) throws SocketException {
        socket = new DatagramSocket();
        socket.setSoTimeout(UDP_CLIENT_TIMEOUT);

        //one thread always waiting for incoming messages
        readerThread = new Thread(() -> {
            //Defining the runnable of the thread

            while (!interrupted()){
                byte[] buffer = new byte[MAX_MESSAGE_LENGTH];
                DatagramPacket datagramPacket = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(datagramPacket);
                    String message = new String(datagramPacket.getData(), 0, datagramPacket.getLength(), StandardCharsets.UTF_8);
                    String[] messageFragments = message.split(" ");
                    if(test)
                        System.out.println(Thread.currentThread().getName() + " udp received " + message);


                    //if message contains a challenge request
                    if(messageFragments[0].equals(REQUEST_CHALLENGE)){

                        //checks if user accepts challenge
                        if(!userBusy.get() && challengeRequestFun.test(messageFragments[1])){
                            //challenge accepted
                            byte[] okResponse = RESPONSE_OK.getBytes(StandardCharsets.UTF_8);
                            datagramPacket = new DatagramPacket(okResponse, okResponse.length, InetAddress.getByName(SERVER_ADDRESS), SERVER_UDP_PORT);
                            socket.send(datagramPacket);

                            waitingChallengeStart = true;

                        } else {
                            byte[] failResponse = CHALLENGE_REFUSED.getBytes(StandardCharsets.UTF_8);
                            datagramPacket = new DatagramPacket(failResponse, failResponse.length, InetAddress.getByName(SERVER_ADDRESS), SERVER_UDP_PORT);
                            socket.send(datagramPacket);
                        }

                    } else if(waitingChallengeStart && messageFragments[0].equals(RESPONSE_OK)) {
                        latestMatchId = Integer.parseInt(messageFragments[1]);
                        waitingChallengeStart = false;
                        startChallenge.set(true);
                        userBusy.set(true);
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

    /**
     * Creates the udp socket and starts the thread who reads all the incoming messages
     * @param startChallenge A boolean to be set to true when the user has to start a challenge (TCP)
     * @param userBusy A boolean to be set true when the user is already doing a challenge
     * @param challengeRequestFun Function to be called when a new challenge arrives
     * @param test If set true the class will print al the interactions with the server
     */
    UDPClient(AtomicBoolean startChallenge, AtomicBoolean userBusy, boolean test, Predicate<String> challengeRequestFun) throws SocketException{
        this(startChallenge, userBusy, challengeRequestFun);
        this.test = test;
    }

    /**
     * Forwards the challenge request to the server. May suspend the thread
     * during wait for answer (even for long time)
     * @param thisUser The user requesting the challenge
     * @param otherUser The user who is being challenged
     * @return The answer of the other user
     */
    Boolean requestChallenge(String thisUser, String otherUser){
        try {
            byte[] challengeRequest = getRequestChallenge(thisUser, otherUser).getBytes(StandardCharsets.UTF_8);
            DatagramPacket datagramPacket = new DatagramPacket(challengeRequest, challengeRequest.length, InetAddress.getByName(SERVER_ADDRESS), SERVER_UDP_PORT);
            socket.send(datagramPacket);
            if(test)
                System.out.println(Thread.currentThread().getName() + " udp sent " + new String(challengeRequest));
            messagesLock.lock();
            int currentLength = receivedMessages.size();
            while (currentLength == receivedMessages.size())
                if(!newMessage.await(ACCEPTABLE_WAIT_CHALLENGE_CONFIRM, TimeUnit.MILLISECONDS)) {
                    messagesLock.unlock();
                    return false;
                }

            String message = receivedMessages.get(currentLength);
            String[] messageFragments = message.split(" ");
            receivedMessages.remove(currentLength);
            messagesLock.unlock();
            if(messageFragments[0].equals(RESPONSE_OK)){
                latestMatchId = Integer.parseInt(messageFragments[1]);
                return true;
            }else {
                System.err.println(message);
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
    @Override
    public void close() {
        readerThread.interrupt();
        try {
            readerThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        socket.close();
    }

    /**
     * @return The matchId of the latest challenge accepted
     */
    int getLatestMatchId() {
        return latestMatchId;
    }

    int getUDPPort() {
        return socket.getLocalPort();
    }
}
