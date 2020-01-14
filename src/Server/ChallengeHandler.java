package Server;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
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
/**
 * A class that handles the challenges: Creates all the challenges, keeps
 * their score and their state
 *
 * Uses singleton pattern, object of class is created when it is loaded to the memory by JVM
 *
 * Thread safety is assured by the use of a concurrent hash map
 */
class ChallengeHandler {

    static ChallengeHandler instance = new ChallengeHandler();

    private String[] dictionary = null;
    private static ConcurrentHashMap<Integer, Challenge> activeChallenges = new ConcurrentHashMap<>();


    /**
     * private constructor that uses the default filename for the dictionary
     */
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
    String getNextWord(int matchId, String user) throws Challenge.EndOfMatchException, Challenge.UnknownUsernameException {
        return activeChallenges.get(matchId).getNextWord(user);
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
     * Gets a recap of the match
     * @param matchId The match id
     * @return A string with the recap
     */
    String getRecap(int matchId) {
        return activeChallenges.get(matchId).toString();
    }


    /**
     * Checks the correctness of the translation and updates the user score
     * @param matchId The match id
     * @param user The user who sent the translation
     * @param userTranslatedWord The word translated by the user
     * @return The translation of the word
     * @throws Challenge.UnknownUsernameException When the given user is not in the challenge
     * @throws Challenge.GameTimeoutException If the user time has expired
     */
    String checkTranslation(int matchId, String user, String userTranslatedWord) throws Challenge.UnknownUsernameException, Challenge.GameTimeoutException {
        Challenge challenge = activeChallenges.get(matchId);
        String[] translatedWord = challenge.getUserLastWordTranslation(user);
        String correctTranslation = translatedWord[0];
        boolean outcome = false;
        int i = 0;
        while (i < translatedWord.length && !outcome){
            if(userTranslatedWord.equals(translatedWord[i])) {
                outcome = true;
                correctTranslation = translatedWord[i];
            }
            i++;
        }
        if(outcome){
            challenge.updateScore(user, Consts.WIN_SCORE_AMOUNT);
        }else{
            challenge.updateScore(user, Consts.LOSE_SCORE_AMOUNT);
        }
        return correctTranslation;
    }

    int getScore(int matchId, String user) throws Challenge.UnknownUsernameException {
        return activeChallenges.get(matchId).getScore(user);
    }

    boolean challengeIsFinished(int matchId) {
        return activeChallenges.get(matchId).finished;
    }

    /**
     * Class that represents a challenge, it stores all the useful info
     * and gives some basic operations.
     *
     * Thread safety is guaranteed by the use of synchronized methods
     * although a challenge should never be accessed by multiple threads with this implementation
     */
    static class Challenge{
        //counter is shared between multiple threads and instances
        private static final AtomicInteger idCounter = new AtomicInteger(0); //every challenge has its id assigned at construction time

        private int id; //challenge id
        private String user1; //first user of the challenge
        private String user2; //second user of the challenge
        private long user1Timestamp = 0; //time at which the user1 started the challenge
        private long user2Timestamp = 0; //time at which the user2 started the challenge
        private int user1CompletedWords = 0;
        private int user2CompletedWords = 0;
        private int user1Score = 0;
        private int user2Score = 0;
        private boolean finished = false; //stores whether or not the challenge is over
        private String[] selectedWords = new String[Consts.CHALLENGE_WORDS_TO_MATCH]; //contains selected words to be translated
        private String[][] translatedWords = new String[Consts.CHALLENGE_WORDS_TO_MATCH][Consts.MAX_TRANSLATIONS_PER_WORD];
        private static Random random = new Random(GregorianCalendar.getInstance().getTimeInMillis()); //random generator to get the challenge words

        /**
         * Creates a challenge and assigns it a new ID
         * @param user1 The first user involved in the challenge
         * @param user2 The first user involved in the challenge
         * @param dictionary An array of words from which to pick the ones needed for the challenge
         */
        Challenge(String user1, String user2, String[] dictionary){
            this.user1 = user1;
            this.user2 = user2;
            id = idCounter.getAndIncrement();

            for(int i = 0; i < selectedWords.length; i++){
                selectedWords[i] = dictionary[abs(random.nextInt() % dictionary.length)];
            }

            for(int i = 0; i < selectedWords.length; i++){
                String[] translatedWord = getTranslation(selectedWords[i]);
                translatedWords[i] = translatedWord == null ? new String[]{""} : translatedWord;
            }
        }

        int getId() {
            return id;
        }


        /**
         * Retrieves next word to be sent and sets the timer for the challenge if needed
         * @param user The user who wants the next word
         * @return Next word to be translated
         * @throws EndOfMatchException When the challenge is over so no words can be provided
         * @throws UnknownUsernameException When the given user is not in the challenge
         */
        synchronized String getNextWord(String user) throws EndOfMatchException, UnknownUsernameException {
            if(user.equals(user1)) {
                if(user1Timestamp == 0)
                    user1Timestamp = System.currentTimeMillis();
                if(user1CompletedWords < Consts.CHALLENGE_WORDS_TO_MATCH)
                    return selectedWords[user1CompletedWords++];
                else {
                    finished = true;
                    throw new EndOfMatchException();
                }
            }else if(user.equals(user2)) {
                if (user2Timestamp == 0)
                    user2Timestamp = System.currentTimeMillis();
                if(user2CompletedWords < Consts.CHALLENGE_WORDS_TO_MATCH)
                    return selectedWords[user2CompletedWords++];
                else {
                    finished = true;
                    throw new EndOfMatchException();
                }
            }
            //no match for the username
            throw new UnknownUsernameException();
        }


        /**
         * Updates the score of the given user of the given amount
         * @param user The user which score changed
         * @param amount The variation, may be negative
         * @throws GameTimeoutException If the challenge timed out so the score cannot be updated
         * @throws UnknownUsernameException When the given user is not in the challenge
         */
        synchronized void updateScore(String user, int amount) throws GameTimeoutException, UnknownUsernameException {
            if(user.equals(user1)) {
                if(System.currentTimeMillis() - user1Timestamp < Consts.CHALLENGE_TIMEOUT)
                    user1Score += amount;
                else {
                    finished = true;
                    throw new GameTimeoutException();
                }
                return;
            }
            else if(user.equals(user2)) {
                if(System.currentTimeMillis() - user2Timestamp < Consts.CHALLENGE_TIMEOUT)
                    user2Score += amount;
                else {
                    finished = true;
                    throw new GameTimeoutException();
                }
                return;
            }
            //no username matches found
            throw new UnknownUsernameException();
        }

        /**
         * Retrieves the score of the given user
         * @param user The user
         * @return The score
         * @throws UnknownUsernameException When the given user is not in the challenge
         */
        int getScore(String user) throws UnknownUsernameException {
            if(user.equals(user1)) {
                return user1Score;
            }
            else if(user.equals(user2)) {
                return user2Score;
            }
            //no username matches found
            throw new UnknownUsernameException();
        }

        /**
         * Retrieves the correct translation of the given word
         * @param originalWord The original word to be translated
         * @return The correct translation
         */
        private String[] getTranslation(String originalWord) {
            try {
                URL url = new URL(Consts.getTranslationURL(originalWord));
                System.out.println("Doing an http request");
                try(var inputStream = new BufferedReader(new InputStreamReader(url.openStream()))){
                    StringBuilder stringBuilder = new StringBuilder();
                    Gson gson = new Gson();
                    String line;

                    while((line = inputStream.readLine()) != null){
                        stringBuilder.append(line);
                    }
                    JsonObject jsonObject = gson.fromJson(stringBuilder.toString(), JsonObject.class);

                    //TODO: change score based on Levenshtein distance
                    String[] translations = new String[Consts.MAX_TRANSLATIONS_PER_WORD];
                    translations[0] = jsonObject.get("responseData").getAsJsonObject().get("translatedText").getAsString();
                    JsonArray jsonArray = jsonObject.getAsJsonArray("matches");
                    int i = 0;
                    while (i < translations.length - 1 && i < jsonArray.size()){
                        translations[i] = jsonArray.get(i).getAsJsonObject().get("translation").getAsString();
                        i++;
                    }
                    return translations;

                }catch (IOException e){
                    e.printStackTrace();
                }
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }

            return null;
        }

//        /**
//         * Gets the provided translation of the given word
//         * @param originalWord The word to get the translation of
//         * @return The translation or null if the given word is not in the set
//         */
//        String getTranslatedWord(String originalWord) {
//            for(int i = 0; i < translatedWords.length; i++){
//                if(selectedWords[i].equals(originalWord))
//                    return translatedWords[i];
//            }
//            return null;
//        }

        //TODO: check correctness
        String[] getUserLastWordTranslation(String user) throws UnknownUsernameException {
            if(user.equals(user1)) {
                return translatedWords[user1CompletedWords - 1];
            }if(user.equals(user2)) {
                return translatedWords[user2CompletedWords - 1];
            }
            throw new UnknownUsernameException();
        }

        @Override
        public String toString() {
            String recap = "Time elapsed since the beginning of the challenge: " + (System.currentTimeMillis() - user1Timestamp)/1000 + " s\n";
            recap += "User " + user1 + " scored a total of " + user1Score + " points" + "\n";
            recap += "User " + user2 + " scored a total of " + user2Score + " points";
            return recap;
        }


        class GameTimeoutException extends Exception{
        }

        class EndOfMatchException extends Exception{
        }

        class UnknownUsernameException extends Exception {
        }
    }

}
