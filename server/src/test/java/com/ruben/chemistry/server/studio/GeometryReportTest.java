package com.ruben.chemistry.server.studio;

import com.ruben.chemistry.server.molecule.MoleculeParser;
import com.ruben.chemistry.server.molecule.MoleculeStore;
import com.ruben.chemistry.space.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GeometryReportTest
{
    private final MoleculeStore molecules = new MoleculeStore();
    private final MoleculeParser parser = new MoleculeParser();

    @Test
    void finds_a_freshly_embedded_molecule_at_ease()
    {
        StudioSession session = open("CCO");

        GeometryReport report = GeometryReport.of(session, session.snapshot());

        assertThat(report.type()).isEqualTo("geometry.result");
        assertThat(report.status()).isEqualTo("complete");
        assertThat(report.sessionId()).isEqualTo(session.sessionId());
        assertThat(report.version()).isEqualTo(1);
        assertThat(report.strain()).isZero();
        assertThat(report.findings()).isEmpty();
        assertThat(report.clashes()).isEmpty();
        assertThat(report.message()).isNull();
    }

    @Test
    void finds_atoms_put_on_top_of_each_other_as_strained_and_clashing()
    {
        StudioSession session = open("CCO");
        double calm = GeometryReport.of(session, session.snapshot()).strain();
        List<String> ids = ids(session);
        session.move(List.of(new StudioSession.Move(ids.get(0), session.positionOf(ids.get(2)))), true);

        GeometryReport report = GeometryReport.of(session, session.snapshot());

        assertThat(report.version()).isEqualTo(2);
        assertThat(report.strain()).isGreaterThan(calm + 10);
        assertThat(report.clashes()).isNotEmpty();
        assertThat(report.findings()).isNotEmpty();
        assertThat(report.atomStrain()).extracting(GeometryReport.AtomStrain::id).contains(ids.get(0), ids.get(2));
    }

    @Test
    void names_the_atoms_of_a_finding_by_the_session_s_identifiers_and_lists_the_worst_first()
    {
        StudioSession session = open("CCO");
        List<String> ids = ids(session);
        // the carbon pulled far from the one it is bonded to, which is the worst thing wrong
        session.move(List.of(new StudioSession.Move(ids.get(1), new Vec3(30, 0, 0))), true);

        GeometryReport report = GeometryReport.of(session, session.snapshot());

        GeometryReport.Finding worst = report.findings().getFirst();
        assertThat(worst.kind()).isEqualTo("bond");
        assertThat(worst.atoms()).hasSize(2).contains(ids.get(1));
        assertThat(ids).containsAll(worst.atoms());
        assertThat(worst.actual()).isGreaterThan(worst.ideal());
        assertThat(report.findings()).isSortedAccordingTo((first, second) -> Double.compare(second.severity(), first.severity()));
        assertThat(report.findings()).allSatisfy(finding -> assertThat(finding.severity()).isGreaterThan(1));
    }

    @Test
    void lists_no_more_findings_than_it_is_allowed()
    {
        StudioSession session = open("CCCCCCCCCCCCCCCCCCCC");
        // every atom in one place
        List<StudioSession.Move> moves = ids(session).stream().map(id -> new StudioSession.Move(id, new Vec3(0, 0, 0))).toList();
        session.move(moves, true);

        GeometryReport report = GeometryReport.of(session, session.snapshot());

        assertThat(report.findings()).hasSize(GeometryReport.MAX_FINDINGS);
        assertThat(report.clashes().size()).isLessThanOrEqualTo(GeometryReport.MAX_CLASHES);
    }

    @Test
    void lists_only_the_atoms_whose_share_is_worth_showing()
    {
        StudioSession session = open("CCO");

        GeometryReport report = GeometryReport.of(session, session.snapshot());

        assertThat(report.atomStrain()).allSatisfy(share -> assertThat(share.strain()).isGreaterThanOrEqualTo(GeometryReport.MIN_ATOM_STRAIN));
    }

    @Test
    void describes_the_geometry_it_was_given_and_not_the_one_the_session_has_moved_on_to()
    {
        StudioSession session = open("CCO");
        StudioSession.Snapshot before = session.snapshot();
        double calm = GeometryReport.of(session, before).strain();
        List<String> ids = ids(session);
        session.move(List.of(new StudioSession.Move(ids.get(0), session.positionOf(ids.get(2)))), true);

        GeometryReport report = GeometryReport.of(session, before);

        assertThat(report.version()).isEqualTo(1);
        assertThat(report.strain()).isEqualTo(calm);
    }

    @Test
    void says_that_it_failed_and_why()
    {
        StudioSession session = open("CCO");

        GeometryReport failed = GeometryReport.failed(session, 3, "no good");

        assertThat(failed.status()).isEqualTo("failed");
        assertThat(failed.version()).isEqualTo(3);
        assertThat(failed.message()).isEqualTo("no good");
        assertThat(failed.strain()).isNull();
        assertThat(failed.findings()).isEmpty();
    }

    private static List<String> ids(StudioSession session)
    {
        return session.molecule().atoms().stream().map(session::atomId).toList();
    }

    private StudioSession open(String smiles)
    {
        return StudioSession.open("session-" + smiles.hashCode(), molecules.add(parser.parse(smiles)), 1);
    }
}
