package com.ruben.chemistry.server.resource;

import com.ruben.chemistry.server.molecule.MoleculeParser;
import com.ruben.chemistry.server.molecule.MoleculeStore;
import com.ruben.chemistry.server.molecule.StoredMolecule;
import com.ruben.chemistry.server.studio.StudioSessionStore;
import com.ruben.chemistry.server.ws.SocketHarness;
import com.ruben.chemistry.server.ws.SocketHarness.Collector;
import com.ruben.chemistry.server.ws.WebSocketServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.net.http.WebSocket;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static com.ruben.chemistry.server.ws.SocketHarness.connect;
import static org.assertj.core.api.Assertions.assertThat;

class StudioSocketEndpointTest
{
    @RegisterExtension
    private final SocketHarness sockets = new SocketHarness();

    private final MoleculeStore molecules = new MoleculeStore();
    private final MoleculeParser parser = new MoleculeParser();

    private StudioSocketEndpoint endpoint = new StudioSocketEndpoint(molecules);
    private WebSocketServer server;

    @Test
    void should_greet_a_studio_session_and_answer_its_pings() throws Exception
    {
        Collector collector = new Collector();
        WebSocket socket = studio(collector);

        Map<String, Object> ready = collector.nextJson();
        assertThat(ready).containsEntry("type", "session.ready");
        assertThat(ready).containsEntry("protocol_version", 3L);
        assertThat(ready.get("connection_id")).asString().startsWith("ws-");

        socket.sendText("{\"type\": \"ping\", \"sequence\": 108}", true).join();

        assertThat(collector.nextJson())
                .containsEntry("type", "pong")
                .containsEntry("sequence", 108L);
    }

    @Test
    void should_answer_a_studio_message_it_cannot_handle_without_closing() throws Exception
    {
        Collector collector = new Collector();
        WebSocket socket = studio(collector);
        collector.next();

        socket.sendText("{\"type\": \"bond.patch\", \"sequence\": 7}", true).join();
        Map<String, Object> unknown = collector.nextJson();
        assertThat(unknown).containsEntry("type", "error").containsEntry("answering", "bond.patch");
        assertThat(unknown).containsEntry("sequence", 7L);
        assertThat(error(unknown)).containsEntry("code", "UNKNOWN_TYPE");

        socket.sendText("not json at all", true).join();
        assertThat(error(collector.nextJson())).containsEntry("code", "INVALID_JSON");

        socket.sendText("{\"sequence\": 9}", true).join();
        Map<String, Object> missing = collector.nextJson();
        assertThat(error(missing)).containsEntry("code", "MISSING_TYPE");
        assertThat(missing).containsEntry("sequence", 9L);

        // the session survived all three
        socket.sendText("{\"type\": \"ping\"}", true).join();
        assertThat(collector.nextJson()).containsEntry("type", "pong");
    }

    @Test
    void should_open_a_session_on_a_stored_molecule() throws Exception
    {
        StoredMolecule stored = store("CCO");
        Collector collector = new Collector();
        WebSocket socket = studio(collector);
        collector.next();

        socket.sendText(open(stored.id(), 1), true).join();
        Map<String, Object> state = collector.nextJson();

        assertThat(state).containsEntry("type", "session.state");
        assertThat(state).containsEntry("sequence", 1L);
        assertThat(state).containsEntry("version", 1L);
        assertThat(state).containsEntry("source_molecule_id", stored.id());
        assertThat(state).containsEntry("smiles", "CCO");
        assertThat(state.get("session_id")).asString().isNotBlank();
        assertThat(state.get("saved_molecule_id")).isNull();
        assertThat(state.get("strain")).isInstanceOf(Double.class);
        assertThat(state.get("pinned")).isEqualTo(List.of());
        assertThat(state.get("locked")).isEqualTo(List.of());

        // ethanol's three written atoms and the six hydrogens it counted
        List<Map<String, Object>> atoms = list(state, "atoms");
        assertThat(atoms).hasSize(9);
        assertThat(list(state, "bonds")).hasSize(8);
        assertThat(atoms.stream().filter(atom -> Boolean.TRUE.equals(atom.get("was_implicit")))).hasSize(6);

        Map<String, Object> first = atoms.getFirst();
        assertThat(first).containsEntry("element", "C").containsEntry("charge", 0L).containsEntry("isotope", 0L);
        assertThat(first.get("id")).asString().isNotBlank();
        assertThat(first.get("colour")).asString().startsWith("#");
        assertThat(position(first)).containsKeys("x", "y", "z");

        Map<String, Object> bond = list(state, "bonds").getFirst();
        assertThat(bond).containsEntry("order", "SINGLE").containsEntry("aromatic", false);
        assertThat(bond.get("first")).isNotEqualTo(bond.get("second"));

        List<Map<String, Object>> fragments = list(state, "fragments");
        assertThat(fragments).hasSize(1);
        assertThat(fragments.getFirst()).containsEntry("key", "f1").containsEntry("name", "Fragment 1");
        assertThat(list(fragments.getFirst(), "atoms")).hasSize(9);
    }

