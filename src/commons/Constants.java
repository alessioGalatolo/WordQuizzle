package commons;

import java.util.Random;

public class Constants {


    public static final String NOT_A_WORD = "ThisIsNotAWord"; //used when an appropriate word cannot be retrieved

    //implementations constants
    public static final int INT_SIZE = 4; //bytes for a int
    public static final int MAX_MESSAGE_LENGTH = 1024; //May be reduced with appropriate calculations

    //network constants
    public static final int TCP_PORT = 6000; //server TCP port
    public static final int RMI_PORT = 7000;
    public static final int UDP_PORT = 8000; //Default for for client UDP
    public static final int SERVER_UDP_PORT = UDP_PORT + 1; //server default port for UDP
    public static final int UDP_SERVER_TIMEOUT = 1000;
    public static final int UDP_CLIENT_TIMEOUT = 1000;
    public static final String SERVER_ADDRESS = "localhost"; //address of server
    public static final String WQ_STUB_NAME = "My word quizzle name";


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

    //challenge constants
    public static final int CHALLENGE_WORDS_TO_MATCH = 3;
    public static final String CHALLENGE_OK = "Ok";
    public static final String CHALLENGE_REFUSED = "challenge_refused";
    public static final String CHALLENGE_WORD_MISMATCH = "FAIL"; //when the user sends a wrong translation

    //responses from server
    public static final String RESPONSE_OK = "Ok";
    public static final String RESPONSE_NEXT_WORD = "NextWord:"; //beginning of response to next word request
    public static final String RESPONSE_WAITING_OTHER_USER = "waiting_other_user_to_finish";
    public static final String RESPONSE_CHALLENGE_TIME = "challenge_time";
    public static final String RESPONSE_CHALLENGE_TIMEOUT = "challenge_timeout";


    //consts to create the translation url
    private static final String TRANSLATION_URL_BASE = "https://api.mymemory.translated.net/get?q=";
    private static final String TRANSLATION_URL_TRAIL = "&langpair=it|en";
    /**
     * Generates an url to translate the word given
     * @param originalWord The word to be translated
     * @return A string containing the url to open
     */
    public static String getTranslationURL(String originalWord) {
        return TRANSLATION_URL_BASE + originalWord + TRANSLATION_URL_TRAIL;
    }



    //constant used for the automatic client testing
    private static final Random random = new Random(System.currentTimeMillis());
    public static final String BASE_USERNAME = "username";
    public static final int N_CLIENTS = 10;
    public static final int CLIENT_TESTS = 20;

    public static String getRandomUserName(String thisUser) {
        String randomUser = thisUser;
        while (randomUser.equals(thisUser)){
            randomUser = BASE_USERNAME + random.nextInt(N_CLIENTS);
        }
        return randomUser;
    }
}

