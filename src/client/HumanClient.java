package client;

import commons.WQRegisterInterface;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.concurrent.atomic.AtomicBoolean;

import static commons.Constants.*;

/**
 * The final client that will be used by a user
 */
public class HumanClient {

    public static void main(String[] args) {
        try {

            boolean test = false;
            try {
                test = !args[0].isEmpty();
            }catch (IndexOutOfBoundsException ignored){}


            Registry r = LocateRegistry.getRegistry(RMI_PORT);
            WQRegisterInterface serverObject = (WQRegisterInterface) r.lookup(WQ_STUB_NAME); //get remote object

            AtomicBoolean incomingChallenge = new AtomicBoolean(false);
            AtomicBoolean userBusy = new AtomicBoolean(false);

            String currentLoggedUser = null;

            //socket init
            SocketAddress address = new InetSocketAddress(TCP_PORT);

            System.out.println(Command.usage());

            try(BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
                UDPClient udpClient = new UDPClient(incomingChallenge, userBusy, test, (String otherUser) -> {
                    //function to be used when a new challenge message arrives
                    //returns whether or not the challenge has been accepted
                    try {
                        System.out.println("Incoming challenge from " + otherUser + ", accept? (y/n)");
                        String response = input.readLine();
                        return response.toLowerCase().equals("y");
                    }catch (IOException e){
                        e.printStackTrace();
                    }
                    return false;});
                ClientNetworkHandler clientSocket = new ClientNetworkHandler(address, udpClient, serverObject, test)){

                boolean quit = false;
                while (!quit) {
                    try {
                        if (incomingChallenge.get()) {
                            incomingChallenge.set(false);
                            startChallenge(clientSocket.getWordIterator(currentLoggedUser), userBusy, input);
                        }


                        if(input.ready()) {
                            String message = input.readLine();
                            String[] messageFragments = message.split(" ");

                            Command command = null;
                            String user1 = "";
                            String user2 = "";
                            String pass = "";


                            switch (messageFragments[0]) {
                                case REQUEST_REGISTER:
                                    command = Command.REGISTER;
                                    user1 = messageFragments[1];
                                    pass = messageFragments[2];
                                    break;
                                case REQUEST_LOGIN:
                                    command = Command.LOGIN;
                                    user1 = messageFragments[1];
                                    pass = messageFragments[2];
                                    break;
                                case REQUEST_LOGOUT:
                                    command = Command.LOGOUT;
                                    user1 = messageFragments[1];
                                    break;
                                case REQUEST_ADD_FRIEND:
                                    command = Command.ADD_FRIEND;
                                    user1 = messageFragments[1];
                                    user2 = messageFragments[2];
                                    break;
                                case REQUEST_CHALLENGE:
                                    command = Command.CHALLENGE;
                                    user1 = messageFragments[1];
                                    user2 = messageFragments[2];
                                    break;
                                case REQUEST_RANKINGS:
                                    command = Command.RANKING;
                                    user1 = messageFragments[1];
                                    break;
                                case REQUEST_SCORE:
                                    command = Command.SCORE;
                                    user1 = messageFragments[1];
                                    break;
                                case REQUEST_FRIEND_LIST:
                                    command = Command.FRIENDS;
                                    user1 = messageFragments[1];
                                    break;
                                case REQUEST_TERMINATION:
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
                                if (command != null) {

                                    if (currentLoggedUser != null && !currentLoggedUser.equals(user1)) {
                                        System.err.println("You tried to use an operation as a different user than the one you are logged in\nPlease try again");
                                        continue;
                                    }

                                    if(command == Command.CHALLENGE)
                                        System.out.println("Sending request...");

                                    String response = clientSocket.handler(command, user1, user2, pass);
                                    boolean outcome = response.startsWith(RESPONSE_OK);

                                    if (outcome)
                                        switch (command) {
                                            case LOGIN:
                                                currentLoggedUser = user1;
                                                break;
                                            case LOGOUT:
                                                currentLoggedUser = null;
                                                break;
                                            case CHALLENGE:
                                                startChallenge(clientSocket.getWordIterator(user1), userBusy, input);
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

    /**
     * Starts and follows the progress of the challenge.
     * It will get through input the translation to the words
     * @param userBusy a bool to set when the user has finished the challenge
     * @param input a buffered reader loaded on the system input
     */
    private static void startChallenge(ClientNetworkHandler.WordIterator wordIterator, AtomicBoolean userBusy, BufferedReader input) throws IOException {
        String lastTranslation = null;

        System.out.println("The challenge has started!");

        while (wordIterator.hasNext()){
            Match match = wordIterator.next(lastTranslation);
            if(match != null) {
                if(match.getTimeRemaining() != 0)
                   System.out.println("Time remaining for the challenge: " + match.getTimeRemaining() + " s");
                if(match.getErrorOccurred()){
                    System.out.println("Error: " + wordIterator.getErrors());
                }
                if (!match.getLastWord().isBlank()) {
                    String outcome = match.isLastWordCorrect() ? "correct" : "incorrect";
                    System.out.println("Last translation was " + outcome);
                    System.out.println("Your translation was " + match.getLastWord() + " and correct translation is: " + match.getLastTranslatedWord());
                }
                if (!match.getNextWord().isBlank()) {
                    System.out.println("Next word to be translated is: " + match.getNextWord());
                    lastTranslation = input.readLine();
                }
            }
        }

        System.out.println("Waiting other user for recap");
        System.out.println(wordIterator.getRecap());
        userBusy.set(false);
    }
}