    @Test
    void should_mark_the_bonds_of_an_aromatic_ring_aromatic() throws Exception
    {
        StoredMolecule stored = store("c1ccccc1");
        Collector collector = new Collector();
        WebSocket socket = studio(collector);
        collector.next();

        socket.sendText(open(stored.id(), 1), true).join();
        Map<String, Object> state = collector.nextJson();

        List<Map<String, Object>> bonds = list(state, "bonds");
        assertThat(bonds.stream().filter(bond -> Boolean.TRUE.equals(bond.get("aromatic")))).hasSize(6);
    }

    @Test
    void should_refuse_to_open_a_molecule_it_does_not_hold() throws Exception
    {
        Collector collector = new Collector();
        WebSocket socket = studio(collector);
        collector.next();

        socket.sendText(open("not-a-molecule", 1), true).join();
        Map<String, Object> refusal = collector.nextJson();

        assertThat(refusal).containsEntry("type", "error").containsEntry("answering", "session.open");
        assertThat(error(refusal)).containsEntry("code", "UNKNOWN_MOLECULE");
    }

    @Test
    void should_refuse_an_open_it_cannot_read() throws Exception
    {
        Collector collector = new Collector();
        WebSocket socket = studio(collector);
        collector.next();

        socket.sendText("{\"type\": \"session.open\", \"sequence\": 1}", true).join();
        assertThat(error(collector.nextJson())).containsEntry("code", "INVALID_MESSAGE");

        socket.sendText("{\"type\": \"session.open\", \"molecule_id\": \"x\", \"shape\": \"round\"}", true).join();
        assertThat(error(collector.nextJson())).containsEntry("code", "INVALID_MESSAGE");

        socket.sendText("{\"type\": \"session.open\", \"molecule_id\": \"" + store("CCO").id()
                + "\", \"conformers\": 0}", true).join();
        assertThat(error(collector.nextJson())).containsEntry("code", "INVALID_MESSAGE");
    }

    @Test
    void should_send_the_state_of_a_session_again_on_request() throws Exception
    {
        StoredMolecule stored = store("CCO");
        Collector collector = new Collector();
        WebSocket socket = studio(collector);
        collector.next();

        socket.sendText(open(stored.id(), 1), true).join();
        String sessionId = (String) collector.nextJson().get("session_id");

        socket.sendText(resync(sessionId, 2), true).join();
        Map<String, Object> again = collector.nextJson();

        assertThat(again).containsEntry("type", "session.state");
        assertThat(again).containsEntry("sequence", 2L);
        assertThat(again).containsEntry("session_id", sessionId);
        assertThat(again).containsEntry("version", 1L);
    }

    @Test
    void should_hand_a_session_back_to_a_client_that_reconnects() throws Exception
    {
        StoredMolecule stored = store("CCO");
        Collector dropped = new Collector();
        WebSocket first = studio(dropped);
        dropped.next();

        first.sendText(open(stored.id(), 1), true).join();
        String sessionId = (String) dropped.nextJson().get("session_id");
        first.sendClose(WebSocket.NORMAL_CLOSURE, "dropped").join();

        Collector reconnected = new Collector();
        WebSocket second = studio(reconnected);
        reconnected.next();

        second.sendText(resync(sessionId, 3), true).join();
        assertThat(reconnected.nextJson()).containsEntry("session_id", sessionId);
    }

