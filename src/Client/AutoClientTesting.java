package Client;

import Commons.WQRegisterInterface;
import Server.Consts;
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


public class AutoClientTesting {

    public static void main(String[] args){
        AtomicBoolean incomingChallenge = new AtomicBoolean(false);
        AtomicBoolean userBusy = new AtomicBoolean(false);
        Random random = new Random(System.currentTimeMillis());

        //socket init
        SocketAddress address = new InetSocketAddress(Consts.TCP_PORT);

        String currentLoggedUser = Consts.BASE_USERNAME;
        boolean test = false;
        try {
            currentLoggedUser = args[0]; //retrieves the username
            test = !args[1].isEmpty();
        }catch (IndexOutOfBoundsException ignored){}



        try(
        UDPClient udpClient = new UDPClient(incomingChallenge, userBusy, (String otherUser) -> {
            //function to be used when a new challenge message arrives
            //returns whether or not the challenge has been accepted
            return random.nextBoolean();
        });
        ClientSocket clientSocket = new ClientSocket(address, udpClient, null, test)){
            Registry r = LocateRegistry.getRegistry(Consts.RMI_PORT);
            WQRegisterInterface serverObject = (WQRegisterInterface) r.lookup(Consts.WQ_STUB_NAME); //get remote object

            String pass = "password";

            try {
                serverObject.registerUser(currentLoggedUser, pass);
            } catch (WQRegisterInterface.UserAlreadyRegisteredException ignored) {} //no problem

            clientSocket.handler(Command.LOGIN, currentLoggedUser, "", pass);

            for(int i = 0; i < Consts.CLIENT_TESTS; i++){
                if (incomingChallenge.get()) {
                    incomingChallenge.set(false);
                    startChallenge(clientSocket.getWordIterator(currentLoggedUser), userBusy, random);
                }

                Command command = Command.getRandomCommand();

                String response = clientSocket.handler(command, currentLoggedUser, Consts.getRandomUserName(currentLoggedUser), pass);

                boolean outcome = response.startsWith(Consts.RESPONSE_OK);

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

    static void startChallenge(ClientSocket.WordIterator wordIterator, AtomicBoolean userBusy, Random random) throws IOException {
        String lastTranslation = null;

        while (wordIterator.hasNext()){
            Match match = wordIterator.next(lastTranslation);
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
