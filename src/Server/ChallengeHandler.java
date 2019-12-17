package Server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

class ChallengeHandler {

    private String[] dictionary;

    ChallengeHandler(){
        try {
            FileChannel inChannel = FileChannel.open(Paths.get(Consts.DICTIONARY_FILENAME)); //file has a word per line
            int dictionaryIndex = 0;
            ByteBuffer wordBuffer = ByteBuffer.allocate(Consts.ITALIAN_WORD_MAX_LENGTH + 1);


            inChannel.read(wordBuffer);
            wordBuffer.flip();
            String words = new String(wordBuffer.array(), 0, wordBuffer.limit(), StandardCharsets.UTF_8);
            String[] wordsFragments = words.split("\n");
            int dictionaryLength = Integer.parseInt(wordsFragments[0]);

            dictionary = new String[dictionaryLength];

            for(int i = 1; i < wordsFragments.length; i++){
                if(dictionary[dictionaryIndex] == null)
                    dictionary[dictionaryIndex] = wordsFragments[i];
                else
                    dictionary[dictionaryIndex] += wordsFragments[i];
                dictionaryIndex++;
            }

            if(words.charAt(words.length() - 1) != '\n')
                dictionaryIndex--;

            while (inChannel.read(wordBuffer) != -1){
                wordBuffer.flip();
                words = new String(wordBuffer.array(), StandardCharsets.UTF_8);
                wordsFragments = words.split("\n");

                if(words.charAt(0) == '\n')
                    dictionaryIndex++;

                for(String word: wordsFragments){
                    dictionary[dictionaryIndex] += word;
                    System.out.println(dictionary[dictionaryIndex]);
                    dictionaryIndex++;
                }

                if(words.charAt(words.length() - 1) != '\n')
                    dictionaryIndex--;
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String[] getDictionary() {
        return dictionary;
    }

    static class Challenge{
        //counter is shared between multiple threads and instances
        private static final AtomicInteger idCounter = new AtomicInteger(0); //every challenge has its id assigned at construction time
        private int id;
        private String user1;
        private String user2;
        private String[] selectedWords; //contains selected words

        Challenge(){
            id = idCounter.getAndIncrement();
            //TODO: select words
        }

    }

}