    @Test
    void should_refuse_a_resync_for_a_session_whose_grace_window_closed() throws Exception
    {
        StudioSessionStore expiring = expiringAtOnce();
        StoredMolecule stored = store("CCO");
        Collector dropped = new Collector();
        WebSocket first = studio(dropped);
        dropped.next();

        first.sendText(open(stored.id(), 1), true).join();
        String sessionId = (String) dropped.nextJson().get("session_id");

        first.sendClose(WebSocket.NORMAL_CLOSURE, "dropped").join();
        await(() -> expiring.find(sessionId).isEmpty());

        Collector late = new Collector();
        WebSocket second = studio(late);
        late.next();

        second.sendText(resync(sessionId, 2), true).join();
        assertThat(error(late.nextJson())).containsEntry("code", "NO_SESSION");
    }

    @Test
    void should_keep_a_session_its_connection_is_still_holding() throws Exception
    {
        // the window is closed the moment a session is let go of, so finding it again proves
        // that holding a session keeps it however long the connection is quiet
        expiringAtOnce();
        StoredMolecule stored = store("CCO");
        Collector collector = new Collector();
        WebSocket socket = studio(collector);
        collector.next();

        socket.sendText(open(stored.id(), 1), true).join();
        String sessionId = (String) collector.nextJson().get("session_id");

        socket.sendText(resync(sessionId, 2), true).join();
        assertThat(collector.nextJson()).containsEntry("session_id", sessionId);
    }

    @Test
    void should_keep_a_session_a_reconnected_client_took_over_when_the_old_connection_closes() throws Exception
    {
        StudioSessionStore expiring = expiringAtOnce();
        StoredMolecule stored = store("CCO");
        Collector dropped = new Collector();
        WebSocket first = studio(dropped);
        dropped.next();

        first.sendText(open(stored.id(), 1), true).join();
        String sessionId = (String) dropped.nextJson().get("session_id");

        Collector reconnected = new Collector();
        WebSocket second = studio(reconnected);
        reconnected.next();
        second.sendText(resync(sessionId, 2), true).join();
        assertThat(reconnected.nextJson()).containsEntry("session_id", sessionId);

        // the dropped connection closes only now, after the new one has taken the session over;
        // the server drops a connection from its own list once its listener has been told
        first.sendClose(WebSocket.NORMAL_CLOSURE, "dropped").join();
        await(() -> server.sessions().size() == 1);

        assertThat(expiring.find(sessionId)).isPresent();
        second.sendText(resync(sessionId, 3), true).join();
        assertThat(reconnected.nextJson()).containsEntry("session_id", sessionId);
    }

    @Test
    void should_refuse_a_resync_for_a_session_it_does_not_hold() throws Exception
    {
        Collector collector = new Collector();
        WebSocket socket = studio(collector);
        collector.next();

        socket.sendText(resync("not-a-session", 1), true).join();
        Map<String, Object> refusal = collector.nextJson();

        assertThat(refusal).containsEntry("answering", "session.resync");
        assertThat(error(refusal)).containsEntry("code", "NO_SESSION");
    }

    @Test
    void should_rebuild_a_session_on_a_submitted_smiles() throws Exception
    {
        StoredMolecule stored = store("CCO");
        Collector collector = new Collector();
        WebSocket socket = studio(collector);
        collector.next();

        socket.sendText(open(stored.id(), 1), true).join();
        Map<String, Object> opened = collector.nextJson();
        String sessionId = (String) opened.get("session_id");

        socket.sendText(submit(sessionId, 1, "CCN", 2), true).join();
        Map<String, Object> rebuilt = collector.nextJson();

        assertThat(rebuilt).containsEntry("type", "session.state");
        assertThat(rebuilt).containsEntry("sequence", 2L);
        assertThat(rebuilt).containsEntry("session_id", sessionId);
        assertThat(rebuilt).containsEntry("version", 2L);
        assertThat(rebuilt).containsEntry("source_molecule_id", stored.id());
        assertThat(rebuilt).containsEntry("smiles", "CCN");
        assertThat(list(rebuilt, "atoms")).hasSize(10);

        // the server now holds the rebuilt session, not the one it opened
        socket.sendText(resync(sessionId, 3), true).join();
        assertThat(collector.nextJson()).containsEntry("version", 2L).containsEntry("smiles", "CCN");
    }

