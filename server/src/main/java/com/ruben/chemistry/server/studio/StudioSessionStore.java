package com.ruben.chemistry.server.studio;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The editing sessions this server is holding open, each outliving the connection that held it
 * by a grace window. Safe to use from several connections at once.
 */
public final class StudioSessionStore
{
    /** How long a session survives the connection that was holding it. */
    public static final Duration DEFAULT_GRACE = Duration.ofMinutes(5);

    private final Duration grace;
    private final Clock clock;
    private final Map<String, Entry> sessions = new LinkedHashMap<>();

    public StudioSessionStore()
    {
        this(DEFAULT_GRACE, Clock.systemUTC());
    }

    /**
     * @param grace how long a session survives the connection that was holding it
     * @param clock what to measure that window against
     * @throws IllegalArgumentException if {@code grace} is negative
     */
    public StudioSessionStore(Duration grace, Clock clock)
    {
        Objects.requireNonNull(grace, "grace");
        if (grace.isNegative())
        {
            throw new IllegalArgumentException("grace cannot be negative, but was " + grace);
        }

        this.grace = grace;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Takes a newly opened session in.
     *
     * @param session the session to hold
     * @param holder the connection holding it
     * @throws IllegalArgumentException if a session of that ID is already held
     */
    public void add(StudioSession session, String holder)
    {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(holder, "holder");

        synchronized (sessions)
        {
            sweep();
            if (sessions.containsKey(session.sessionId()))
            {
                throw new IllegalArgumentException("A session is already held as '" + session.sessionId() + "'");
            }

            sessions.put(session.sessionId(), new Entry(session, holder));
        }
    }

    /**
     * @param sessionId a session's ID
     * @return the session, if it is still held
     */
    public Optional<StudioSession> find(String sessionId)
    {
        synchronized (sessions)
        {
            sweep();
            Entry entry = sessions.get(sessionId);
            return Optional.ofNullable(entry).map(Entry::session);
        }
    }

    /**
     * Says a connection is holding this session, which stops its grace window.
     *
     * @param sessionId a session's ID, which may name no session at all
     * @param holder the connection now holding it
     */
    public void attach(String sessionId, String holder)
    {
        Objects.requireNonNull(holder, "holder");

        synchronized (sessions)
        {
            Entry entry = sessions.get(sessionId);
            if (entry != null)
            {
                entry.holder = holder;
                entry.idleSince = null;
            }
        }
    }

    /**
     * Says a connection has let this session go, which starts its grace window. A connection
     * that is no longer the holder has nothing to let go of, so the window is left alone.
     *
     * @param sessionId a session's ID, which may name no session at all
     * @param holder the connection letting it go
     */
    public void detach(String sessionId, String holder)
    {
        synchronized (sessions)
        {
            Entry entry = sessions.get(sessionId);
            if (entry != null && holder.equals(entry.holder))
            {
                entry.holder = null;
                entry.idleSince = clock.instant();
            }
        }
    }

    /**
     * Discards a session now, whatever is left of its grace window.
     *
     * @param sessionId a session's ID, which may name no session at all
     */
    public void remove(String sessionId)
    {
        synchronized (sessions)
        {
            sessions.remove(sessionId);
        }
    }

    /**
     * @return how many sessions are held, counting those still inside their grace window
     */
    public int size()
    {
        synchronized (sessions)
        {
            sweep();
            return sessions.size();
        }
    }

    /** Discards every session whose grace window has closed; called before every lookup. */
    private void sweep()
    {
        Instant now = clock.instant();
        List<String> expired = new ArrayList<>();

        sessions.forEach((sessionId, entry) ->
        {
            if (entry.idleSince != null && !entry.idleSince.plus(grace).isAfter(now))
            {
                expired.add(sessionId);
            }
        });

        expired.forEach(sessions::remove);
    }

    /** A held session, the connection holding it, and when its grace window started if none is. */
    private static final class Entry
    {
        private final StudioSession session;
        private String holder;
        private Instant idleSince;

        private Entry(StudioSession session, String holder)
        {
            this.session = session;
            this.holder = holder;
        }

        private StudioSession session()
        {
            return session;
        }
    }
}
