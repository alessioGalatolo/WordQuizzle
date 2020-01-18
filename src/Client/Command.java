package Client;

import java.util.Random;

import static Commons.Constants.*;

/**
 * Class containing all the command a user can enter
 */
public enum Command {
    REGISTER,
    LOGIN,
    LOGOUT,
    ADD_FRIEND,
    FRIENDS,
    RANKING,
    CHALLENGE,
    SCORE;


    private static Random random = new Random();

    /**
     * @return a random command different from REGISTER, LOGIN, LOGOUT
     */
    public static Command getRandomCommand() {
        return values()[3 + random.nextInt(values().length - 3)]; //get a command different from the first three
    }

    public static String usage() {
        String usage = "Usage: \n";
        usage += REQUEST_REGISTER + " username password\n";
        usage += REQUEST_LOGIN + " username password\n";
        usage += REQUEST_LOGOUT + " username\n";
        usage += REQUEST_ADD_FRIEND + " yourUsername otherUsername\n";
        usage += REQUEST_FRIEND_LIST + " yourUsername\n";
        usage += REQUEST_RANKINGS + " yourUsername\n";
        usage += REQUEST_CHALLENGE + " yourUsername otherUsername\n";
        usage += REQUEST_SCORE + " yourUsername\n";
        usage += REQUEST_TERMINATION + " to exit";
        return usage;
    }
}