    @Test
    void should_answer_a_submit_made_on_a_stale_version_with_a_conflict() throws Exception
    {
        StoredMolecule stored = store("CCO");
        Collector collector = new Collector();
        WebSocket socket = studio(collector);
        collector.next();

        socket.sendText(open(stored.id(), 1), true).join();
        String sessionId = (String) collector.nextJson().get("session_id");
        socket.sendText(submit(sessionId, 1, "CCN", 2), true).join();
        collector.next();

        socket.sendText(submit(sessionId, 1, "CCC", 3), true).join();
        Map<String, Object> conflict = collector.nextJson();

        assertThat(conflict).containsEntry("type", "conflict");
        assertThat(conflict).containsEntry("sequence", 3L);
        assertThat(conflict).containsEntry("expected_version", 2L);
        assertThat(conflict).containsEntry("base_version", 1L);
        assertThat(conflict.get("state")).isInstanceOfSatisfying(Map.class, state ->
                assertThat(state).containsEntry("version", 2L).containsEntry("smiles", "CCN"));
    }

    @Test
    void should_leave_the_session_as_it_was_when_a_submitted_smiles_cannot_be_read() throws Exception
    {
        StoredMolecule stored = store("CCO");
        Collector collector = new Collector();
        WebSocket socket = studio(collector);
        collector.next();

        socket.sendText(open(stored.id(), 1), true).join();
        String sessionId = (String) collector.nextJson().get("session_id");

        socket.sendText(submit(sessionId, 1, "C(", 2), true).join();
        Map<String, Object> refusal = collector.nextJson();
        assertThat(refusal).containsEntry("type", "error").containsEntry("answering", "smiles.submit");
        assertThat(refusal).containsEntry("sequence", 2L);
        assertThat(error(refusal)).containsEntry("code", "INVALID_SMILES");

        socket.sendText(submit(sessionId, 1, "   ", 3), true).join();
        assertThat(error(collector.nextJson())).containsEntry("code", "INVALID_SMILES");

        socket.sendText(resync(sessionId, 4), true).join();
        assertThat(collector.nextJson()).containsEntry("version", 1L).containsEntry("smiles", "CCO");
    }

    @Test
    void should_refuse_a_submit_it_cannot_read_or_place() throws Exception
    {
        Collector collector = new Collector();
        WebSocket socket = studio(collector);
        collector.next();

        socket.sendText("{\"type\": \"smiles.submit\", \"sequence\": 1}", true).join();
        assertThat(error(collector.nextJson())).containsEntry("code", "INVALID_MESSAGE");

        socket.sendText(submit("not-a-session", 1, "CCO", 2), true).join();
        assertThat(error(collector.nextJson())).containsEntry("code", "NO_SESSION");
    }

    @Test
    void should_move_an_atom_and_acknowledge_the_patch() throws Exception
    {
        StoredMolecule stored = store("CCO");
        Collector collector = new Collector();
        WebSocket socket = studio(collector);
        collector.next();

        socket.sendText(open(stored.id(), 1), true).join();
        Map<String, Object> opened = collector.nextJson();
        String sessionId = (String) opened.get("session_id");
        String atom = (String) list(opened, "atoms").getFirst().get("id");

        socket.sendText(patch(sessionId, 1, true, atom, 1.5, -2, 0.25, 2), true).join();
        Map<String, Object> ack = collector.nextJson();

        assertThat(ack).containsEntry("type", "atom.patch.ack");
        assertThat(ack).containsEntry("sequence", 2L);
        assertThat(ack).containsEntry("version", 2L);
        assertThat(ack).containsEntry("status", "applied");
        assertThat(ack).containsEntry("can_undo", true);

        socket.sendText(resync(sessionId, 3), true).join();
        Map<String, Object> state = collector.nextJson();
        assertThat(state).containsEntry("version", 2L).containsEntry("can_undo", true).containsEntry("can_redo", false);
        assertThat(list(state, "atoms").getFirst().get("position"))
                .isEqualTo(Map.of("x", 1.5, "y", -2.0, "z", 0.25));
    }

