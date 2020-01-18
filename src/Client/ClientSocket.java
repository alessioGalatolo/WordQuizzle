package Client;

import Commons.WQRegisterInterface;
import Server.Consts;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

class ClientSocket implements AutoCloseable{
    private boolean test = false;
    private SocketChannel client;
    private UDPClient udpClient;
    private WQRegisterInterface serverObject;

    /**
     *
     * @param address The socket address of the server
     * @param udpClient udpClient used to send UDP requests
     * @param serverObject The remote object used for registration
     */
    ClientSocket(SocketAddress address, UDPClient udpClient, WQRegisterInterface serverObject) throws IOException {
        this.client = SocketChannel.open(address);
        this.udpClient = udpClient;
        this.serverObject = serverObject;
    }

    ClientSocket(SocketAddress address, UDPClient udpClient, WQRegisterInterface serverObject, boolean test) throws IOException {
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
                return Consts.RESPONSE_OK;
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
                    return Consts.CHALLENGE_OK;
                else
                    return Consts.CHALLENGE_REFUSED;
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


    private String readResponse() throws IOException {
        ByteBuffer intBuffer = ByteBuffer.allocate(Consts.INT_SIZE);
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

    @Override
    public void close() throws IOException {
        client.close();
    }

    WordIterator getWordIterator(String user) throws IOException {
        return new WordIterator(udpClient.getLatestMatchId(), user);
    }

    class WordIterator {
        private int wordIndex = 0;
        private boolean timeout = false;
        private int matchId;
        private String user;
        private String errors = "";

        WordIterator(int matchId, String user) throws IOException {
            byte[] byteMessage = (Consts.REQUEST_READY_FOR_CHALLENGE + " " + matchId + " " + user).getBytes(StandardCharsets.UTF_8);
            ByteBuffer messageBuffer = ByteBuffer.wrap(byteMessage);

            //write ready for challenge
            while (messageBuffer.hasRemaining())
                client.write(messageBuffer);
            this.matchId = matchId;
            this.user = user;
        }


        boolean hasNext() {
            return !timeout && wordIndex < Consts.CHALLENGE_WORDS_TO_MATCH + 1;
        }

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

            if(wordIndex < Consts.CHALLENGE_WORDS_TO_MATCH + 2) {
                //get new word
                String wholeMessage = readResponse();
                String[] messages = wholeMessage.split("\n");

                boolean lastWordCorrect = false; //true if last word was correct
                String lastWord = ""; //last word translated by the user
                String lastTranslatedWord = ""; //last word correct translation
                String nextWord = ""; //next word to be translated
                boolean errorOccurred = false;
                long timeRemaining = 0;

                for (String message : messages) {
                    if (message.equals(Consts.RESPONSE_CHALLENGE_TIMEOUT)) {
                        errors += message + "\n";
                        timeout = true;
                        errorOccurred = true;
                    }

                    String[] messageFragments = message.split(" ");
                    switch (messageFragments[0]) {
                        case Consts.RESPONSE_NEXT_WORD:
                            nextWord = messageFragments[2];
                            break;
                        case Consts.CHALLENGE_OK:
                            lastWordCorrect = true;
                            lastWord = messageFragments[2];
                            lastTranslatedWord = messageFragments[3];
                            break;
                        case Consts.CHALLENGE_WORD_MISMATCH:
                            lastWordCorrect = false;
                            lastWord = messageFragments[2];
                            StringBuilder stringBuilder = new StringBuilder();
                            for(int i = 3; i < messageFragments.length; i++) {
                                stringBuilder.append(messageFragments[i]).append(" ");
                            }
                            lastTranslatedWord = stringBuilder.toString();
                            break;
                        case Consts.RESPONSE_CHALLENGE_TIME:
                            timeRemaining = Long.parseLong(messageFragments[1]);
                        case Consts.RESPONSE_WAITING_OTHER_USER:
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

        String getErrors() {
            String error = errors;
            errors = "";
            return error;
        }

        String getRecap() throws IOException {
            if(hasNext())
                return null;
            String recapRequest = Consts.getRequestChallengeRecap(matchId, user);
            ByteBuffer messageBuffer = ByteBuffer.wrap(recapRequest.getBytes(StandardCharsets.UTF_8));

            //send translated word
            while (messageBuffer.hasRemaining())
                client.write(messageBuffer);

            return readResponse();
        }
    }

}
