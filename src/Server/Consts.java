package Server;

import java.util.Random;

/**
 * Class with the most used constants
 */
public class Consts {
    //TODO: move unnecessary variables

    //network constants
    public static final int TCP_PORT = 6000; //server TCP port
    public static final int RMI_PORT = 7000;
    public static final int UDP_PORT = 8000; //Default for for client UDP
    public static final int SERVER_UDP_PORT = UDP_PORT + 1; //server default port for UDP
    public static final int UDP_SERVER_TIMEOUT = 1000;
    public static final int UDP_CLIENT_TIMEOUT = 1000;
    public static final String SERVER_ADDRESS = "localhost"; //address of server
    public static final String WQ_STUB_NAME = "My word quizzle name";

    //implementation constants
    public static final int SERVER_THREADS = 10; //worker threads in server
    public static final int INT_SIZE = 4; //bytes for a int
    public static final int MAX_MESSAGE_LENGTH = 1024; //May be reduced with appropriate calculations
    public static final String DICTIONARY_FILENAME = "dictionary";
    public static final String USER_DB_FILENAME = "user_db.json";
    public static final String USER_DB_FILENAME_TMP = "user_db_tmp.json";
    public static final long DB_SAVE_INTERVAL = 5000; //ms

    //game constants
    public static final int MAX_TRANSLATIONS_PER_WORD = 5;
    public static final String NOT_A_WORD = "ThisIsNotAWord"; //used when an appropriate word cannot be retrieved
    public static final int ITALIAN_WORD_MAX_LENGTH = 29; //https://www.focus.it/cultura/curiosita/qual-e-la-parola-piu-lunga-della-lingua-italiana
    public static final int WIN_SCORE_AMOUNT = 3;
    public static final int LOSE_SCORE_AMOUNT = 0;

    //challenge constants
    public static final int CHALLENGE_WORDS_TO_MATCH = 6;
    private static final int CHALLENGE_TIME_PER_WORD = 10000; //in ms
    public static final long CHALLENGE_TIMEOUT = CHALLENGE_WORDS_TO_MATCH * CHALLENGE_TIME_PER_WORD;
    public static final long CHALLENGE_REQUEST_TIMEOUT = 5000; //time to wait before a challenge request expires
    public static final String CHALLENGE_OK = "Ok";
    public static final String CHALLENGE_REFUSED = "challenge_refused";
    public static final String CHALLENGE_WORD_MISMATCH = "FAIL"; //when the user sends a wrong translation

    //requests from client
    public static final String REQUEST_TERMINATION = "quit"; //not to be sent
    public static final String REQUEST_REGISTER = "registra_utente"; //not to be sent
    public static final String REQUEST_LOGIN = "login";
    public static final String REQUEST_LOGOUT = "logout";
    public static final String REQUEST_ADD_FRIEND = "aggiungi_amico";
    public static final String REQUEST_FRIEND_LIST = "lista_amici";
    public static final String REQUEST_CHALLENGE = "sfida";
    public static final String REQUEST_RANKINGS = "mostra_classifica";
    public static final String REQUEST_SCORE = "mostra_punteggio";
    public static final String REQUEST_READY_FOR_CHALLENGE = "220_Ready_for_challenge"; //client has been challenged or has challenged someone and it is ready for the match
    public static final String REQUEST_NEXT_WORD = "next_word";
    public static final String REQUEST_CHALLENGE_RECAP = "challenge_recap";

    //responses from server
    public static final String RESPONSE_OK = "Ok";
    public static final String RESPONSE_NEXT_WORD = "NextWord:"; //beginning of response to next word request
    public static final String RESPONSE_WAITING_OTHER_USER = "waiting_other_user_to_finish";
    public static final String RESPONSE_CHALLENGE_TIME = "challenge_time";
    //error messages from server
    public static final String RESPONSE_USER_NOT_FOUND = "404 User not found";
    public static final String RESPONSE_WRONG_PASSWORD = "Wrong password";
    public static final String RESPONSE_ALREADY_LOGGED = "User is already logged";
    public static final String RESPONSE_NOT_LOGGED = "User is not logged";
    public static final String RESPONSE_ALREADY_FRIENDS = "The two users are already friends";
    public static final String RESPONSE_NOT_FRIENDS = "The two users are not friends";
    public static final String RESPONSE_UNKNOWN_REQUEST = "The request is unknown";
    public static final String RESPONSE_ILLEGAL_REQUEST = "Illegal operation was requested";
    public static final String RESPONSE_SAME_USER = "The two user are the same";
    public static final String RESPONSE_CHALLENGE_REFUSED = "The user challenged has not accepted within the timeout";
    public static final String RESPONSE_CHALLENGE_TIMEOUT = "Your time for the challenge expired";
    public static final String RESPONSE_UNKNOWN_USERNAME = "The given username is not involved in the given challenge";
    public static final String RESPONSE_WRONG_FORMAT = "Client send a request without proper format";


    //consts to create the translation url
    private static final String TRANSLATION_URL_BASE = "https://api.mymemory.translated.net/get?q=";
    private static final String TRANSLATION_URL_TRAIL = "&langpair=it|en";

    /**
     * Generates an url to translate the word given
     * @param originalWord The word to be translated
     * @return A string containing the url to open
     */
    public static String getTranslationURL(String originalWord) {
        return Consts.TRANSLATION_URL_BASE + originalWord + Consts.TRANSLATION_URL_TRAIL;
    }

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
        return Consts.RESPONSE_NEXT_WORD + " " + matchId + " " + nextWord;
    }

    static String getResponseTimeRemaining(long time) {
        return RESPONSE_CHALLENGE_TIME + " " + time;
    }



    /*
        All the methods used by the client to generate correct requests
     */
    public static String getRequestLogin(String username, String password, int udpPort){
        return Consts.REQUEST_LOGIN + " " + username + " " + password + " " + udpPort;
    }

    public static String getRequestLogout(String username){
        return Consts.REQUEST_LOGOUT + " " + username;
    }

    public static String getRequestAddFriend(String user1, String user2) {
        return REQUEST_ADD_FRIEND + " " + user1 + " " + user2;
    }

    public static String getRequestChallenge(String user1, String user2) {
        return REQUEST_CHALLENGE + " " + user1 + " " + user2;
    }

    public static String getRequestRankings(String user) {
        return REQUEST_RANKINGS + " " + user;
    }

    public static String getRequestScore(String user) {
        return REQUEST_SCORE + " " + user;
    }

    public static String getRequestFriends(String user) {
        return REQUEST_FRIEND_LIST + " " + user;
    }

    public static String getRequestChallengeRecap(int matchId, String username) {
        return REQUEST_CHALLENGE_RECAP + " " + matchId + " " + username;
    }

    /**
     * Creates a string to be sent by the client to the server after a user translation
     * @param matchID The current match ID
     * @param user The user who translated the word
     * @param translatedWord The word translated
     * @return The string to be sent back
     */
    public static String getRequestWordTranslation(int matchID, String user, String translatedWord) {
        return Consts.REQUEST_NEXT_WORD + " " + matchID + " " + user + " " + translatedWord;
    }

    //constant used for the automatic client testing
    private static final Random random = new Random(System.currentTimeMillis());
    public static final String BASE_USERNAME = "username";
    public static final int N_CLIENTS = 500;
    public static final int CLIENT_TESTS = 20;

    public static String getRandomUserName(String thisUser) {
        String randomUser = thisUser;
        while (randomUser.equals(thisUser)){
            randomUser = BASE_USERNAME + random.nextInt(N_CLIENTS);
        }
        return randomUser;
    }


}