    @Test
    void should_answer_a_patch_made_on_a_stale_version_with_a_conflict() throws Exception
    {
        StoredMolecule stored = store("CCO");
        Collector collector = new Collector();
        WebSocket socket = studio(collector);
        collector.next();

        socket.sendText(open(stored.id(), 1), true).join();
        Map<String, Object> opened = collector.nextJson();
        String sessionId = (String) opened.get("session_id");
        String atom = (String) list(opened, "atoms").getFirst().get("id");

        socket.sendText(patch(sessionId, 7, true, atom, 9, 9, 9, 2), true).join();
        Map<String, Object> conflict = collector.nextJson();

        assertThat(conflict).containsEntry("type", "conflict");
        assertThat(conflict).containsEntry("expected_version", 1L);
        assertThat(conflict).containsEntry("base_version", 7L);
        assertThat(conflict.get("state")).isInstanceOfSatisfying(Map.class, state ->
                assertThat(state).containsEntry("version", 1L));
    }

    @Test
    void should_refuse_a_patch_of_an_atom_it_does_not_hold() throws Exception
    {
        StoredMolecule stored = store("CCO");
        Collector collector = new Collector();
        WebSocket socket = studio(collector);
        collector.next();

        socket.sendText(open(stored.id(), 1), true).join();
        String sessionId = (String) collector.nextJson().get("session_id");

        socket.sendText(patch(sessionId, 1, true, "a999", 1, 1, 1, 2), true).join();
        assertThat(error(collector.nextJson())).containsEntry("code", "UNKNOWN_ID");

        socket.sendText("{\"type\": \"atom.patch\", \"sequence\": 3, \"session_id\": \"" + sessionId
                + "\", \"base_version\": 1, \"atoms\": [{\"id\": \"a1\"}]}", true).join();
        assertThat(error(collector.nextJson())).containsEntry("code", "INVALID_MESSAGE");

        socket.sendText(resync(sessionId, 4), true).join();
        assertThat(collector.nextJson()).containsEntry("version", 1L);
    }

    @Test
    void should_undo_and_redo_a_move_and_say_when_there_is_nothing_to_do() throws Exception
    {
        StoredMolecule stored = store("CCO");
        Collector collector = new Collector();
        WebSocket socket = studio(collector);
        collector.next();

        socket.sendText(open(stored.id(), 1), true).join();
        Map<String, Object> opened = collector.nextJson();
        String sessionId = (String) opened.get("session_id");
        Map<String, Object> first = list(opened, "atoms").getFirst();

        socket.sendText(history("edit.undo", sessionId, 1, 2), true).join();
        assertThat(error(collector.nextJson())).containsEntry("code", "NOTHING_TO_UNDO");

        socket.sendText(patch(sessionId, 1, true, (String) first.get("id"), 4, 4, 4, 3), true).join();
        collector.next();

        socket.sendText(history("edit.undo", sessionId, 2, 4), true).join();
        Map<String, Object> undone = collector.nextJson();
        assertThat(undone).containsEntry("type", "session.state").containsEntry("version", 3L);
        assertThat(undone).containsEntry("can_undo", false).containsEntry("can_redo", true);
        assertThat(list(undone, "atoms").getFirst().get("position")).isEqualTo(first.get("position"));

        socket.sendText(history("edit.redo", sessionId, 3, 5), true).join();
        Map<String, Object> redone = collector.nextJson();
        assertThat(redone).containsEntry("version", 4L);
        assertThat(list(redone, "atoms").getFirst().get("position")).isEqualTo(Map.of("x", 4.0, "y", 4.0, "z", 4.0));

        socket.sendText(history("edit.redo", sessionId, 4, 6), true).join();
        assertThat(error(collector.nextJson())).containsEntry("code", "NOTHING_TO_REDO");
    }

