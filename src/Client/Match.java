package Client;

/**
 * Class containing useful info about a match
 * where a match is a single translation inside a challenge
 */
class Match{
    private boolean lastWordCorrect; //true if last word was correct
    private String lastWord; //last word translated by the user
    private String lastTranslatedWord; //last word correct translation
    private String nextWord; //next word to be translated
    private boolean errorOccurred;
    private long timeRemaining;

    Match(boolean lastWordCorrect, String lastWord, String lastTranslatedWord, String nextWord, boolean errorOccurred, long timeRemaining) {
        this.lastWordCorrect = lastWordCorrect;
        this.lastWord = lastWord;
        this.lastTranslatedWord = lastTranslatedWord;
        this.nextWord = nextWord;
        this.errorOccurred = errorOccurred;
        this.timeRemaining = timeRemaining / 1000;
    }

    boolean isLastWordCorrect() {
        return lastWordCorrect;
    }

    String getLastWord() {
        return lastWord;
    }

    String getLastTranslatedWord() {
        return lastTranslatedWord;
    }

    String getNextWord() {
        return nextWord;
    }

    Boolean getErrorOccurred() {
        return errorOccurred;
    }

    long getTimeRemaining() {
        return timeRemaining;
    }
}

