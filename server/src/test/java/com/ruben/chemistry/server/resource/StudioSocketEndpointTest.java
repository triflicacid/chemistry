package com.ruben.chemistry.server.resource;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.server.molecule.MoleculeParser;
import com.ruben.chemistry.server.molecule.MoleculeStore;
import com.ruben.chemistry.server.molecule.StoredMolecule;
import com.ruben.chemistry.server.studio.GeometryScheduler;
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
    /** The start of every message, naming the protocol the client is written for. */
    private static final String PROTOCOL = "\"protocol_version\": 12, ";

    @RegisterExtension
    private final SocketHarness sockets = new SocketHarness();

    private final MoleculeStore molecules = new MoleculeStore();
    private final MoleculeParser parser = new MoleculeParser();

    // results of examining the geometry arrive on their own, so the tests that did not ask for them are not sent any
    private StudioSocketEndpoint endpoint = new StudioSocketEndpoint(molecules, new StudioSessionStore(), GeometryScheduler.disabled());
    private WebSocketServer server;

    @Test
    void should_greet_a_studio_session_and_answer_its_pings() throws Exception
    {
        Collector collector = new Collector();
        WebSocket socket = studio(collector);

        Map<String, Object> ready = collector.nextJson();
        assertThat(ready).containsEntry("type", "session.ready");
        assertThat(ready).containsEntry("protocol_version", 12L);
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

        socket.sendText("{" + PROTOCOL + "\"type\": \"bond.patch\", \"sequence\": 7}", true).join();
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
    void should_refuse_a_message_written_for_another_protocol_before_anything_else() throws Exception
    {
        StoredMolecule stored = store("CCO");
        Collector collector = new Collector();
        WebSocket socket = studio(collector);
        collector.next();

        // a stale client, a client from the future, and one that never said, each refused for its own reason
        socket.sendText("{\"protocol_version\": 10, \"type\": \"session.open\", \"sequence\": 1, \"molecule_id\": \"" + stored.id() + "\"}", true).join();
        Map<String, Object> stale = collector.nextJson();
        assertThat(error(stale)).containsEntry("code", "PROTOCOL_MISMATCH");
        assertThat(stale).containsEntry("answering", "session.open").containsEntry("sequence", 1L);
        assertThat((String) error(stale).get("message")).contains("out of date");

        socket.sendText("{\"protocol_version\": 13, \"type\": \"session.open\", \"sequence\": 2}", true).join();
        assertThat((String) error(collector.nextJson()).get("message")).contains("restart the server");

        // the version is checked ahead of the type, so a message this server has never heard of is still a mismatch
        socket.sendText("{\"type\": \"bond.patch\", \"sequence\": 3}", true).join();
        Map<String, Object> unnamed = collector.nextJson();
        assertThat(error(unnamed)).containsEntry("code", "PROTOCOL_MISMATCH");
        assertThat((String) error(unnamed).get("message")).contains("did not name");

        // nothing was opened, and a ping needs no version
        socket.sendText("{\"type\": \"ping\", \"sequence\": 4}", true).join();
        assertThat(collector.nextJson()).containsEntry("type", "pong");
        socket.sendText(open(stored.id(), 5), true).join();
        assertThat(collector.nextJson()).containsEntry("type", "session.state").containsEntry("version", 1L);
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
        assertThat(state).doesNotContainKey("strain");
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

        socket.sendText("{" + PROTOCOL + "\"type\": \"session.open\", \"sequence\": 1}", true).join();
        assertThat(error(collector.nextJson())).containsEntry("code", "INVALID_MESSAGE");

        socket.sendText("{" + PROTOCOL + "\"type\": \"session.open\", \"molecule_id\": \"x\", \"shape\": \"round\"}", true).join();
        assertThat(error(collector.nextJson())).containsEntry("code", "INVALID_MESSAGE");

        socket.sendText("{" + PROTOCOL + "\"type\": \"session.open\", \"molecule_id\": \"" + store("CCO").id()
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

        socket.sendText("{" + PROTOCOL + "\"type\": \"smiles.submit\", \"sequence\": 1}", true).join();
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

        socket.sendText("{" + PROTOCOL + "\"type\": \"atom.patch\", \"sequence\": 3, \"session_id\": \"" + sessionId
                + "\", \"base_version\": 1, \"atoms\": [{\"id\": \"a1\"}]}", true).join();
        assertThat(error(collector.nextJson())).containsEntry("code", "INVALID_MESSAGE");

        socket.sendText(resync(sessionId, 4), true).join();
        assertThat(collector.nextJson()).containsEntry("version", 1L);
    }

    @Test
    void should_give_a_bond_a_length_and_answer_with_the_atoms_that_moved() throws Exception
    {
        StoredMolecule stored = store("CCO");
        Collector collector = new Collector();
        WebSocket socket = studio(collector);
        collector.next();

        socket.sendText(open(stored.id(), 1), true).join();
        Map<String, Object> opened = collector.nextJson();
        String sessionId = (String) opened.get("session_id");
        Map<String, Object> bond = list(opened, "bonds").getFirst();
        String first = (String) bond.get("first");
        String second = (String) bond.get("second");

        socket.sendText(bondEdit("bond.length", sessionId, 1, 2,
                "\"bond\": \"" + bond.get("id") + "\", \"length\": 2.0, \"hold\": \"" + first + "\""), true).join();
        Map<String, Object> ack = collector.nextJson();

        assertThat(ack).containsEntry("type", "bond.length.ack").containsEntry("sequence", 2L).containsEntry("version", 2L)
                .containsEntry("status", "applied").containsEntry("can_undo", true);
        List<Map<String, Object>> moved = list(ack, "atoms");
        assertThat(moved).extracting(atom -> atom.get("id")).contains(second).doesNotContain(first);

        socket.sendText(history("edit.undo", sessionId, 2, 3), true).join();
        assertThat(collector.nextJson()).containsEntry("type", "edit.applied").containsEntry("version", 3L);
    }

    @Test
    void should_keep_a_bond_drag_out_of_the_history_until_it_is_committed() throws Exception
    {
        StoredMolecule stored = store("CCO");
        Collector collector = new Collector();
        WebSocket socket = studio(collector);
        collector.next();

        socket.sendText(open(stored.id(), 1), true).join();
        Map<String, Object> opened = collector.nextJson();
        String sessionId = (String) opened.get("session_id");
        Map<String, Object> bond = list(opened, "bonds").getFirst();
        String target = "\"bond\": \"" + bond.get("id") + "\", \"hold\": \"" + bond.get("first") + "\"";

        socket.sendText(bondEdit("bond.length", sessionId, 1, 2, target + ", \"length\": 1.8, \"commit\": false"), true).join();
        Map<String, Object> dragged = collector.nextJson();
        // an edit in progress can be cancelled, but is not yet worth saving
        assertThat(dragged).containsEntry("version", 2L).containsEntry("saved_molecule_id", null);
        assertThat(list(dragged, "atoms")).isNotEmpty();

        socket.sendText(bondEdit("bond.length", sessionId, 2, 3, target + ", \"length\": 1.8"), true).join();
        Map<String, Object> dropped = collector.nextJson();
        assertThat(dropped).containsEntry("version", 2L).containsEntry("can_undo", true);
        assertThat(list(dropped, "atoms")).isEmpty();
        assertThat(dropped.get("saved_molecule_id")).isNotNull();
    }

    @Test
    void should_give_a_bond_a_torsion_and_refuse_what_it_cannot() throws Exception
    {
        StoredMolecule stored = store("CCO");
        Collector collector = new Collector();
        WebSocket socket = studio(collector);
        collector.next();

        socket.sendText(open(stored.id(), 1), true).join();
        Map<String, Object> opened = collector.nextJson();
        String sessionId = (String) opened.get("session_id");
        List<Map<String, Object>> atoms = list(opened, "atoms");
        Map<String, Object> bond = list(opened, "bonds").getFirst();
        String hydrogen = (String) atoms.stream().filter(atom -> atom.get("element").equals("H")).findFirst().orElseThrow().get("id");
        String oxygen = (String) atoms.get(2).get("id");

        // the first hydrogen is on the first carbon, and the oxygen is on the second
        socket.sendText(bondEdit("bond.torsion", sessionId, 1, 2,
                "\"bond\": \"" + bond.get("id") + "\", \"from\": \"" + hydrogen + "\", \"to\": \"" + oxygen + "\", \"angle\": 60, \"hold\": \"" + bond.get("first") + "\""), true).join();
        Map<String, Object> ack = collector.nextJson();
        assertThat(ack).containsEntry("type", "bond.torsion.ack").containsEntry("version", 2L);
        assertThat(list(ack, "atoms")).isNotEmpty();

        socket.sendText(bondEdit("bond.torsion", sessionId, 2, 3,
                "\"bond\": \"" + bond.get("id") + "\", \"from\": \"" + oxygen + "\", \"to\": \"" + hydrogen + "\", \"angle\": 60"), true).join();
        assertThat(error(collector.nextJson())).containsEntry("code", "INVALID_EDIT");

        socket.sendText(bondEdit("bond.torsion", sessionId, 2, 4, "\"bond\": \"" + bond.get("id") + "\""), true).join();
        assertThat(error(collector.nextJson())).containsEntry("code", "INVALID_MESSAGE");

        socket.sendText(bondEdit("bond.length", sessionId, 1, 5, "\"bond\": \"" + bond.get("id") + "\", \"length\": 1.5"), true).join();
        assertThat(collector.nextJson()).containsEntry("type", "conflict");
    }

    @Test
    void should_give_an_atom_an_angle_and_refuse_what_it_cannot() throws Exception
    {
        StoredMolecule stored = store("CCO");
        Collector collector = new Collector();
        WebSocket socket = studio(collector);
        collector.next();

        socket.sendText(open(stored.id(), 1), true).join();
        Map<String, Object> opened = collector.nextJson();
        String sessionId = (String) opened.get("session_id");
        List<Map<String, Object>> atoms = list(opened, "atoms");
        String carbon = (String) atoms.get(0).get("id");
        String centre = (String) atoms.get(1).get("id");
        String oxygen = (String) atoms.get(2).get("id");
        String ends = "\"centre\": \"" + centre + "\", \"from\": \"" + carbon + "\", \"to\": \"" + oxygen + "\"";

        socket.sendText(bondEdit("atom.angle", sessionId, 1, 2, ends + ", \"angle\": 120, \"hold\": \"" + carbon + "\""), true).join();
        Map<String, Object> ack = collector.nextJson();
        assertThat(ack).containsEntry("type", "atom.angle.ack").containsEntry("version", 2L).containsEntry("can_undo", true);
        assertThat(list(ack, "atoms")).extracting(atom -> atom.get("id")).contains(oxygen).doesNotContain(carbon, centre);

        socket.sendText(bondEdit("atom.angle", sessionId, 2, 3,
                "\"centre\": \"" + centre + "\", \"from\": \"" + carbon + "\", \"to\": \"" + carbon + "\", \"angle\": 120"), true).join();
        assertThat(error(collector.nextJson())).containsEntry("code", "INVALID_EDIT");

        socket.sendText(bondEdit("atom.angle", sessionId, 2, 4, "\"centre\": \"" + centre + "\""), true).join();
        assertThat(error(collector.nextJson())).containsEntry("code", "INVALID_MESSAGE");

        socket.sendText(bondEdit("atom.angle", sessionId, 1, 5, ends + ", \"angle\": 100"), true).join();
        assertThat(collector.nextJson()).containsEntry("type", "conflict");
    }

    @Test
    void should_refuse_to_change_a_bond_it_does_not_hold_or_to_an_unreasonable_length() throws Exception
    {
        StoredMolecule stored = store("CCO");
        Collector collector = new Collector();
        WebSocket socket = studio(collector);
        collector.next();

        socket.sendText(open(stored.id(), 1), true).join();
        Map<String, Object> opened = collector.nextJson();
        String sessionId = (String) opened.get("session_id");
        Map<String, Object> bond = list(opened, "bonds").getFirst();

        socket.sendText(bondEdit("bond.length", sessionId, 1, 2, "\"bond\": \"b999\", \"length\": 1.5"), true).join();
        assertThat(error(collector.nextJson())).containsEntry("code", "UNKNOWN_ID");

        socket.sendText(bondEdit("bond.length", sessionId, 1, 3, "\"bond\": \"" + bond.get("id") + "\", \"length\": 99"), true).join();
        assertThat(error(collector.nextJson())).containsEntry("code", "INVALID_EDIT");

        socket.sendText(bondEdit("bond.length", sessionId, 1, 4, "\"bond\": \"" + bond.get("id") + "\""), true).join();
        assertThat(error(collector.nextJson())).containsEntry("code", "INVALID_MESSAGE");
    }

    @Test
    void should_lock_atoms_refuse_to_move_them_and_unlock_them() throws Exception
    {
        StoredMolecule stored = store("CCO");
        Collector collector = new Collector();
        WebSocket socket = studio(collector);
        collector.next();

        socket.sendText(open(stored.id(), 1), true).join();
        Map<String, Object> opened = collector.nextJson();
        String sessionId = (String) opened.get("session_id");
        String atom = (String) list(opened, "atoms").getFirst().get("id");

        socket.sendText(lock(sessionId, 1, atom, true, 2), true).join();
        Map<String, Object> ack = collector.nextJson();
        assertThat(ack).containsEntry("type", "atom.lock.ack").containsEntry("sequence", 2L).containsEntry("version", 2L);
        assertThat(ack).containsEntry("locked", List.of(atom));

        socket.sendText(patch(sessionId, 2, true, atom, 1, 1, 1, 3), true).join();
        assertThat(error(collector.nextJson())).containsEntry("code", "LOCKED");

        socket.sendText(lock(sessionId, 2, atom, false, 4), true).join();
        assertThat(collector.nextJson()).containsEntry("version", 3L).containsEntry("locked", List.of());

        socket.sendText(patch(sessionId, 3, true, atom, 1, 1, 1, 5), true).join();
        assertThat(collector.nextJson()).containsEntry("type", "atom.patch.ack");
    }

    @Test
    void should_change_an_atom_answer_with_the_state_and_store_the_changed_graph_under_one_id() throws Exception
    {
        StoredMolecule stored = store("CCO");
        Collector collector = new Collector();
        WebSocket socket = studio(collector);
        collector.next();

        socket.sendText(open(stored.id(), 1), true).join();
        Map<String, Object> opened = collector.nextJson();
        String sessionId = (String) opened.get("session_id");
        String oxygen = (String) list(opened, "atoms").get(2).get("id");

        socket.sendText("{" + PROTOCOL + "\"type\": \"atom.set\", \"sequence\": 2, \"session_id\": \"" + sessionId
                + "\", \"base_version\": 1, \"atom\": \"" + oxygen + "\", \"element\": \"N\", \"charge\": 1}", true).join();
        Map<String, Object> ack = collector.nextJson();
        assertThat(ack).containsEntry("type", "atom.set.ack").containsEntry("sequence", 2L).containsEntry("version", 2L);
        Map<String, Object> state = map(ack, "state");
        assertThat(list(state, "atoms").get(2)).containsEntry("id", oxygen).containsEntry("element", "N").containsEntry("charge", 1L);
        assertThat((String) state.get("smiles")).contains("N");
        assertThat(state).containsEntry("can_undo", true);

        String savedId = (String) state.get("saved_molecule_id");
        assertThat(savedId).isNotNull();
        assertThat(molecules.find(savedId).orElseThrow().molecule().atoms().stream().map(Atom::element)).contains("N");

        socket.sendText(history("edit.undo", sessionId, 2, 3), true).join();
        Map<String, Object> undone = collector.nextJson();
        assertThat(undone).containsEntry("type", "edit.applied").containsEntry("saved_molecule_id", savedId);
        assertThat(list(map(undone, "state"), "atoms").get(2)).containsEntry("element", "O").containsEntry("charge", 0L);
        assertThat(molecules.find(savedId).orElseThrow().molecule().atoms().stream().map(Atom::element)).doesNotContain("N");
    }

    @Test
    void should_refuse_an_atom_change_it_cannot_make_and_leave_the_session_as_it_was() throws Exception
    {
        StoredMolecule stored = store("CCO");
        Collector collector = new Collector();
        WebSocket socket = studio(collector);
        collector.next();

        socket.sendText(open(stored.id(), 1), true).join();
        String sessionId = (String) collector.nextJson().get("session_id");

        socket.sendText("{" + PROTOCOL + "\"type\": \"atom.set\", \"sequence\": 2, \"session_id\": \"" + sessionId
                + "\", \"base_version\": 1, \"atom\": \"a1\", \"element\": \"Zz\"}", true).join();
        assertThat(error(collector.nextJson())).containsEntry("code", "INVALID_EDIT");

        socket.sendText("{" + PROTOCOL + "\"type\": \"atom.set\", \"sequence\": 3, \"session_id\": \"" + sessionId
                + "\", \"base_version\": 1, \"element\": \"N\"}", true).join();
        assertThat(error(collector.nextJson())).containsEntry("code", "INVALID_MESSAGE");

        socket.sendText(resync(sessionId, 4), true).join();
        assertThat(collector.nextJson()).containsEntry("version", 1L);
    }

    @Test
    void should_save_what_is_locked_with_the_molecule_and_open_it_locked() throws Exception
    {
        StoredMolecule stored = store("CCO");
        Collector collector = new Collector();
        WebSocket socket = studio(collector);
        collector.next();

        socket.sendText(open(stored.id(), 1), true).join();
        Map<String, Object> opened = collector.nextJson();
        String sessionId = (String) opened.get("session_id");
        String atom = (String) list(opened, "atoms").get(1).get("id");

        socket.sendText(lock(sessionId, 1, atom, true, 2), true).join();
        String saved = (String) collector.nextJson().get("saved_molecule_id");
        assertThat(saved).isNotNull().isNotEqualTo(stored.id());
        assertThat(stored.studio().lockedSites()).isEmpty();

        socket.sendText(open(saved, 3), true).join();
        assertThat(collector.nextJson().get("locked")).isEqualTo(List.of(atom));

        socket.sendText(lock(sessionId, 2, atom, false, 4), true).join();
        assertThat(collector.nextJson()).containsEntry("saved_molecule_id", saved);
        socket.sendText(open(saved, 5), true).join();
        assertThat(collector.nextJson().get("locked")).isEqualTo(List.of());
    }

    @Test
    void should_move_the_molecule_as_a_whole_and_leave_its_atoms_alone() throws Exception
    {
        StoredMolecule stored = store("CCO");
        Collector collector = new Collector();
        WebSocket socket = studio(collector);
        collector.next();

        socket.sendText(open(stored.id(), 1), true).join();
        Map<String, Object> opened = collector.nextJson();
        String sessionId = (String) opened.get("session_id");
        assertThat(opened.get("transform")).isEqualTo(Map.of(
                "position", Map.of("x", 0.0, "y", 0.0, "z", 0.0),
                "rotation", Map.of("x", 0.0, "y", 0.0, "z", 0.0, "w", 1.0)));

        socket.sendText(transform(sessionId, 1, false, 1, 2, 3, 2), true).join();
        Map<String, Object> ack = collector.nextJson();
        assertThat(ack).containsEntry("type", "molecule.transform.ack").containsEntry("sequence", 2L)
                .containsEntry("version", 2L).containsEntry("can_undo", true).containsEntry("saved_molecule_id", null);

        socket.sendText(transform(sessionId, 2, true, 4, 5, 6, 3), true).join();
        Map<String, Object> committed = collector.nextJson();
        assertThat(committed).containsEntry("version", 3L).containsEntry("can_undo", true);
        assertThat(committed.get("saved_molecule_id")).isNotNull();

        socket.sendText(resync(sessionId, 4), true).join();
        Map<String, Object> state = collector.nextJson();
        assertThat(state.get("transform")).isEqualTo(Map.of(
                "position", Map.of("x", 4.0, "y", 5.0, "z", 6.0),
                "rotation", Map.of("x", 0.0, "y", 0.0, "z", 0.0, "w", 1.0)));
        assertThat(list(state, "atoms").stream().map(StudioSocketEndpointTest::position).toList())
                .isEqualTo(list(opened, "atoms").stream().map(StudioSocketEndpointTest::position).toList());
    }

    @Test
    void should_undo_and_redo_a_move_of_the_molecule_as_one_edit_and_save_it() throws Exception
    {
        StoredMolecule stored = store("CCO");
        Collector collector = new Collector();
        WebSocket socket = studio(collector);
        collector.next();

        socket.sendText(open(stored.id(), 1), true).join();
        Map<String, Object> opened = collector.nextJson();
        String sessionId = (String) opened.get("session_id");

        // a drag is several patches and then one that commits, which is one step to undo
        socket.sendText(transform(sessionId, 1, false, 1, 0, 0, 2), true).join();
        collector.next();
        socket.sendText(transform(sessionId, 2, true, 2, 0, 0, 3), true).join();
        String saved = (String) collector.nextJson().get("saved_molecule_id");

        socket.sendText(history("edit.undo", sessionId, 3, 4), true).join();
        Map<String, Object> undone = collector.nextJson();
        assertThat(undone).containsEntry("type", "edit.applied").containsEntry("can_undo", false).containsEntry("can_redo", true);
        assertThat(undone.get("transform")).isEqualTo(Map.of(
                "position", Map.of("x", 0.0, "y", 0.0, "z", 0.0),
                "rotation", Map.of("x", 0.0, "y", 0.0, "z", 0.0, "w", 1.0)));
        assertThat(list(undone, "atoms")).isEmpty();
        assertThat(molecules.find(saved).orElseThrow().studio().pose().position().x()).isEqualTo(0.0);

        socket.sendText(history("edit.redo", sessionId, 4, 5), true).join();
        Map<String, Object> redone = collector.nextJson();
        assertThat(redone.get("transform")).isEqualTo(Map.of(
                "position", Map.of("x", 2.0, "y", 0.0, "z", 0.0),
                "rotation", Map.of("x", 0.0, "y", 0.0, "z", 0.0, "w", 1.0)));
        assertThat(molecules.find(saved).orElseThrow().studio().pose().position().x()).isEqualTo(2.0);
    }

    @Test
    void should_open_a_saved_molecule_where_it_was_left() throws Exception
    {
        StoredMolecule stored = store("CCO");
        Collector collector = new Collector();
        WebSocket socket = studio(collector);
        collector.next();

        socket.sendText(open(stored.id(), 1), true).join();
        String sessionId = (String) collector.nextJson().get("session_id");
        socket.sendText(transform(sessionId, 1, true, 3, 0, 0, 2), true).join();
        String saved = (String) collector.nextJson().get("saved_molecule_id");

        socket.sendText(open(saved, 3), true).join();
        Map<String, Object> reopened = collector.nextJson();

        assertThat(reopened.get("transform")).isEqualTo(Map.of(
                "position", Map.of("x", 3.0, "y", 0.0, "z", 0.0),
                "rotation", Map.of("x", 0.0, "y", 0.0, "z", 0.0, "w", 1.0)));
    }

    @Test
    void should_refuse_a_transform_it_cannot_make() throws Exception
    {
        StoredMolecule stored = store("CCO");
        Collector collector = new Collector();
        WebSocket socket = studio(collector);
        collector.next();

        socket.sendText(open(stored.id(), 1), true).join();
        String sessionId = (String) collector.nextJson().get("session_id");

        socket.sendText("{" + PROTOCOL + "\"type\": \"molecule.transform\", \"sequence\": 2, \"session_id\": \"" + sessionId
                + "\", \"base_version\": 1, \"position\": {\"x\": 0, \"y\": 0, \"z\": 0}, "
                + "\"rotation\": {\"x\": 0, \"y\": 0, \"z\": 0, \"w\": 0}}", true).join();
        assertThat(error(collector.nextJson())).containsEntry("code", "INVALID_MESSAGE");

        socket.sendText("{" + PROTOCOL + "\"type\": \"molecule.transform\", \"sequence\": 3, \"session_id\": \"" + sessionId
                + "\", \"base_version\": 1}", true).join();
        assertThat(error(collector.nextJson())).containsEntry("code", "INVALID_MESSAGE");

        socket.sendText(transform(sessionId, 9, true, 1, 1, 1, 4), true).join();
        assertThat(collector.nextJson()).containsEntry("type", "conflict");

        socket.sendText(resync(sessionId, 5), true).join();
        assertThat(collector.nextJson()).containsEntry("version", 1L);
    }

    @Test
    void should_save_the_camera_with_the_molecule_and_give_it_back_when_that_is_opened() throws Exception
    {
        StoredMolecule stored = store("CCO");
        Collector collector = new Collector();
        WebSocket socket = studio(collector);
        collector.next();

        socket.sendText(open(stored.id(), 1), true).join();
        Map<String, Object> opened = collector.nextJson();
        String sessionId = (String) opened.get("session_id");
        assertThat(opened).containsEntry("camera", null);

        socket.sendText(camera(sessionId, 2, 40), true).join();
        Map<String, Object> ack = collector.nextJson();
        assertThat(ack).containsEntry("type", "camera.set.ack").containsEntry("sequence", 2L);
        String saved = (String) ack.get("saved_molecule_id");
        assertThat(saved).isNotNull().isNotEqualTo(stored.id());

        // looking around is not an edit, so the session is still at its first version with nothing to undo
        socket.sendText(resync(sessionId, 3), true).join();
        Map<String, Object> same = collector.nextJson();
        assertThat(same).containsEntry("version", 1L).containsEntry("can_undo", false);
        assertThat(same.get("camera")).isEqualTo(cameraState(40));

        socket.sendText(open(saved, 4), true).join();
        assertThat(collector.nextJson().get("camera")).isEqualTo(cameraState(40));
        assertThat(stored.studio().camera()).isNull();
    }

    @Test
    void should_send_the_strain_of_the_geometry_once_it_is_left_alone() throws Exception
    {
        endpoint = new StudioSocketEndpoint(molecules, new StudioSessionStore(), new GeometryScheduler(Duration.ofMillis(30)));
        StoredMolecule stored = store("CCO");
        Collector collector = new Collector();
        WebSocket socket = studio(collector);
        collector.next();

        socket.sendText(open(stored.id(), 1), true).join();
        Map<String, Object> opened = collector.nextJson();
        String sessionId = (String) opened.get("session_id");

        Map<String, Object> first = collector.nextJson();
        assertThat(first).containsEntry("type", "geometry.result").containsEntry("session_id", sessionId)
                .containsEntry("version", 1L).containsEntry("status", "complete");
        assertThat((Double) first.get("strain")).isZero();
        assertThat(list(first, "clashes")).isEmpty();

        // two atoms put on top of each other, which is as strained as a geometry gets
        String atom = (String) list(opened, "atoms").getFirst().get("id");
        Map<String, Object> other = list(opened, "atoms").get(2);
        Map<String, Object> there = position(other);
        socket.sendText(patch(sessionId, 1, true, atom, ((Number) there.get("x")).doubleValue(), ((Number) there.get("y")).doubleValue(),
                ((Number) there.get("z")).doubleValue(), 2), true).join();
        assertThat(collector.nextJson()).containsEntry("type", "atom.patch.ack");

        Map<String, Object> second = collector.nextJson();
        assertThat(second).containsEntry("type", "geometry.result").containsEntry("version", 2L);
        assertThat((Double) second.get("strain")).isGreaterThan((Double) first.get("strain"));
        assertThat(list(second, "findings")).isNotEmpty();
    }

    @Test
    void should_send_one_result_for_a_drag_of_patches_and_it_for_the_last_version() throws Exception
    {
        endpoint = new StudioSocketEndpoint(molecules, new StudioSessionStore(), new GeometryScheduler(Duration.ofMillis(150)));
        StoredMolecule stored = store("CCO");
        Collector collector = new Collector();
        WebSocket socket = studio(collector);
        collector.next();

        socket.sendText(open(stored.id(), 1), true).join();
        Map<String, Object> opened = collector.nextJson();
        String sessionId = (String) opened.get("session_id");
        String atom = (String) list(opened, "atoms").getFirst().get("id");

        socket.sendText(patch(sessionId, 1, false, atom, 4, 4, 4, 2), true).join();
        socket.sendText(patch(sessionId, 2, false, atom, 5, 5, 5, 3), true).join();
        socket.sendText(patch(sessionId, 3, true, atom, 6, 6, 6, 4), true).join();

        // the acknowledgements come first, and then the one result, for the version the drag ended at
        for (int ack = 0; ack < 3; ack++)
        {
            assertThat(collector.nextJson()).containsEntry("type", "atom.patch.ack");
        }
        assertThat(collector.nextJson()).containsEntry("type", "geometry.result").containsEntry("version", 4L);
        assertThat(collector.messages.poll(400, java.util.concurrent.TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void should_relax_a_distorted_geometry_as_one_edit_that_can_be_undone() throws Exception
    {
        StoredMolecule stored = store("CCO");
        Collector collector = new Collector();
        WebSocket socket = studio(collector);
        collector.next();

        socket.sendText(open(stored.id(), 1), true).join();
        Map<String, Object> opened = collector.nextJson();
        String sessionId = (String) opened.get("session_id");
        Map<String, Object> atom = list(opened, "atoms").getFirst();
        Map<String, Object> from = position(atom);
        socket.sendText(patch(sessionId, 1, true, (String) atom.get("id"), ((Number) from.get("x")).doubleValue() + 0.6,
                ((Number) from.get("y")).doubleValue(), ((Number) from.get("z")).doubleValue(), 2), true).join();
        assertThat(collector.nextJson()).containsEntry("type", "atom.patch.ack").containsEntry("version", 2L);

        socket.sendText(optimise(sessionId, 2, 3), true).join();
        Map<String, Object> started = collector.nextJson();
        assertThat(started).containsEntry("type", "optimise.started").containsEntry("sequence", 3L).containsEntry("version", 2L);

        Map<String, Object> result = untilResult(collector);
        assertThat(result).containsEntry("session_id", sessionId)
                .containsEntry("operation_id", started.get("operation_id"))
                .containsEntry("base_version", 2L)
                .containsEntry("status", "complete")
                .containsEntry("version", 3L)
                .containsEntry("can_undo", true);
        assertThat(result).doesNotContainKey("model");
        assertThat((Double) result.get("strain_after")).isLessThan((Double) result.get("strain_before"));
        assertThat(list(result, "atoms")).isNotEmpty();
        assertThat(result.get("saved_molecule_id")).isNotNull();

        // the optimisation is one edit, so one undo takes every atom back to where the patch left it
        socket.sendText(history("edit.undo", sessionId, 3, 4), true).join();
        Map<String, Object> undone = collector.nextJson();
        assertThat(undone).containsEntry("type", "edit.applied").containsEntry("version", 4L);
        assertThat(list(undone, "atoms")).hasSameSizeAs(list(result, "atoms"));
    }

    @Test
    void should_report_progress_while_relaxing() throws Exception
    {
        StoredMolecule stored = store("CCO");
        Collector collector = new Collector();
        WebSocket socket = studio(collector);
        collector.next();

        socket.sendText(open(stored.id(), 1), true).join();
        Map<String, Object> opened = collector.nextJson();
        String sessionId = (String) opened.get("session_id");
        Map<String, Object> atom = list(opened, "atoms").getFirst();
        Map<String, Object> from = position(atom);
        socket.sendText(patch(sessionId, 1, true, (String) atom.get("id"), ((Number) from.get("x")).doubleValue() + 0.6,
                ((Number) from.get("y")).doubleValue(), ((Number) from.get("z")).doubleValue(), 2), true).join();
        collector.nextJson();

        socket.sendText(optimise(sessionId, 2, 3), true).join();
        List<Map<String, Object>> reports = new java.util.ArrayList<>();
        Map<String, Object> message;
        do
        {
            message = collector.nextJson();
            if (message.get("type").equals("optimise.progress"))
            {
                reports.add(message);
            }
        }
        while (!message.get("type").equals("optimise.result"));

        // the first report is sent at once, whatever the interval
        assertThat(reports).isNotEmpty();
        assertThat(reports.getFirst()).containsEntry("session_id", sessionId).containsEntry("iteration", 0L);
        assertThat(reports.getFirst().get("energy")).isInstanceOf(Double.class);
    }

    @Test
    void should_refuse_to_relax_while_an_edit_is_in_progress() throws Exception
    {
        StoredMolecule stored = store("CCO");
        Collector collector = new Collector();
        WebSocket socket = studio(collector);
        collector.next();

        socket.sendText(open(stored.id(), 1), true).join();
        Map<String, Object> opened = collector.nextJson();
        String sessionId = (String) opened.get("session_id");
        socket.sendText(patch(sessionId, 1, false, (String) list(opened, "atoms").getFirst().get("id"), 4, 4, 4, 2), true).join();
        assertThat(collector.nextJson()).containsEntry("type", "atom.patch.ack");

        socket.sendText(optimise(sessionId, 2, 3), true).join();

        Map<String, Object> refusal = collector.nextJson();
        assertThat(refusal).containsEntry("type", "error").containsEntry("answering", "optimise.start");
        assertThat(error(refusal)).containsEntry("code", "BUSY");
    }

    @Test
    void should_answer_an_optimise_made_on_a_stale_version_with_a_conflict() throws Exception
    {
        StoredMolecule stored = store("CCO");
        Collector collector = new Collector();
        WebSocket socket = studio(collector);
        collector.next();

        socket.sendText(open(stored.id(), 1), true).join();
        String sessionId = (String) collector.nextJson().get("session_id");

        socket.sendText(optimise(sessionId, 7, 2), true).join();

        Map<String, Object> conflict = collector.nextJson();
        assertThat(conflict).containsEntry("type", "conflict").containsEntry("expected_version", 1L).containsEntry("base_version", 7L);
    }

    @Test
    void should_say_whether_there_was_an_optimisation_to_cancel() throws Exception
    {
        StoredMolecule stored = store("CCO");
        Collector collector = new Collector();
        WebSocket socket = studio(collector);
        collector.next();

        socket.sendText(open(stored.id(), 1), true).join();
        String sessionId = (String) collector.nextJson().get("session_id");

        socket.sendText("{" + PROTOCOL + "\"type\": \"optimise.cancel\", \"sequence\": 2, \"session_id\": \"" + sessionId + "\"}", true).join();

        assertThat(collector.nextJson()).containsEntry("type", "optimise.cancel.ack").containsEntry("sequence", 2L).containsEntry("cancelling", false);
    }

    @Test
    void should_refuse_a_lock_it_cannot_make() throws Exception
    {
        StoredMolecule stored = store("CCO");
        Collector collector = new Collector();
        WebSocket socket = studio(collector);
        collector.next();

        socket.sendText(open(stored.id(), 1), true).join();
        String sessionId = (String) collector.nextJson().get("session_id");

        socket.sendText(lock(sessionId, 1, "a999", true, 2), true).join();
        assertThat(error(collector.nextJson())).containsEntry("code", "UNKNOWN_ID");

        socket.sendText("{" + PROTOCOL + "\"type\": \"atom.lock\", \"sequence\": 3, \"session_id\": \"" + sessionId
                + "\", \"base_version\": 1, \"atoms\": [\"a1\"]}", true).join();
        assertThat(error(collector.nextJson())).containsEntry("code", "INVALID_MESSAGE");

        socket.sendText(lock(sessionId, 9, "a1", true, 4), true).join();
        assertThat(collector.nextJson()).containsEntry("type", "conflict");

        socket.sendText(resync(sessionId, 5), true).join();
        assertThat(collector.nextJson()).containsEntry("version", 1L).containsEntry("locked", List.of());
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
        assertThat(undone).containsEntry("type", "edit.applied").containsEntry("version", 3L);
        assertThat(undone).containsEntry("can_undo", false).containsEntry("can_redo", true);
        assertThat(list(undone, "atoms")).hasSize(1);
        assertThat(list(undone, "atoms").getFirst()).containsEntry("id", first.get("id")).containsEntry("position", first.get("position"));

        socket.sendText(history("edit.redo", sessionId, 3, 5), true).join();
        Map<String, Object> redone = collector.nextJson();
        assertThat(redone).containsEntry("version", 4L);
        assertThat(list(redone, "atoms").getFirst().get("position")).isEqualTo(Map.of("x", 4.0, "y", 4.0, "z", 4.0));
        assertThat(redone).containsEntry("type", "edit.applied");

        socket.sendText(history("edit.redo", sessionId, 4, 6), true).join();
        assertThat(error(collector.nextJson())).containsEntry("code", "NOTHING_TO_REDO");
    }

    @Test
    void should_save_each_committed_edit_to_a_molecule_of_its_own() throws Exception
    {
        StoredMolecule stored = store("CCO");
        Collector collector = new Collector();
        WebSocket socket = studio(collector);
        collector.next();

        socket.sendText(open(stored.id(), 1), true).join();
        Map<String, Object> opened = collector.nextJson();
        String sessionId = (String) opened.get("session_id");
        assertThat(opened).containsEntry("saved_molecule_id", null);
        String atom = (String) list(opened, "atoms").getFirst().get("id");

        socket.sendText(patch(sessionId, 1, true, atom, 4, 5, 6, 2), true).join();
        Map<String, Object> ack = collector.nextJson();
        String saved = (String) ack.get("saved_molecule_id");

        assertThat(saved).isNotNull().isNotEqualTo(stored.id());
        assertThat(molecules.find(saved)).isPresent();
        assertThat(stored.studio().structure().conformers().getFirst().positions().getFirst())
                .isNotEqualTo(new com.ruben.chemistry.space.Vec3(4, 5, 6));

        // a second edit, and an undo, go to the same molecule
        socket.sendText(patch(sessionId, 2, true, atom, 7, 8, 9, 3), true).join();
        assertThat(collector.nextJson()).containsEntry("saved_molecule_id", saved);
        socket.sendText(history("edit.undo", sessionId, 3, 4), true).join();
        assertThat(collector.nextJson()).containsEntry("saved_molecule_id", saved);
        assertThat(molecules.find(saved).orElseThrow().studio().structure().conformers().getFirst().positions().getFirst())
                .isEqualTo(new com.ruben.chemistry.space.Vec3(4, 5, 6));

        // opening what was saved gives the edited structure back
        socket.sendText(open(saved, 5), true).join();
        Map<String, Object> reopened = collector.nextJson();
        assertThat(list(reopened, "atoms").getFirst().get("position")).isEqualTo(Map.of("x", 4.0, "y", 5.0, "z", 6.0));
        assertThat(reopened).containsEntry("smiles", "CCO");
        assertThat(list(reopened, "atoms")).hasSize(9);
    }

    @Test
    void should_not_save_before_anything_is_edited_or_while_an_edit_is_in_progress() throws Exception
    {
        StoredMolecule stored = store("CCO");
        Collector collector = new Collector();
        WebSocket socket = studio(collector);
        collector.next();

        socket.sendText(open(stored.id(), 1), true).join();
        Map<String, Object> opened = collector.nextJson();
        String sessionId = (String) opened.get("session_id");
        String atom = (String) list(opened, "atoms").getFirst().get("id");

        socket.sendText(patch(sessionId, 1, false, atom, 4, 5, 6, 2), true).join();
        assertThat(collector.nextJson()).containsEntry("saved_molecule_id", null);

        socket.sendText(patch(sessionId, 2, true, atom, 4, 5, 6, 3), true).join();
        assertThat(collector.nextJson().get("saved_molecule_id")).isNotNull();
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
        endpoint = new StudioSocketEndpoint(molecules, expiring, GeometryScheduler.disabled());
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
        return "{" + PROTOCOL + "\"type\": \"session.open\", \"sequence\": " + sequence + ", \"molecule_id\": \"" + moleculeId + "\"}";
    }

    /**
     * @param sessionId the session to ask about
     * @param sequence the message's sequence number
     * @return a {@code session.resync} message
     */
    private static String resync(String sessionId, int sequence)
    {
        return "{" + PROTOCOL + "\"type\": \"session.resync\", \"sequence\": " + sequence
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
        return "{" + PROTOCOL + "\"type\": \"smiles.submit\", \"sequence\": " + sequence + ", \"session_id\": \"" + sessionId
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
        return "{" + PROTOCOL + "\"type\": \"atom.patch\", \"sequence\": " + sequence + ", \"session_id\": \"" + sessionId
                + "\", \"base_version\": " + baseVersion + ", \"commit\": " + commit + ", \"atoms\": [{\"id\": \"" + atom
                + "\", \"position\": {\"x\": " + x + ", \"y\": " + y + ", \"z\": " + z + "}}]}";
    }

    /**
     * @param sessionId the session to edit
     * @param baseVersion the version the client says it was looking at
     * @param atom the atom to lock or unlock
     * @param locked whether to lock it
     * @param sequence the message's sequence number
     * @return an {@code atom.lock} message
     */
    private static String lock(String sessionId, long baseVersion, String atom, boolean locked, int sequence)
    {
        return "{" + PROTOCOL + "\"type\": \"atom.lock\", \"sequence\": " + sequence + ", \"session_id\": \"" + sessionId
                + "\", \"base_version\": " + baseVersion + ", \"atoms\": [\"" + atom + "\"], \"locked\": " + locked + "}";
    }

    /**
     * @param sessionId the session to edit
     * @param baseVersion the version the client says it was looking at
     * @param commit whether this ends the edit
     * @param x where the molecule's origin goes along x
     * @param y where it goes along y
     * @param z where it goes along z
     * @param sequence the message's sequence number
     * @return a {@code molecule.transform} message that moves the molecule without turning it
     */
    private static String transform(String sessionId, long baseVersion, boolean commit, double x, double y, double z, int sequence)
    {
        return "{" + PROTOCOL + "\"type\": \"molecule.transform\", \"sequence\": " + sequence + ", \"session_id\": \"" + sessionId
                + "\", \"base_version\": " + baseVersion + ", \"commit\": " + commit + ", \"position\": {\"x\": " + x + ", \"y\": " + y
                + ", \"z\": " + z + "}, \"rotation\": {\"x\": 0, \"y\": 0, \"z\": 0, \"w\": 1}}";
    }

    /**
     * @param sessionId the session whose camera it is
     * @param sequence the message's sequence number
     * @param fov the camera's field of view, in degrees
     * @return a {@code camera.set} message for {@link #cameraState(double)}
     */
    private static String camera(String sessionId, int sequence, double fov)
    {
        return "{" + PROTOCOL + "\"type\": \"camera.set\", \"sequence\": " + sequence + ", \"session_id\": \"" + sessionId
                + "\", \"position\": {\"x\": 1, \"y\": 2, \"z\": 3}, \"target\": {\"x\": 0, \"y\": 0.5, \"z\": -1}, \"fov\": " + fov
                + ", \"roll\": 15}";
    }

    /**
     * @param fov the camera's field of view, in degrees
     * @return the camera a state carries after {@link #camera(String, int, double)} sent it
     */
    private static Map<String, Object> cameraState(double fov)
    {
        return Map.of(
                "position", Map.of("x", 1.0, "y", 2.0, "z", 3.0),
                "target", Map.of("x", 0.0, "y", 0.5, "z", -1.0),
                "fov", fov,
                "roll", 15.0);
    }

    /**
     * @param type the message type, {@code bond.length} or {@code bond.torsion}
     * @param sessionId the session to edit
     * @param baseVersion the version the client says it was looking at
     * @param sequence the message's sequence number
     * @param fields the rest of the message's fields, written as JSON members
     * @return a bond edit message
     */
    private static String bondEdit(String type, String sessionId, long baseVersion, int sequence, String fields)
    {
        return "{" + PROTOCOL + "\"type\": \"" + type + "\", \"sequence\": " + sequence + ", \"session_id\": \"" + sessionId
                + "\", \"base_version\": " + baseVersion + ", " + fields + "}";
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
        return "{" + PROTOCOL + "\"type\": \"" + type + "\", \"sequence\": " + sequence + ", \"session_id\": \"" + sessionId
                + "\", \"base_version\": " + baseVersion + "}";
    }

    /**
     * @param sessionId the session to optimise
     * @param baseVersion the version the client says it was looking at
     * @param sequence the message's sequence number
     * @return an {@code optimise.start} message
     */
    private static String optimise(String sessionId, long baseVersion, int sequence)
    {
        return "{" + PROTOCOL + "\"type\": \"optimise.start\", \"sequence\": " + sequence + ", \"session_id\": \"" + sessionId
                + "\", \"base_version\": " + baseVersion + "}";
    }

    /**
     * @param collector what the connection has been sent
     * @return the next {@code optimise.result}, skipping the progress that comes before it
     */
    private static Map<String, Object> untilResult(Collector collector) throws InterruptedException
    {
        Map<String, Object> message = collector.nextJson();
        while (!message.get("type").equals("optimise.result"))
        {
            assertThat(message).containsEntry("type", "optimise.progress");
            message = collector.nextJson();
        }

        return message;
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
    private static Map<String, Object> map(Map<String, Object> body, String name)
    {
        return (Map<String, Object>) body.get(name);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Map<String, Object> body, String name)
    {
        return (List<Map<String, Object>>) body.get(name);
    }
}