    /**
     * Serves this test's connections from a store that gives a session no grace at all, so
     * that letting go of one retires it rather than the test waiting out a window.
     *
     * @return that store
     */
    private StudioSessionStore expiringAtOnce()
    {
        StudioSessionStore expiring = new StudioSessionStore(Duration.ZERO, Clock.systemUTC());
        endpoint = new StudioSocketEndpoint(molecules, expiring);
        return expiring;
    }

    /**
     * Waits for the server to catch up with something a client has already been told of.
     *
     * @param settled what is true once it has
     */
    private static void await(BooleanSupplier settled) throws InterruptedException
    {
        for (int attempt = 0; attempt < 500 && !settled.getAsBoolean(); attempt++)
        {
            Thread.sleep(10);
        }

        assertThat(settled.getAsBoolean()).as("the server caught up").isTrue();
    }

    private WebSocket studio(Collector collector) throws IOException
    {
        if (server == null)
        {
            server = sockets.start(() -> endpoint);
        }

        return connect(server, "/ws/studio", collector);
    }

    /**
     * @param smiles a molecule
     * @return it, stored where a session can open it
     */
    private StoredMolecule store(String smiles)
    {
        return molecules.add(parser.parse(smiles));
    }

    /**
     * @param moleculeId the molecule to open
     * @param sequence the message's sequence number
     * @return a {@code session.open} message
     */
    private static String open(String moleculeId, int sequence)
    {
        return "{\"type\": \"session.open\", \"sequence\": " + sequence + ", \"molecule_id\": \"" + moleculeId + "\"}";
    }

    /**
     * @param sessionId the session to ask about
     * @param sequence the message's sequence number
     * @return a {@code session.resync} message
     */
    private static String resync(String sessionId, int sequence)
    {
        return "{\"type\": \"session.resync\", \"sequence\": " + sequence
                + ", \"session_id\": \"" + sessionId + "\", \"since_version\": 1}";
    }

    /**
     * @param sessionId the session to rebuild
     * @param baseVersion the version the client says it was looking at
     * @param smiles the molecule to rebuild on
     * @param sequence the message's sequence number
     * @return a {@code smiles.submit} message
     */
    private static String submit(String sessionId, long baseVersion, String smiles, int sequence)
    {
        return "{\"type\": \"smiles.submit\", \"sequence\": " + sequence + ", \"session_id\": \"" + sessionId
                + "\", \"base_version\": " + baseVersion + ", \"smiles\": \"" + smiles + "\"}";
    }

    /**
     * @param sessionId the session to edit
     * @param baseVersion the version the client says it was looking at
     * @param commit whether the patch ends its edit
     * @param atom the atom to move
     * @param x where to put it
     * @param y where to put it
     * @param z where to put it
     * @param sequence the message's sequence number
     * @return an {@code atom.patch} message
     */
    private static String patch(String sessionId, long baseVersion, boolean commit, String atom, double x, double y, double z, int sequence)
    {
        return "{\"type\": \"atom.patch\", \"sequence\": " + sequence + ", \"session_id\": \"" + sessionId
                + "\", \"base_version\": " + baseVersion + ", \"commit\": " + commit + ", \"atoms\": [{\"id\": \"" + atom
                + "\", \"position\": {\"x\": " + x + ", \"y\": " + y + ", \"z\": " + z + "}}]}";
    }

    /**
     * @param type {@code edit.undo} or {@code edit.redo}
     * @param sessionId the session to step through
     * @param baseVersion the version the client says it was looking at
     * @param sequence the message's sequence number
     * @return that message
     */
    private static String history(String type, String sessionId, long baseVersion, int sequence)
    {
        return "{\"type\": \"" + type + "\", \"sequence\": " + sequence + ", \"session_id\": \"" + sessionId
                + "\", \"base_version\": " + baseVersion + "}";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> error(Map<String, Object> envelope)
    {
        return (Map<String, Object>) envelope.get("error");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> position(Map<String, Object> atom)
    {
        return (Map<String, Object>) atom.get("position");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Map<String, Object> body, String name)
    {
        return (List<Map<String, Object>>) body.get(name);
    }
}
