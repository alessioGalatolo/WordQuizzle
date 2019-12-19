package Server;//class with most used constants

public class Consts {
    //TODO: move unnecessary variables
    public static final int TCP_PORT = 6000;
    public static final int RMI_PORT = 7000;
    public static final int UDP_PORT = 8000;
    public static final int SERVER_THREADS = 10;
    public static final String WQ_STUB_NAME = "My word quizzle name";
    public static final int ARRAY_INIT_SIZE = 1024;
    public static final int INT_SIZE = 4;
    public static final int UDP_TIMEOUT = 1000;
    public static final String DICTIONARY_FILENAME = "long_dictionary";
    public static final int CHALLENGE_WORDS_TO_MATCH = 6;
    public static final int ITALIAN_WORD_MAX_LENGTH = 29; //https://www.focus.it/cultura/curiosita/qual-e-la-parola-piu-lunga-della-lingua-italiana
    public static final String TRANSLATION_URL_BASE = "https://api.mymemory.translated.net/get?q=";
    public static final String TRANSLATION_URL_TRAIL = "&langpair=it|en";

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
}
