/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.web.http.session;

import sirius.kernel.commons.Value;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Represents a server sided session.
 * <p>
 * A ServerSession is identified by an id and kept on the server until a usage timeout occurs. The timeout depends on
 * the state of the session. The initial timeout defaults to 5 minutes and can be set via
 * <tt>http.serverMiniSessionLifetime</tt>. After the second use of the session, it will be set to 30 min (or the
 * value defined in <tt>http.serverSessionLifetime</tt>. This permits to get rid of useless "one-call" sessions created
 * by bots like Google etc.
 * <p>
 * Normally the WebContext takes care of finding or creating sessions based on cookies.
 *
 * @author Andreas Haufler (aha@scireum.de)
 * @see sirius.web.http.WebContext#getServerSession()
 * @since 2014/05
 */
public interface ServerSession {

    /**
     * Fixed field used to store the initial URI used to create this session
     */
    public static final String INITIAL_URI = "_INITIAL_URI";

    /**
     * Fixed field containing the user agent used to request the initial url
     */
    public static final String USER_AGENT = "_USER_AGENT";

    /**
     * Fixed field storing the name of the current user owning this session
     */
    public static final String USER = "_USER";

    /**
     * Fixed field storing the IP which was used to create the session
     */
    public static final String REMOTE_IP = "_REMOTE_IP";

    /**
     * Returns the timestamp of the sessions creation
     *
     * @return the timestamp in milliseconds when the session was created
     */
    long getCreationTime();

    /**
     * Returns the unique session id
     *
     * @return the session id
     */
    String getId();

    /**
     * Returns the timestamp when the session was last accessed
     *
     * @return the timestamp in milliseconds when the session was last accessed
     */
    long getLastAccessedTime();

    /**
     * Returns the max. time span a session is permitted to be inactive (not accessed) before it is eligible for
     * invalidation.
     * <p>
     * If the session was not accessed since its creation, this time span is rather short, to get quickly rid of
     * "one call" sessions created by bots. After the second call, the timeout is expanded.
     *
     * @return the max number of seconds since the last access before the session is eligible for invalidation
     */
    int getMaxInactiveInterval();

    /**
     * Fetches a previously stored value from the session.
     *
     * @param key the key for which the value is requested.
     * @return the stored value, wrapped as {@link sirius.kernel.commons.Value}
     */
    @Nonnull
    Value getValue(String key);

    /**
     * Returns a list of all keys for which a value is stored in the session
     *
     * @return a list of all keys for which a value is stored
     */
    List<String> getKeys();

    /**
     * Determines if a key with the given name is already present.
     *
     * @param key the name of the key
     * @return <tt>true</tt> if a value with the given key exists, <tt>false</tt> otherwise
     */
    boolean hasKey(String key);

    /**
     * Stores the given name value pair in the session.
     *
     * @param key  the key used to associate the data with
     * @param data the data to store for the given key. Note that data needs to be serializable!
     */
    void putValue(String key, Object data);

    /**
     * Deletes the stored value for the given key.
     *
     * @param key the key identifying the data to be removed
     */
    void removeValue(String key);

    /**
     * Deletes this session.
     */
    void invalidate();

    /**
     * Determines if the session is new.
     * <p>
     * A new session was created by the current request and not fetched from the session map using its ID.
     *
     * @return <tt>true</tt> if the session was created by this request, <tt>false</tt> otherwise.
     */
    boolean isNew();

    /**
     * Signals the system that this session belongs to a user which logged in. This will normally enhance the
     * session live time over a session without an attached user.
     */
    void markAsUserSession();

}
