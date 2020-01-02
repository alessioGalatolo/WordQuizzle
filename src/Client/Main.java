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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    public static void main(String[] args) {
        try {
            Registry r = LocateRegistry.getRegistry(Consts.RMI_PORT);
            WQRegisterInterface serverObject = (WQRegisterInterface) r.lookup(Consts.WQ_STUB_NAME); //get remote object

            AtomicBoolean incomingChallenge = new AtomicBoolean(false);
            AtomicBoolean outgoingChallenge = new AtomicBoolean(false);
            AtomicInteger waitingResponse = new AtomicInteger(0);
            StringBuffer thisUser = new StringBuffer();
            StringBuffer otherUser = new StringBuffer();

            UDPClient udpClient = new UDPClient(incomingChallenge, outgoingChallenge, waitingResponse, thisUser, otherUser);
            udpClient.start();

            //socket init
            SocketAddress address = new InetSocketAddress(Consts.TCP_PORT);

            try(BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
                SocketChannel client = SocketChannel.open(address)){

                while (true) {
                    if (incomingChallenge.get()) {
                        incomingChallenge.set(false);
                        System.out.println("Incoming challenge from " + otherUser + ", accept? (y/n)");
                        String response = input.readLine();
                        if (response.toLowerCase().equals("y")) {
                            startChallenge(client, input);
                        }
                    }
                    if (waitingResponse.get() > 0) {
                        waitingResponse.set(0);
                        startChallenge(client, input);
                    }

                    String message = input.readLine();
                    String[] messageFragments = message.split(" ");
                    switch (messageFragments[0]) {
                        case "register":
                            try {
                                serverObject.registerUser(messageFragments[1], messageFragments[2]);
                                System.out.println("Ok");
                            } catch (WQRegisterInterface.UserAlreadyRegisteredException e){
                                System.out.println("The given username already exists");
                            } catch (WQRegisterInterface.InvalidPasswordException e){
                                System.out.println("Please enter a valid password");
                            }
                            break;
                        case "login":
                            String toWrite = Consts.getRequestLogin(messageFragments[1], messageFragments[2]);
                            ByteBuffer byteBuffer = ByteBuffer.wrap(toWrite.getBytes(StandardCharsets.UTF_8));
                            while (byteBuffer.hasRemaining())
                                client.write(byteBuffer);
                            System.out.println(readResponse(client));
                            break;
                        case "logout":
                            toWrite = Consts.getRequestLogout(messageFragments[1]);
                            byteBuffer = ByteBuffer.wrap(toWrite.getBytes(StandardCharsets.UTF_8));
                            while (byteBuffer.hasRemaining())
                                client.write(byteBuffer);
                            System.out.println(readResponse(client));
                            break;
                        case "addFriend":
                            toWrite = Consts.getRequestAddFriend(messageFragments[1], messageFragments[2]);
                            byteBuffer = ByteBuffer.wrap(toWrite.getBytes(StandardCharsets.UTF_8));
                            while (byteBuffer.hasRemaining())
                                client.write(byteBuffer);
                            System.out.println(readResponse(client));
                            break;
                        case "challenge":
                            thisUser.replace(0, thisUser.length(), messageFragments[1]);
                            otherUser.replace(0, thisUser.length(), messageFragments[2]);
                            outgoingChallenge.set(true);
                            break;
                        //TODO: add missing commands
                        default:
                            System.out.println("Sorry, not recognized");
                    }
                }
            }catch (IOException e) {
                e.printStackTrace();
            }


        } catch (NotBoundException e) {
            e.printStackTrace();
        } catch (AccessException e) {
            e.printStackTrace();
        } catch (RemoteException e) {
            e.printStackTrace();
        }

    }

    private static String readResponse(SocketChannel client) throws IOException {
        ByteBuffer intBuffer = ByteBuffer.allocate(Consts.INT_SIZE);
        client.read(intBuffer);
        intBuffer.flip();

        ByteBuffer byteBuffer = ByteBuffer.allocate(intBuffer.getInt());
        byteBuffer.clear();

        //read response
        client.read(byteBuffer);
        byteBuffer.flip();
        return new String(byteBuffer.array(), 0, byteBuffer.remaining(), StandardCharsets.UTF_8);
    }

    private static void startChallenge(SocketChannel client, BufferedReader input) {
        byte[] byteMessage = Consts.REQUEST_READY_FOR_CHALLENGE.getBytes(StandardCharsets.UTF_8);
        ByteBuffer messageBuffer = ByteBuffer.wrap(byteMessage);

        try {
            //write ready for challenge
            while (messageBuffer.hasRemaining())
                client.write(messageBuffer);

            //prepare to read new word
            messageBuffer = ByteBuffer.allocate(Consts.MAX_MESSAGE_LENGTH);

            //read x words to be translated
            int i = 0;
            while(i < Consts.CHALLENGE_WORDS_TO_MATCH){
                client.read(messageBuffer);
                messageBuffer.flip();

                String[] messages = new String(messageBuffer.array(), 0, messageBuffer.remaining(), StandardCharsets.UTF_8).split("\n");
                for(String message: messages) {
                    String[] messageFragments = message.split(" ");
                    if (messageFragments[0].equals(Consts.RESPONSE_NEXT_WORD)) {
                        System.out.println("Next word to be translated is: " + messageFragments[2]);
                        String translatedWord = input.readLine();
                        String translationMessage = Consts.getTranslationResponseClient(Integer.parseInt(messageFragments[1]), messageFragments[2], translatedWord);
                        messageBuffer = ByteBuffer.wrap(translationMessage.getBytes(StandardCharsets.UTF_8));

                        //send translated word
                        while (messageBuffer.hasRemaining())
                            client.write(messageBuffer);

                    } else if (messageFragments[0].equals(Consts.CHALLENGE_OK)){
                        System.out.println("Last translation was correct!");
                    } else if (messageFragments[0].equals(Consts.CHALLENGE_WORD_MISMATCH)){
                        System.out.println("Last translation was incorrect");
                    } else if (messageFragments[0].equals("timeout")){
                        //TODO: add timeout case
                    }

                }
                i++;
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
