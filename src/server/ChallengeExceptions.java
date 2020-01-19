package server;

/**
 * Exceptions thrown in ChallengeHandler class
 */
class ChallengeExceptions {
    static class GameTimeoutException extends Exception{
    }

    static class EndOfMatchException extends Exception{
    }

    static class UnknownUsernameException extends Exception {
        UnknownUsernameException(String s) {
            super(s);
        }
    }
}
