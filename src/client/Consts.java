package client;

import static commons.Constants.*;

/**
 * All the constants used only by the client
 */
class Consts {
    static final String CHALLENGE_TIMEOUT = "Your time for the challenge expired";
    static final long ACCEPTABLE_WAIT_CHALLENGE_CONFIRM = 10000; //time to wait for a datagram before considering the challenge refused


    /*
        All the methods used by the client to generate correct requests
     */
    static String getRequestLogin(String username, String password, int udpPort){
        return REQUEST_LOGIN + " " + username + " " + password + " " + udpPort;
    }

    static String getRequestLogout(String username){
        return REQUEST_LOGOUT + " " + username;
    }

    static String getRequestAddFriend(String user1, String user2) {
        return REQUEST_ADD_FRIEND + " " + user1 + " " + user2;
    }

    static String getRequestChallenge(String user1, String user2) {
        return REQUEST_CHALLENGE + " " + user1 + " " + user2;
    }

    static String getRequestRankings(String user) {
        return REQUEST_RANKINGS + " " + user;
    }

    static String getRequestScore(String user) {
        return REQUEST_SCORE + " " + user;
    }

    static String getRequestFriends(String user) {
        return REQUEST_FRIEND_LIST + " " + user;
    }

    static String getRequestChallengeRecap(int matchId) {
        return REQUEST_CHALLENGE_RECAP + " " + matchId;
    }

    /**
     * Creates a string to be sent by the client to the server after a user translation
     * @param matchID The current match ID
     * @param user The user who translated the word
     * @param translatedWord The word translated
     * @return The string to be sent back
     */
    static String getRequestWordTranslation(int matchID, String user, String translatedWord) {
        return REQUEST_NEXT_WORD + " " + matchID + " " + user + " " + translatedWord;
    }

}
