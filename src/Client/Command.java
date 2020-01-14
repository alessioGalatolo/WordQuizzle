package Client;

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

    public static Command getRandomCommand() {

        return values()[3 + random.nextInt(values().length - 3)]; //get a command different from the first three
    }

    public static String usage() {
        String usage = "Usage: \n";
        usage += REGISTER + " username password\n";
        usage += LOGIN + " username password\n";
        usage += LOGOUT + " username\n";
        usage += ADD_FRIEND + " yourUsername otherUsername\n";
        usage += FRIENDS + " yourUsername\n";
        usage += RANKING + " yourUsername\n";
        usage += CHALLENGE + " yourUsername otherUsername\n";
        usage += SCORE + " yourUsername\n";
        return usage;
    }
}
