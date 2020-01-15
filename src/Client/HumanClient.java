package Client;

import Commons.WQRegisterInterface;
import Server.Consts;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.concurrent.atomic.AtomicBoolean;

public class HumanClient {

    public static void main(String[] args) {
        try {
            Registry r = LocateRegistry.getRegistry(Consts.RMI_PORT);
            WQRegisterInterface serverObject = (WQRegisterInterface) r.lookup(Consts.WQ_STUB_NAME); //get remote object

            AtomicBoolean incomingChallenge = new AtomicBoolean(false);
            AtomicBoolean userBusy = new AtomicBoolean(false);

            String currentLoggedUser = null;

            //socket init
            SocketAddress address = new InetSocketAddress(Consts.TCP_PORT);

            try(BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
                SocketChannel client = SocketChannel.open(address);
                UDPClient udpClient = new UDPClient(incomingChallenge, userBusy, (String otherUser) -> {
                    //function to be used when a new challenge message arrives
                    //returns whether or not the challenge has been accepted
                    try {
                        System.out.println("Incoming challenge from " + otherUser + ", accept? (y/n)");
                        String response = input.readLine();
                        return response.toLowerCase().equals("y");
                    }catch (IOException e){
                        e.printStackTrace();
                    }
                    return false;})
            ){

                boolean quit = false;
                while (!quit) {
                    try {
                        if (incomingChallenge.get()) {
                            incomingChallenge.set(false);
                            WordIterator wordIterator = new WordIterator(client, udpClient.getLatestMatchId(), currentLoggedUser);
                            startChallenge(wordIterator, userBusy, input);
                        }

                        //TODO: avoid active wait
                        if (input.ready()) {
                            String message = input.readLine();
                            String[] messageFragments = message.split(" ");

                            Command command = null;
                            String user1 = "";
                            String user2 = "";
                            String pass = "";


                            switch (messageFragments[0]) {
                                case "register":
                                    command = Command.REGISTER;
                                    user1 = messageFragments[1];
                                    pass = messageFragments[2];
                                    break;
                                case "login":
                                    command = Command.LOGIN;
                                    user1 = messageFragments[1];
                                    pass = messageFragments[2];
                                    break;
                                case "logout":
                                    command = Command.LOGOUT;
                                    user1 = messageFragments[1];
                                    break;
                                case "addFriend":
                                    command = Command.ADD_FRIEND;
                                    user1 = messageFragments[1];
                                    user2 = messageFragments[2];
                                    break;
                                case "challenge":
                                    command = Command.CHALLENGE;
                                    user1 = messageFragments[1];
                                    user2 = messageFragments[2];
                                    break;
                                case "ranking":
                                    command = Command.RANKING;
                                    user1 = messageFragments[1];
                                    break;
                                case "score":
                                    command = Command.SCORE;
                                    user1 = messageFragments[1];
                                    break;
                                case "friends":
                                    command = Command.FRIENDS;
                                    user1 = messageFragments[1];
                                    break;
                                case "quit":
                                    if (currentLoggedUser != null)
                                        System.out.println("You must logout before quitting");
                                    else
                                        quit = true;
                                    break;
                                default:
                                    System.out.println("Sorry, not recognized");
                                    System.out.println(Command.usage());
                            }
                            try {
                                if(command != null) {
                                    if(currentLoggedUser != null && !currentLoggedUser.equals(user1)){
                                        System.err.println("You tried to use an operation as a different user than the one you are logged in\nPlease try again");
                                        continue;
                                    }
                                    var outcome = handler(client, udpClient, serverObject, command, user1, user2, pass);
                                    String response = "";

                                    if (command != Command.CHALLENGE && command != Command.REGISTER) {
                                        response = readResponse(client);
                                        outcome = response.startsWith(Consts.RESPONSE_OK);
                                    }

                                    if (outcome)
                                        switch (command) {
                                            case LOGIN:
                                                currentLoggedUser = user1;
                                                break;
                                            case LOGOUT:
                                                currentLoggedUser = null;
                                                break;
                                            case CHALLENGE:
                                                WordIterator wordIterator = new WordIterator(client, udpClient.getLatestMatchId(), user1);
                                                startChallenge(wordIterator, userBusy, input);
                                            case REGISTER:
                                                System.out.println(Consts.RESPONSE_OK);
                                                break;
                                        }

                                    System.out.println(response);
                                }

                            } catch (WQRegisterInterface.InvalidPasswordException e) {
                                System.out.println("Please enter a valid password");
                            } catch (WQRegisterInterface.UserAlreadyRegisteredException e) {
                                System.out.println("The given username already exists");
                            }
                        }
                    } catch (IndexOutOfBoundsException e) {
                        System.err.println("Wrong syntax");
                        System.err.println(Command.usage());
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (NotBoundException | RemoteException e) {
            e.printStackTrace();
        }

    }

    private static void startChallenge(WordIterator wordIterator, AtomicBoolean userBusy, BufferedReader input) throws IOException {
        String lastTranslation = null;

        while (wordIterator.hasNext()){
            WordIterator.Match match = wordIterator.next(lastTranslation);
            if(match != null) {
                if (!match.getLastWord().isBlank()) {
                    String outcome = match.isLastWordCorrect() ? "correct" : "incorrect";
                    System.out.println("Last translation was " + outcome);
                    System.out.println("Your translation was " + match.getLastWord() + " and correct translation is " + match.getLastTranslatedWord());
                }
                if (!match.getNextWord().isBlank()) {
                    System.out.println("Next word to be translated is: " + match.getNextWord());
                    lastTranslation = input.readLine();
                }
            }
        }

        System.out.println(wordIterator.getRecap());
        userBusy.set(false);
    }

    /**
     * Gets a command type and sends the corresponding request to the server
     * @param client A blocking socketChannel to use to send TCP requests
     * @param udpClient udpClient used to send UDP requests
     * @param serverObject The remote object used for registration
     * @param command The command to be executed
     * @param user1 The user who made the request
     * @param user2 A second user, if relevant
     * @param pass Password of user1, if relevant
     * @return The outcome of the operation
     */
    static boolean handler(SocketChannel client, UDPClient udpClient, WQRegisterInterface serverObject, Command command, String user1, String user2, String pass) throws IOException, WQRegisterInterface.InvalidPasswordException, WQRegisterInterface.UserAlreadyRegisteredException {
        switch (command) {
            case REGISTER:
                    serverObject.registerUser(user1, pass);
                break;
            case LOGIN:
                writeRequest(client, Consts.getRequestLogin(user1, pass, udpClient.getUDPPort()));
                break;
            case LOGOUT:
                writeRequest(client, Consts.getRequestLogout(user1));
                break;
            case ADD_FRIEND:
                writeRequest(client, Consts.getRequestAddFriend(user1, user2));
                break;
            case CHALLENGE:
                if (!udpClient.requestChallenge(user1, user2))
                    return false;
                break;
            case RANKING:
                writeRequest(client, Consts.getRequestRankings(user1));
                break;
            case SCORE:
                writeRequest(client, Consts.getRequestScore(user1));
                break;
            case FRIENDS:
                writeRequest(client, Consts.getRequestFriends(user1));
                break;
        }
        return true;
    }

    private static void writeRequest(SocketChannel client, String requestString) throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.wrap(requestString.getBytes(StandardCharsets.UTF_8));
        while (byteBuffer.hasRemaining())
            client.write(byteBuffer);

        byteBuffer.rewind();
        String message = new String(byteBuffer.array(), 0, byteBuffer.remaining(), StandardCharsets.UTF_8);
        System.out.println(Thread.currentThread().getName() + " wrote " + message);

    }

    static String readResponse(SocketChannel client) throws IOException {
        ByteBuffer intBuffer = ByteBuffer.allocate(Consts.INT_SIZE);
        client.read(intBuffer);
        intBuffer.flip();

        ByteBuffer byteBuffer = ByteBuffer.allocate(intBuffer.getInt());
        byteBuffer.clear();

        //read response
        client.read(byteBuffer);
        byteBuffer.flip();
        String message = new String(byteBuffer.array(), 0, byteBuffer.remaining(), StandardCharsets.UTF_8);
        System.out.println(Thread.currentThread().getName() + " read " + message);
        return message;
    }
}
