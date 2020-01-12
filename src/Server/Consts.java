package Server;

/**
 * Class with the most used constants
 */
public class Consts {
    //TODO: move unnecessary variables

    //network constants
    public static final int TCP_PORT = 6000;
    public static final int RMI_PORT = 7000;
    public static final int UDP_PORT = 8000;
    public static final int SERVER_UDP_PORT = UDP_PORT;
    public static final int UDP_SERVER_TIMEOUT = 1000;
    public static final int UDP_CLIENT_TIMEOUT = 1000;
    public static final String WQ_STUB_NAME = "My word quizzle name";

    //implementation constants
    public static final int SERVER_THREADS = 10;
    public static final int ARRAY_INIT_SIZE = 1024;
    public static final int INT_SIZE = 4;
    public static final String DICTIONARY_FILENAME = "dictionary";
    public static final String USER_DB_FILENAME = "user_db.json";

    //game constants
    public static final int CHALLENGE_WORDS_TO_MATCH = 6;
    public static final int MAX_MESSAGE_LENGTH = 1024; //TODO: improve calculation
    public static final String SERVER_ADDRESS = "localhost";
    private static final int CHALLENGE_TIME_PER_WORD = 20000; //in ms
    public static final long CHALLENGE_TIMEOUT = CHALLENGE_WORDS_TO_MATCH * CHALLENGE_TIME_PER_WORD;
    public static final String CHALLENGE_OK = "Ok";
    public static final String CHALLENGE_REFUSED = "challenge_refused";
    public static final long CHALLENGE_REQUEST_TIMEOUT = 10000; //time to wait before a challenge request expires
    public static final String CHALLENGE_WORD_MISMATCH = "FAIL"; //when the user sends a wrong translation
    public static final int ITALIAN_WORD_MAX_LENGTH = 29; //https://www.focus.it/cultura/curiosita/qual-e-la-parola-piu-lunga-della-lingua-italiana
    public static final int WIN_SCORE_AMOUNT = 3;
    public static final int LOSE_SCORE_AMOUNT = -1;

    //requests from client
    public static final String REQUEST_LOGIN = "login";
    public static final String REQUEST_LOGOUT = "logout";
    public static final String REQUEST_ADD_FRIEND = "aggiungi_amico";
    public static final String REQUEST_FRIEND_LIST = "lista_amici";
    public static final String REQUEST_CHALLENGE = "sfida";
    public static final String REQUEST_RANKINGS = "mostra_classifica";
    public static final String REQUEST_SCORE = "mostra_punteggio";
    public static final String REQUEST_READY_FOR_CHALLENGE = "220_Ready_for_challenge"; //client has been challenged or has challenged someone and it is ready for the match
    public static final String REQUEST_NEXT_WORD = "next_word";

    //responses from server
    public static final String RESPONSE_OK = "Ok";
    public static final String RESPONSE_NEXT_WORD = "NextWord:"; //beginning of response to next word request
    //error message from server
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
    public static String getTranslationResponseServer(int matchID, String translatedWord, String wellTranslatedWord, boolean outcome) {
        String outString = outcome ? CHALLENGE_OK: CHALLENGE_WORD_MISMATCH;
        outString += " " + matchID + " " + translatedWord + " " + wellTranslatedWord;
        return outString;
    }


    public static String getTranslationResponseClient(int matchID, String user, String originalWord, String translatedWord) {
        return Consts.REQUEST_NEXT_WORD + " " + matchID + " " + user + " " + originalWord + " " + translatedWord;
    }

    /**
     * Creates a string to be sent after a next word request
     * @param matchId The current match ID
     * @param nextWord The next word to be sent
     * @return The string to be sent back
     */
    public static String getNextWordResponse(int matchId, String nextWord) {
        return Consts.RESPONSE_NEXT_WORD + " " + matchId + " " + nextWord;
    }



    //client request messages
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
}
