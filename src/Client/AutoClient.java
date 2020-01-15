package Client;

import Commons.WQRegisterInterface;
import Server.Consts;
import Server.WQRegister;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.SocketAddress;
import java.net.URL;
import java.nio.channels.SocketChannel;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import static Client.HumanClient.handler;
import static Client.HumanClient.readResponse;

public class AutoClient {

    public static void main(String[] args) {

        AtomicBoolean incomingChallenge = new AtomicBoolean(false);
        AtomicBoolean userBusy = new AtomicBoolean(false);
        Random random = new Random(System.currentTimeMillis());

        //socket init
        SocketAddress address = new InetSocketAddress(Consts.TCP_PORT);

        String currentLoggedUser = Consts.BASE_USERNAME;
        try {
            currentLoggedUser = args[0]; //retrieves the username
        }catch (IndexOutOfBoundsException e){}


        try(SocketChannel client = SocketChannel.open(address);
        UDPClient udpClient = new UDPClient(incomingChallenge, userBusy, (String otherUser) -> {
            //function to be used when a new challenge message arrives
            //returns whether or not the challenge has been accepted
            return random.nextBoolean();
        })
        ){
            Registry r = LocateRegistry.getRegistry(Consts.RMI_PORT);
            WQRegisterInterface serverObject = (WQRegisterInterface) r.lookup(Consts.WQ_STUB_NAME); //get remote object

            String pass = "password";

            try {
                serverObject.registerUser(currentLoggedUser, pass);
            } catch (WQRegister.UserAlreadyRegisteredException ignored) {} //no problem

            handler(client, udpClient, serverObject, Command.LOGIN, currentLoggedUser, "", pass);
            readResponse(client);

            for(int i = 0; i < Consts.CLIENT_TESTS; i++){
                if (incomingChallenge.get()) {
                    incomingChallenge.set(false);
                    WordIterator wordIterator = new WordIterator(client, udpClient.getLatestMatchId(), currentLoggedUser);
                    startChallenge(wordIterator, userBusy, random);
                }

                Command command = Command.getRandomCommand();

                var outcome = handler(client, udpClient, serverObject, command, currentLoggedUser, Consts.getRandomUserName(currentLoggedUser), pass);
                String response;

                if (command != Command.CHALLENGE) {
                    response = readResponse(client);
                    outcome = response.startsWith(Consts.RESPONSE_OK);
                }

                if (outcome && command == Command.CHALLENGE) {
                    WordIterator wordIterator = new WordIterator(client, udpClient.getLatestMatchId(), currentLoggedUser);
                    startChallenge(wordIterator, userBusy, random);
                }

            }

            handler(client, udpClient, serverObject, Command.LOGOUT, currentLoggedUser, "", pass);
            readResponse(client);

        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("User " + currentLoggedUser + " has logged out");
    }

    private static void startChallenge(WordIterator wordIterator, AtomicBoolean userBusy, Random random) throws IOException {
        String lastTranslation = null;

        while (wordIterator.hasNext()){
            WordIterator.Match match = wordIterator.next(lastTranslation);
            if(match != null) {
                if (!match.getNextWord().isBlank()) {
                    lastTranslation = random.nextBoolean() ? Consts.NOT_A_WORD: getTranslation(match.getNextWord());
                }
            }
        }

        wordIterator.getRecap();
        userBusy.set(false);
    }

    private static String getTranslation(String nextWord) {
        try {
            URL url = new URL(Consts.getTranslationURL(nextWord));
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
        return Consts.NOT_A_WORD;
    }

}
