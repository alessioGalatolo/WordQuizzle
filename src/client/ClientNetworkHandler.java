package client;

import commons.WQRegisterInterface;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

import static client.Consts.CHALLENGE_TIMEOUT;
import static commons.Constants.*;

/**
 * A class that handles most of the actions of the client
 */
class ClientNetworkHandler implements AutoCloseable{
    private boolean test = false;
    private SocketChannel client;
    private UDPClient udpClient;
    private WQRegisterInterface serverObject;


    /**
     * @param address The socket address of the server
     * @param udpClient udpClient used to send UDP requests
     * @param serverObject The remote object used for registration
     */
    ClientNetworkHandler(SocketAddress address, UDPClient udpClient, WQRegisterInterface serverObject) throws IOException {
        this.client = SocketChannel.open(address);
        this.udpClient = udpClient;
        this.serverObject = serverObject;
    }

    /**
     * @param address The socket address of the server
     * @param udpClient udpClient used to send UDP requests
     * @param serverObject The remote object used for registration
     * @param test true will make the class write to console every interaction it has with the server
     */
    ClientNetworkHandler(SocketAddress address, UDPClient udpClient, WQRegisterInterface serverObject, boolean test) throws IOException {
        this(address, udpClient, serverObject);
        this.test = test;
    }

    /**
     * Gets a command type and sends the corresponding request to the server
     * @param command The command to be executed
     * @param user1 The user who made the request
     * @param user2 A second user, if relevant
     * @param pass Password of user1, if relevant
     * @return The response from the server
     */
    String handler(Command command, String user1, String user2, String pass) throws IOException, WQRegisterInterface.InvalidPasswordException, WQRegisterInterface.UserAlreadyRegisteredException {
        switch (command) {
            case REGISTER:
                serverObject.registerUser(user1, pass);
                return RESPONSE_OK;
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
                if (udpClient.requestChallenge(user1, user2))
                    return CHALLENGE_OK;
                else
                    return CHALLENGE_REFUSED;
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
        return readResponse();
    }

    /**
     * Writes through a socketChannel the requested string using nio
     * @param client The socket channel
     * @param requestString The string to write
     */
    private void writeRequest(SocketChannel client, String requestString) throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.wrap(requestString.getBytes(StandardCharsets.UTF_8));
        while (byteBuffer.hasRemaining())
            client.write(byteBuffer);

        byteBuffer.rewind();
        String message = new String(byteBuffer.array(), 0, byteBuffer.remaining(), StandardCharsets.UTF_8);
        if(test)
            System.out.println(Thread.currentThread().getName() + " wrote " + message);

    }


    /**
     * Reads a response from the server
     * @return The string read
     */
    private String readResponse() throws IOException {
        ByteBuffer intBuffer = ByteBuffer.allocate(INT_SIZE);
        client.read(intBuffer);
        intBuffer.flip();

        ByteBuffer byteBuffer = ByteBuffer.allocate(intBuffer.getInt());
        byteBuffer.clear();

        //read response
        client.read(byteBuffer);
        byteBuffer.flip();
        String message = new String(byteBuffer.array(), 0, byteBuffer.remaining(), StandardCharsets.UTF_8);
        if(test)
            System.out.println(Thread.currentThread().getName() + " read " + message);
        return message;
    }

    /**
     * @return a word iterator for the current challenge
     */
    WordIterator getWordIterator(String user) throws IOException {
        return new WordIterator(udpClient.getLatestMatchId(), user);
    }

    @Override
    public void close() throws IOException {
        client.close();
    }


    /**
     * A class that emulates the behaviour of an iterator of words in a challenge
     * Given a user translation it will return the next word to be translated
     */
    class WordIterator {
        private int wordIndex = 0;
        private boolean timeout = false; //challenge timeout
        private int matchId;
        private String user;
        private String errors = ""; //stores all unrecognised string received from server

        WordIterator(int matchId, String user) throws IOException {
            byte[] byteMessage = (REQUEST_READY_FOR_CHALLENGE + " " + matchId + " " + user).getBytes(StandardCharsets.UTF_8);
            ByteBuffer messageBuffer = ByteBuffer.wrap(byteMessage);

            //write ready for challenge
            while (messageBuffer.hasRemaining())
                client.write(messageBuffer);
            this.matchId = matchId;
            this.user = user;
        }


        /**
         * @return true if there are any other words to translate
         */
        boolean hasNext() {
            return !timeout && wordIndex < CHALLENGE_WORDS_TO_MATCH + 1;
        }

        /**
         * @param translatedWord The last word the user translated, may be null if not available
         * @return A match object with all the info needed
         */
        Match next(String translatedWord) throws IOException {
            wordIndex++;
            if (wordIndex > 1) {
                //send old translation
                String translationMessage = Consts.getRequestWordTranslation(matchId, user, translatedWord);
                ByteBuffer messageBuffer = ByteBuffer.wrap(translationMessage.getBytes(StandardCharsets.UTF_8));

                //send translated word
                while (messageBuffer.hasRemaining())
                    client.write(messageBuffer);
            }

            if(wordIndex < CHALLENGE_WORDS_TO_MATCH + 2) {
                //get new word
                String wholeMessage = readResponse();
                String[] messages = wholeMessage.split("\n");

                boolean lastWordCorrect = false; //true if last word was correct
                String lastWord = ""; //last word translated by the user
                String lastTranslatedWord = ""; //last word correct translation
                String nextWord = ""; //next word to be translated
                boolean errorOccurred = false;
                long timeRemaining = 0; //time remaining for the challenge

                for (String message : messages) {
                    if (message.equals(RESPONSE_CHALLENGE_TIMEOUT)) {
                        errors += CHALLENGE_TIMEOUT + "\n";
                        timeout = true;
                        errorOccurred = true;
                    }

                    String[] messageFragments = message.split(" ");
                    switch (messageFragments[0]) {
                        case RESPONSE_NEXT_WORD:
                            nextWord = messageFragments[2];
                            break;
                        case CHALLENGE_OK:
                            lastWordCorrect = true;
                            lastWord = messageFragments[2];
                            lastTranslatedWord = messageFragments[3];
                            break;
                        case CHALLENGE_WORD_MISMATCH:
                            lastWordCorrect = false;
                            lastWord = messageFragments[2];
                            StringBuilder stringBuilder = new StringBuilder();
                            for(int i = 3; i < messageFragments.length; i++) {
                                stringBuilder.append(messageFragments[i]).append(" ");
                            }
                            lastTranslatedWord = stringBuilder.toString();
                            break;
                        case RESPONSE_CHALLENGE_TIME:
                            timeRemaining = Long.parseLong(messageFragments[1]);
                        case RESPONSE_WAITING_OTHER_USER:
                            //waiting other user for final recap
                            break;
                        default:
                            errorOccurred = true;
                            errors += message + "\n";
                            break;
                    }

                }
                return new Match(lastWordCorrect, lastWord, lastTranslatedWord, nextWord, errorOccurred, timeRemaining);
            }
            return null;
        }

        /**
         * @return A string with all the error received separated by a new line
         */
        String getErrors() {
            String error = errors;
            errors = "";
            return error;
        }

        /**
         * @return A string containing a small recap of the past challenge
         */
        String getRecap() throws IOException {
            if(hasNext())
                return null;
            String recapRequest = Consts.getRequestChallengeRecap(matchId);
            ByteBuffer messageBuffer = ByteBuffer.wrap(recapRequest.getBytes(StandardCharsets.UTF_8));

            //send translated word
            while (messageBuffer.hasRemaining())
                client.write(messageBuffer);

            return readResponse();
        }
    }

}
