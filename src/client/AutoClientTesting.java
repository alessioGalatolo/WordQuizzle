package client;

import commons.WQRegisterInterface;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.SocketAddress;
import java.net.URL;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import static commons.Constants.*;

/**
 * A randomized client that sends random requests to the server
 *
 * May get as first argument the username to be used
 * And if a second argument is found it will activate testing mode
 * making the client print every interaction with the server
 */
public class AutoClientTesting {

    public static void main(String[] args){
        AtomicBoolean incomingChallenge = new AtomicBoolean(false);
        AtomicBoolean userBusy = new AtomicBoolean(false);
        Random random = new Random(System.currentTimeMillis());

        //socket init
        SocketAddress address = new InetSocketAddress(TCP_PORT);

        String currentLoggedUser = BASE_USERNAME;
        boolean test = false;
        try {
            currentLoggedUser = args[0]; //retrieves the username
            test = !args[1].isEmpty(); //set test variable
        }catch (IndexOutOfBoundsException ignored){}



        try(UDPClient udpClient = new UDPClient(incomingChallenge, userBusy, (String otherUser) -> random.nextBoolean());
        ClientNetworkHandler clientSocket = new ClientNetworkHandler(address, udpClient, null, test)
        ){
            Registry r = LocateRegistry.getRegistry(RMI_PORT);
            WQRegisterInterface serverObject = (WQRegisterInterface) r.lookup(WQ_STUB_NAME); //get remote object

            String pass = "password";

            try {
                serverObject.registerUser(currentLoggedUser, pass);
            } catch (WQRegisterInterface.UserAlreadyRegisteredException ignored) {} //no problem

            clientSocket.handler(Command.LOGIN, currentLoggedUser, "", pass);

            for(int i = 0; i < CLIENT_TESTS; i++){
                if (incomingChallenge.get()) {
                    incomingChallenge.set(false);
                    startChallenge(clientSocket.getWordIterator(currentLoggedUser), userBusy, random);
                }

                Command command = Command.getRandomCommand();

                String response = clientSocket.handler(command, currentLoggedUser, getRandomUserName(currentLoggedUser), pass);

                boolean outcome = response.startsWith(RESPONSE_OK);

                if (outcome && command == Command.CHALLENGE) {
                    startChallenge(clientSocket.getWordIterator(currentLoggedUser), userBusy, random);
                }

            }

            clientSocket.handler(Command.LOGOUT, currentLoggedUser, "", pass);
            System.out.println("User " + currentLoggedUser + " has logged out");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Starts and follows the progress of the challenge.
     * It will send the server either a correct or wrong answer randomly
     * @param userBusy a bool to set when the user has finished the challenge
     * @param random a random generator
     */
    static void startChallenge(ClientNetworkHandler.WordIterator wordIterator, AtomicBoolean userBusy, Random random) throws IOException {
        String lastTranslation = null;

        while (wordIterator.hasNext()){
            Match match = wordIterator.next(lastTranslation);
            if(match != null) {
                if (!match.getNextWord().isBlank()) {
                    lastTranslation = random.nextBoolean() ? NOT_A_WORD: getTranslation(match.getNextWord());
                }
            }
        }

        wordIterator.getRecap();
        userBusy.set(false);
    }

    /**
     * Get an online translation the the given word
     * @param nextWord The word to translate
     * @return The correct translation
     */
    private static String getTranslation(String nextWord) {
        try {
            URL url = new URL(getTranslationURL(nextWord));
            try(var inputStream = new BufferedReader(new InputStreamReader(url.openStream()))){

                StringBuilder stringBuilder = new StringBuilder();
                Gson gson = new Gson();
                String line;

                while((line = inputStream.readLine()) != null){
                    stringBuilder.append(line);
                }
                JsonObject jsonObject = gson.fromJson(stringBuilder.toString(), JsonObject.class);

                String translations;
                translations = jsonObject.get("responseData").getAsJsonObject().get("translatedText").getAsString();

                return translations;

            }catch (IOException e){
                e.printStackTrace();
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return NOT_A_WORD;
    }

}
