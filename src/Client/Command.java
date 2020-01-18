package Client;

import Server.Consts;

import java.util.Random;

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
        usage += Consts.REQUEST_REGISTER + " username password\n";
        usage += Consts.REQUEST_LOGIN + " username password\n";
        usage += Consts.REQUEST_LOGOUT + " username\n";
        usage += Consts.REQUEST_ADD_FRIEND + " yourUsername otherUsername\n";
        usage += Consts.REQUEST_FRIEND_LIST + " yourUsername\n";
        usage += Consts.REQUEST_RANKINGS + " yourUsername\n";
        usage += Consts.REQUEST_CHALLENGE + " yourUsername otherUsername\n";
        usage += Consts.REQUEST_SCORE + " yourUsername\n";
        usage += Consts.REQUEST_TERMINATION + " to exit";
        return usage;
    }
}
