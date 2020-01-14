package Client;

import Server.Consts;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

class WordIterator {
    private int wordIndex = 0;
    private boolean timeout = false;
    private SocketChannel client;
    private int matchId;
    private String user;
    private String recap = "";

    WordIterator(SocketChannel client, int matchId, String user) throws IOException {
        byte[] byteMessage = (Consts.REQUEST_READY_FOR_CHALLENGE + " " + matchId + " " + user).getBytes(StandardCharsets.UTF_8);
        ByteBuffer messageBuffer = ByteBuffer.wrap(byteMessage);

        //write ready for challenge
        while (messageBuffer.hasRemaining())
            client.write(messageBuffer);

        this.client = client;
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
            String translationMessage = Consts.getTranslationResponseClient(matchId, user, translatedWord);
            ByteBuffer messageBuffer = ByteBuffer.wrap(translationMessage.getBytes(StandardCharsets.UTF_8));

            //send translated word
            while (messageBuffer.hasRemaining())
                client.write(messageBuffer);
        }

        if(wordIndex < Consts.CHALLENGE_WORDS_TO_MATCH + 2) {
            //get new word
            String wholeMessage = readResponse();
            String[] messages = wholeMessage.split("\n");
            Match match = new Match();
            for (String message : messages) {
                if (message.equals(Consts.RESPONSE_CHALLENGE_TIMEOUT))
                    timeout = true;

                String[] messageFragments = message.split(" ");
                switch (messageFragments[0]) {
                    case Consts.RESPONSE_NEXT_WORD:
                        match.nextWord = messageFragments[2];
                        break;
                    case Consts.CHALLENGE_OK:
                        match.lastWordCorrect = true;
                        match.lastWord = messageFragments[2];
                        match.lastTranslatedWord = messageFragments[3];
                        break;
                    case Consts.CHALLENGE_WORD_MISMATCH:
                        match.lastWordCorrect = false;
                        match.lastWord = messageFragments[2];
                        match.lastTranslatedWord = messageFragments[3];
                        break;
                    default:
                        recap += message + "\n";
                        break;
                }

            }
            return match;
        }
        return null;
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
        return new String(byteBuffer.array(), 0, byteBuffer.remaining(), StandardCharsets.UTF_8);
    }

    String getRecap() {
        if(hasNext())
            return null;
        return recap;
    }

    class Match{
        private boolean lastWordCorrect = false; //true if last word was correct
        private String lastWord = ""; //last word translated by the user
        private String lastTranslatedWord = ""; //last word correct translation
        private String nextWord = ""; //next word to be translated

        boolean isLastWordCorrect() {
            return lastWordCorrect;
        }

        String getLastWord() {
            return lastWord;
        }

        String getLastTranslatedWord() {
            return lastTranslatedWord;
        }

        String getNextWord() {
            return nextWord;
        }
    }
}
