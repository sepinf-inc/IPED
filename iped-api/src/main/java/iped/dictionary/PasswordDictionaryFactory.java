package iped.dictionary;

import iped.search.IItemSearcher;

public interface PasswordDictionaryFactory {

    /**
     * Creates a password dictionary (wordlist) to be used for cracking.
     *
     * The wordlist file itself is provided by the examiner through the command
     * line (-dictionary/-dict). Passwords already known from the case (looked up
     * with the given searcher, e.g. UFED password items) are prepended and tried
     * before the wordlist.
     *
     * @param searcher
     *            used to look up known passwords stored in the case. May be
     *            {@code null}, in which case only the wordlist is used.
     * @return an iterable that yields the candidate passwords, known passwords
     *         first, then the wordlist entries (read incrementally).
     */
    Iterable<String> createPasswordDictionary(IItemSearcher searcher);

    /**
     * Tries the given candidate passwords, invoking {@code attempt} once per
     * password. The attempts are run in background, parallelized and subject to
     * the configured crack time limit.
     *
     * @param passwords
     *            candidate passwords, e.g. the iterable returned by
     *            {@link #createPasswordDictionary(IItemSearcher)}.
     * @param attempt
     *            the single-try logic supplied by the caller; returns a non-null
     *            result on success, or {@code null} on failure.
     * @return the result of the first successful attempt, or {@code null} if none
     *         matched or the time limit was reached.
     */
    <T> T crack(Iterable<String> passwords, PasswordCrackAttempt<T> attempt);
}
