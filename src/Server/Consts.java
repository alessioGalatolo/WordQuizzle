package Server;

import java.util.Random;

import static Commons.Constants.*;

/**
 * Class with the most used constants from the server
 */
class Consts {

    //implementation constants
    static final int SERVER_THREADS = 10; //worker threads in server
    static final String DICTIONARY_FILENAME = "dictionary";
    static final String USER_TABLE_FILENAME = "user_table.json";
    static final String USER_TABLE_FILENAME_TMP = "user_table_tmp.json";
    static final String USER_GRAPH_FILENAME_TMP = "user_graph_tmp.json";
    static final String USER_GRAPH_FILENAME = "user_graph.json";
    static final long DB_SAVE_INTERVAL = 5000; //ms, the interval in which the server saves the user database

    //game constants
    static final int MAX_TRANSLATIONS_PER_WORD = 5; //The max number of accepted translations for a given word
    static final int ITALIAN_WORD_MAX_LENGTH = 29; //https://www.focus.it/cultura/curiosita/qual-e-la-parola-piu-lunga-della-lingua-italiana
    static final int WIN_SCORE_AMOUNT = 3;
    static final int LOSE_SCORE_AMOUNT = 0;

    //challenge constants
    private static final int CHALLENGE_TIME_PER_WORD = 10000; //in ms
    static final long CHALLENGE_TIMEOUT = CHALLENGE_WORDS_TO_MATCH * CHALLENGE_TIME_PER_WORD;
    static final long CHALLENGE_REQUEST_TIMEOUT = 5000; //time to wait before a challenge request expired

    //error messages from server
    static final String RESPONSE_USER_NOT_FOUND = "404 User not found";
    static final String RESPONSE_WRONG_PASSWORD = "Wrong password";
    static final String RESPONSE_ALREADY_LOGGED = "User is already logged";
    static final String RESPONSE_NOT_LOGGED = "User is not logged";
    static final String RESPONSE_ALREADY_FRIENDS = "The two users are already friends";
    static final String RESPONSE_NOT_FRIENDS = "The two users are not friends";
    static final String RESPONSE_UNKNOWN_REQUEST = "The request is unknown";
    static final String RESPONSE_ILLEGAL_REQUEST = "Illegal operation was requested";
    static final String RESPONSE_SAME_USER = "The two user are the same";
    static final String RESPONSE_CHALLENGE_REFUSED = "The user challenged has not accepted within the timeout";
    static final String RESPONSE_UNKNOWN_USERNAME = "The given username is not involved in the given challenge";
    static final String RESPONSE_WRONG_FORMAT = "Client send a request without proper format";

    /**
     * Creates a string to be sent in response to a translation
     * @param matchID The current match ID
     * @param translatedWord The user-translated word
     * @param wellTranslatedWord The given well translated word
     * @param outcome The correctness of the translation
     * @return The string to be sent back
     */
    static String getResponseTranslationServer(int matchID, String translatedWord, String wellTranslatedWord, boolean outcome) {
        String outString = outcome ? CHALLENGE_OK: CHALLENGE_WORD_MISMATCH;
        outString += " " + matchID + " " + translatedWord + " " + wellTranslatedWord;
        return outString;
    }

    /**
     * Creates a string to be sent after a next word request
     * @param matchId The current match ID
     * @param nextWord The next word to be sent
     * @return The string to be sent back
     */
    static String getResponseNextWord(int matchId, String nextWord) {
        return RESPONSE_NEXT_WORD + " " + matchId + " " + nextWord;
    }

    static String getResponseTimeRemaining(long time) {
        return RESPONSE_CHALLENGE_TIME + " " + time;
    }
}
