package Server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.GregorianCalendar;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.Math.abs;

class ChallengeHandler {

    private String[] dictionary;
    private ConcurrentHashMap<Integer, Challenge> activeChallenges = new ConcurrentHashMap<>();


    ChallengeHandler(){
        this(Consts.DICTIONARY_FILENAME);
    }

    //initialize the dictionary from file
    ChallengeHandler(String filename){
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
            System.err.println("Dictionary size is lower than actual size");
        }
    }

    void startChallenge(int challengeId){
        Challenge challenge = activeChallenges.get(challengeId);
        //TODO: start challenge
    }


    String[] getDictionary() {
        return dictionary;
    }

    static class Challenge{
        //counter is shared between multiple threads and instances
        private static final AtomicInteger idCounter = new AtomicInteger(0); //every challenge has its id assigned at construction time
        private int id;
        private String user1;
        private String user2;
        private String[] selectedWords = new String[Consts.CHALLENGE_WORDS_TO_MATCH]; //contains selected words
        private static Random random = new Random(GregorianCalendar.getInstance().getTimeInMillis());

        Challenge(String[] dictionary){
            id = idCounter.getAndIncrement();

            for(int i = 0; i < selectedWords.length; i++){
                selectedWords[i] = dictionary[abs(random.nextInt() % dictionary.length)];
            }
        }

    }

}
