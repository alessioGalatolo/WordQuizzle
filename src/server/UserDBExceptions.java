package server;

/**
 * Exceptions thrown inside UserDB
 */
class UserDBExceptions {

    static class UserNotFoundException extends Exception {
    }

    static class AlreadyLoggedException extends Exception {
    }

    static class NotLoggedException extends Exception {
    }

    static class NotFriendsException extends Exception{
    }

    static class AlreadyFriendsException extends Exception {
    }

    static class SameUserException extends Exception{
    }

    static class ChallengeRequestTimeoutException extends Exception {
    }
}
