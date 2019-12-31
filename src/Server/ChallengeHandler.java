package Server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.GregorianCalendar;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.Math.abs;

//TODO: check thread-safety
class ChallengeHandler {

    private static ChallengeHandler singleInstance = null;

    private static String[] dictionary = null;
    private static ConcurrentHashMap<Integer, Challenge> activeChallenges = new ConcurrentHashMap<>();
    private static ConcurrentHashMap<Integer, Challenge> pastChallenges = new ConcurrentHashMap<>();

    /**
     * Creates ChallengeHandler instance if necessary
     * Follows singleton pattern
     * @return The instance of the ChallengeHandler
     */
    static ChallengeHandler getInstance(){
        if(singleInstance == null){
            singleInstance = new ChallengeHandler();
        }
        return singleInstance;
    }

    //private constructor that get the default filename
    private ChallengeHandler(){
        this(Consts.DICTIONARY_FILENAME);
    }

    /**
     * Initializes the challenges dictionary from file
     * Follows singleton pattern
     * @param filename File containing the dictionary. Must have the size of the dictionary as a string in the first line
     */
    private ChallengeHandler(String filename){
        try {
            FileChannel inChannel = FileChannel.open(Paths.get(filename)); //file has a word per line
            int dictionaryIndex = 0;
            int dictionaryLength;
            ByteBuffer wordBuffer = ByteBuffer.allocate(Consts.ITALIAN_WORD_MAX_LENGTH + 1);
            String words; //stores the string from the buffer
            String[] wordsFragments; //stores the different lines contained in 'words'

            //first line must contain the size of the dictionary as a string
            //reading first lines
            inChannel.read(wordBuffer);
            wordBuffer.flip();
            words = new String(wordBuffer.array(), 0, wordBuffer.limit(), StandardCharsets.UTF_8);
            wordsFragments = words.split("\n");
            dictionaryLength = Integer.parseInt(wordsFragments[0]); //first lines is the size

            //create the dictionary
            dictionary = new String[dictionaryLength];

            //TODO: improve code repetition
            //store in the dictionary the overflowing words caught with the first read
            for(int i = 1; i < wordsFragments.length; i++){
                dictionary[dictionaryIndex] = wordsFragments[i];
                dictionaryIndex++;
            }

            //last word read was incomplete
            if(words.charAt(words.length() - 1) != '\n')
                dictionaryIndex--; //next time add to last word


            //read all file
            while (inChannel.read(wordBuffer) != -1){
                wordBuffer.flip();
                words = new String(wordBuffer.array(), 0, wordBuffer.limit(), StandardCharsets.UTF_8);
                wordsFragments = words.split("\n");

                //if first character is a newline then last word read is complete
                if(words.charAt(0) == '\n')
                    dictionaryIndex++;

                for(String word: wordsFragments){
                    if(!word.isBlank()) {
                        if (dictionary[dictionaryIndex] == null)
                            dictionary[dictionaryIndex] = word;
                        else
                            dictionary[dictionaryIndex] += word;

                        dictionaryIndex++;
                    }
                }

                if(words.charAt(words.length() - 1) != '\n')
                    dictionaryIndex--;

            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (ArrayIndexOutOfBoundsException e){
            System.err.println("Given dictionary size is lower than actual size");
        }
    }

    /**
     * @param matchId The current match ID
     * @param user The user request a new word
     * @return The word to be translated
     */
    String getNextWord(int matchId, String user) throws Challenge.EndOfMatchException {
        return activeChallenges.get(matchId).getNextWord(user);
        //TODO: start challenge
    }

    /**
     * Creates a challenge and adds it to the list
     * @param user1 First user of the challenge
     * @param user2 Second user of the challenge
     * @return The id of the challenge
     */
    int createChallenge(String user1, String user2){
        Challenge newChallenge = new Challenge(user1, user2, dictionary);
        activeChallenges.put(newChallenge.getId(), newChallenge);
        return newChallenge.getId();
    }

    /**
     * Updates challenge score based on the correctness of the translation
     * @param matchId The id of the match
     * @param user The user who sent the word
     * @param translatedWord The user-translated word
     * @return True if the translation is correct, false otherwise
     * @throws Challenge.GameTimeoutException if the user time has expired
     */
    boolean checkTranslatedWord(int matchId, String user, String translatedWord) throws Challenge.GameTimeoutException {
        Challenge challenge = activeChallenges.get(matchId);
        String originalWord = challenge.getLastWord(user);
        if(wellTranslated(originalWord, translatedWord)){
            challenge.updateScore(user, Consts.WIN_SCORE_AMOUNT);
            return true;
        }else{
            challenge.updateScore(user, Consts.LOSE_SCORE_AMOUNT);
            return false;
        }
    }

    /**
     * Checks the match between the user-translated word and the online translation
     * @param originalWord The original word to be translated
     * @param translatedWord The user translation
     * @return True if the translation is correct, false otherwise
     */
    private boolean wellTranslated(String originalWord, String translatedWord) {
        try {
            URL url = new URL(Consts.getTranslationURL(originalWord));
            try(var inputStream = new BufferedReader(new InputStreamReader(url.openStream()))){
                StringBuilder stringBuilder = new StringBuilder();
                Gson gson = new Gson();
                String line;

                while((line = inputStream.readLine()) != null){
                    stringBuilder.append(line);
                }
                JsonObject jsonObject = gson.fromJson(stringBuilder.toString(), JsonObject.class);

                //TODO: change score based on Levenshtein distance
                return jsonObject.get("responseData").getAsJsonObject().get("translatedText").getAsString().toLowerCase().equals(translatedWord.toLowerCase());



            }catch (IOException e){
                e.printStackTrace();
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Class that represents a challenge, it stores all the useful info
     * and gives some basic operations
     */
    static class Challenge{
        //counter is shared between multiple threads and instances
        private static final AtomicInteger idCounter = new AtomicInteger(0); //every challenge has its id assigned at construction time
        private int id;
        private String user1;
        private String user2;
        private long user1Timestamp = 0;
        private long user2Timestamp = 0;
        private int user1Index = 0;
        private int user2Index = 0;
        private int user1Score = 0;
        private int user2Score = 0;
        private String[] selectedWords = new String[Consts.CHALLENGE_WORDS_TO_MATCH]; //contains selected words
        private static Random random = new Random(GregorianCalendar.getInstance().getTimeInMillis()); //random generator to get the challenge words

        Challenge(String user1, String user2, String[] dictionary){
            this.user1 = user1;
            this.user2 = user2;
            id = idCounter.getAndIncrement();

            for(int i = 0; i < selectedWords.length; i++){
                selectedWords[i] = dictionary[abs(random.nextInt() % dictionary.length)];
            }
        }

        int getId() {
            return id;
        }


        //TODO: add time check
        String getNextWord(String user) throws EndOfMatchException {
            if(user.equals(user1)) {
                if(user1Timestamp == 0)
                    user1Timestamp = System.currentTimeMillis();
                if(user1Index < Consts.CHALLENGE_WORDS_TO_MATCH)
                    return selectedWords[user1Index++];
                else
                    throw new EndOfMatchException();
            }else if(user.equals(user2)) {
                if (user2Timestamp == 0)
                    user2Timestamp = System.currentTimeMillis();
                return selectedWords[user2Index++];
            }
            return null; //no username match
            //TODO: throw exception
        }

        String getLastWord(String user) {
            if(user.equals(user1))
                return selectedWords[user1Index - 1];
            else if(user.equals(user2))
                return selectedWords[user2Index - 1];
            return null; //no username match
            //TODO: throw exception
        }

        void updateScore(String user, int amount) throws GameTimeoutException {
            if(user.equals(user1)) {
                if(System.currentTimeMillis() - user1Timestamp < Consts.CHALLENGE_TIMEOUT)
                    user1Score += amount;
                else
                    throw new GameTimeoutException();
            }
            else if(user.equals(user2)) {
                if(System.currentTimeMillis() - user2Timestamp < Consts.CHALLENGE_TIMEOUT)
                    user2Score += amount;
                else
                    throw new GameTimeoutException();
            }
            //TODO: throw exception for user not found
        }

        class GameTimeoutException extends Exception{
        }

        class EndOfMatchException extends Exception{
        }
    }

}
