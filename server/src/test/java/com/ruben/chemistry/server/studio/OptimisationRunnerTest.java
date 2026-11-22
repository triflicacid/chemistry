package com.ruben.chemistry.server.studio;

import com.ruben.chemistry.server.molecule.MoleculeParser;
import com.ruben.chemistry.server.molecule.MoleculeStore;
import com.ruben.chemistry.space.Structure3D;
import com.ruben.chemistry.space.StructureFixer;
import com.ruben.chemistry.space.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OptimisationRunnerTest
{
    private final MoleculeStore molecules = new MoleculeStore();
    private final MoleculeParser parser = new MoleculeParser();
    private final BlockingQueue<OptimisationRunner.Result> finished = new LinkedBlockingQueue<>();
    private final List<Integer> reported = new CopyOnWriteArrayList<>();
    private OptimisationRunner runner;

    private final OptimisationRunner.Listener listener = new OptimisationRunner.Listener()
    {
        @Override
        public void progress(OptimisationRunner.Operation operation, int iteration, double energy)
        {
            reported.add(iteration);
        }

        @Override
        public void finished(OptimisationRunner.Operation operation, OptimisationRunner.Result result)
        {
            finished.add(result);
        }
    };

    @AfterEach
    void stop()
    {
        if (runner != null)
        {
            runner.close();
        }
    }

    @Test
    void relaxes_a_distorted_geometry_and_files_the_result_as_one_edit()
    {
        runner = new OptimisationRunner();
        StudioSession session = open("CCO");
        Vec3 from = session.positionOf(ids(session).getFirst());
        session.move(List.of(new StudioSession.Move(ids(session).getFirst(), from.plus(new Vec3(0.6, 0, 0)))), true);

        double shown = GeometryReport.of(session, session.snapshot()).strain();

        OptimisationRunner.Operation operation = runner.start(session, listener);
        OptimisationRunner.Result result = take();

        // the numbers are the ones the client's toolbar shows for the geometry before and after
        assertThat(result.strainBefore()).isEqualTo(shown);
        assertThat(result.strainAfter()).isEqualTo(GeometryReport.of(session, session.snapshot()).strain());
        assertThat(operation.version()).isEqualTo(2);
        assertThat(result.status()).isEqualTo(OptimisationRunner.COMPLETE);
        assertThat(result.version()).isEqualTo(3);
        assertThat(result.moved()).isNotEmpty();
        assertThat(result.strainAfter()).isLessThan(result.strainBefore());
        assertThat(session.version()).isEqualTo(3);
        assertThat(runner.runningFor(session.sessionId())).isEmpty();
    }

    @Test
    void holds_each_locked_atom_as_a_pinned_site()
    {
        AtomicReference<StructureFixer.Constraints> given = new AtomicReference<>();
        runner = new OptimisationRunner((structure, constraints, options, progress) ->
        {
            given.set(constraints);
            return unchanged(structure);
        }, Duration.ofMillis(1));
        StudioSession session = open("CCO");
        session.lock(List.of(ids(session).get(2), ids(session).get(5)), true);

        runner.start(session, listener);
        take();

        assertThat(given.get().sitePins().keySet()).containsExactlyInAnyOrder(2, 5);
    }

    @Test
    void reports_a_geometry_it_could_not_improve_as_unchanged()
    {
        runner = new OptimisationRunner((structure, constraints, options, progress) -> unchanged(structure), Duration.ofMillis(1));
        StudioSession session = open("CCO");

        runner.start(session, listener);
        OptimisationRunner.Result result = take();

        assertThat(result.status()).isEqualTo(OptimisationRunner.UNCHANGED);
        assertThat(result.strainBefore()).isEqualTo(result.strainAfter()).isEqualTo(0.0);
        assertThat(result.version()).isEqualTo(1);
        assertThat(session.canUndo()).isFalse();
    }

    @Test
    void reports_a_relaxation_that_could_not_keep_the_stereochemistry_as_failed()
    {
        runner = new OptimisationRunner((structure, constraints, options, progress) ->
                new StructureFixer.Fixed(structure, List.of(report(StructureFixer.Outcome.FAILED))), Duration.ofMillis(1));
        StudioSession session = open("CCO");

        runner.start(session, listener);
        OptimisationRunner.Result result = take();

        assertThat(result.status()).isEqualTo(OptimisationRunner.FAILED);
        assertThat(result.message()).contains("stereochemistry");
        assertThat(session.version()).isEqualTo(1);
    }

    @Test
    void reports_a_relaxation_that_threw_as_failed_and_changes_nothing()
    {
        runner = new OptimisationRunner((structure, constraints, options, progress) ->
        {
            throw new IllegalArgumentException("The structure's hydrogens cannot be reproduced as a molecule");
        }, Duration.ofMillis(1));
        StudioSession session = open("CCO");

        runner.start(session, listener);
        OptimisationRunner.Result result = take();

        assertThat(result.status()).isEqualTo(OptimisationRunner.FAILED);
        assertThat(result.message()).contains("hydrogens");
        assertThat(session.version()).isEqualTo(1);
        assertThat(runner.runningFor(session.sessionId())).isEmpty();
    }

    @Test
    void stops_when_asked_and_changes_nothing()
    {
        CountDownLatch started = new CountDownLatch(1);
        runner = new OptimisationRunner((structure, constraints, options, progress) ->
        {
            started.countDown();
            for (int iteration = 0; progress.continues(iteration, 1); iteration++)
            {
                sleep(1);
            }
            return shifted(structure);
        }, Duration.ofMillis(1));
        StudioSession session = open("CCO");

        runner.start(session, listener);
        await(started);

        assertThat(runner.cancel(session.sessionId(), "Cancelled")).isTrue();
        OptimisationRunner.Result result = take();

        assertThat(result.status()).isEqualTo(OptimisationRunner.CANCELLED);
        assertThat(result.message()).isEqualTo("Cancelled");
        assertThat(session.version()).isEqualTo(1);
        assertThat(session.canUndo()).isFalse();
    }

    @Test
    void drops_its_result_when_the_session_is_edited_while_it_runs()
    {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        runner = new OptimisationRunner((structure, constraints, options, progress) ->
        {
            started.countDown();
            await(release);
            return shifted(structure);
        }, Duration.ofMillis(1));
        StudioSession session = open("CCO");
        Vec3 before = session.positionOf(ids(session).getFirst());

        runner.start(session, listener);
        await(started);
        session.move(List.of(new StudioSession.Move(ids(session).get(1), new Vec3(5, 5, 5))), true);
        runner.supersede(session);
        release.countDown();
        OptimisationRunner.Result result = take();

        assertThat(result.status()).isEqualTo(OptimisationRunner.CANCELLED);
        assertThat(result.message()).contains("edited");
        assertThat(session.positionOf(ids(session).getFirst())).isEqualTo(before);
        assertThat(session.version()).isEqualTo(2);
    }

    @Test
    void drops_its_result_when_the_session_moved_on_without_being_told_to_supersede()
    {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        runner = new OptimisationRunner((structure, constraints, options, progress) ->
        {
            started.countDown();
            await(release);
            return shifted(structure);
        }, Duration.ofMillis(1));
        StudioSession session = open("CCO");

        runner.start(session, listener);
        await(started);
        session.lock(List.of(ids(session).getFirst()), true);
        release.countDown();

        assertThat(take().status()).isEqualTo(OptimisationRunner.CANCELLED);
    }

    @Test
    void does_not_supersede_an_optimisation_of_the_version_the_session_is_still_at()
    {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        runner = new OptimisationRunner((structure, constraints, options, progress) ->
        {
            started.countDown();
            await(release);
            return shifted(structure);
        }, Duration.ofMillis(1));
        StudioSession session = open("CCO");

        OptimisationRunner.Operation operation = runner.start(session, listener);
        await(started);
        runner.supersede(session);
        release.countDown();

        assertThat(operation.cancelled()).isFalse();
        assertThat(take().status()).isEqualTo(OptimisationRunner.COMPLETE);
    }

    @Test
    void optimises_a_session_one_at_a_time()
    {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        runner = new OptimisationRunner((structure, constraints, options, progress) ->
        {
            started.countDown();
            await(release);
            return unchanged(structure);
        }, Duration.ofMillis(1));
        StudioSession session = open("CCO");

        runner.start(session, listener);
        await(started);

        assertThatThrownBy(() -> runner.start(session, listener))
                .isInstanceOfSatisfying(StudioEditException.class, refused -> assertThat(refused.code()).isEqualTo("BUSY"));
        release.countDown();
        take();

        // once it has ended the session can be optimised again
        runner.start(session, listener);
        assertThat(take().status()).isEqualTo(OptimisationRunner.UNCHANGED);
    }

    @Test
    void has_nothing_to_cancel_for_a_session_it_is_not_optimising()
    {
        runner = new OptimisationRunner();

        assertThat(runner.cancel("nobody", "Cancelled")).isFalse();
    }

    @Test
    void reports_progress_no_more_often_than_its_interval_but_always_at_the_start()
    {
        runner = new OptimisationRunner((structure, constraints, options, progress) ->
        {
            for (int iteration = 0; iteration < 500; iteration++)
            {
                progress.continues(iteration, 1);
            }
            return unchanged(structure);
        }, Duration.ofHours(1));
        StudioSession session = open("CCO");

        runner.start(session, listener);
        take();

        assertThat(reported).containsExactly(0);
    }

    @Test
    void reports_every_step_when_the_interval_is_nothing()
    {
        runner = new OptimisationRunner((structure, constraints, options, progress) ->
        {
            for (int iteration = 0; iteration < 5; iteration++)
            {
                progress.continues(iteration, 1);
            }
            return unchanged(structure);
        }, Duration.ZERO);
        StudioSession session = open("CCO");

        runner.start(session, listener);
        take();

        assertThat(reported).containsExactly(0, 1, 2, 3, 4);
    }

    @Test
    void keeps_the_energy_of_every_step_however_few_it_reports()
    {
        runner = new OptimisationRunner((structure, constraints, options, progress) ->
        {
            for (int iteration = 0; iteration < 5; iteration++)
            {
                progress.continues(iteration, 10.0 - iteration);
            }
            return unchanged(structure);
        }, Duration.ofHours(1));
        StudioSession session = open("CCO");

        runner.start(session, listener);
        OptimisationRunner.Result result = take();

        assertThat(reported).containsExactly(0);
        assertThat(result.history()).extracting(OptimisationRunner.Step::iteration).containsExactly(0, 1, 2, 3, 4);
        assertThat(result.history()).extracting(OptimisationRunner.Step::energy).containsExactly(10.0, 9.0, 8.0, 7.0, 6.0);
    }

    @Test
    void has_no_history_for_a_result_that_never_began()
    {
        runner = new OptimisationRunner((structure, constraints, options, progress) ->
        {
            throw new IllegalStateException("broken");
        }, Duration.ZERO);
        StudioSession session = open("CCO");

        runner.start(session, listener);
        OptimisationRunner.Result result = take();

        assertThat(result.status()).isEqualTo(OptimisationRunner.FAILED);
        assertThat(result.history()).isEmpty();
    }

    private OptimisationRunner.Result take()
    {
        try
        {
            OptimisationRunner.Result result = finished.poll(20, TimeUnit.SECONDS);
            assertThat(result).as("the optimisation finished").isNotNull();
            return result;
        }
        catch (InterruptedException interrupted)
        {
            throw new AssertionError(interrupted);
        }
    }

    private static void await(CountDownLatch latch)
    {
        try
        {
            assertThat(latch.await(20, TimeUnit.SECONDS)).isTrue();
        }
        catch (InterruptedException interrupted)
        {
            throw new AssertionError(interrupted);
        }
    }

    private static void sleep(long milliseconds)
    {
        try
        {
            Thread.sleep(milliseconds);
        }
        catch (InterruptedException interrupted)
        {
            Thread.currentThread().interrupt();
        }
    }

    private static StructureFixer.Report report(StructureFixer.Outcome outcome)
    {
        return new StructureFixer.Report(outcome, 4, 1, List.of(), List.of(), List.of(), false);
    }

    /**
     * @param structure what was to be relaxed
     * @return it as it came, reported as having been improved
     */
    private static StructureFixer.Fixed unchanged(Structure3D structure)
    {
        return new StructureFixer.Fixed(structure, List.of(report(StructureFixer.Outcome.UNCHANGED)));
    }

    /**
     * @param structure what was to be relaxed
     * @return it with every site moved, reported as having been improved
     */
    private static StructureFixer.Fixed shifted(Structure3D structure)
    {
        List<Vec3> moved = new ArrayList<>();
        structure.conformers().getFirst().positions().forEach(position -> moved.add(position.plus(new Vec3(0.1, 0, 0))));
        Structure3D relaxed = new Structure3D(structure.sites(), structure.connections(), List.of(new Structure3D.Conformer(1, moved)));
        return new StructureFixer.Fixed(relaxed, List.of(report(StructureFixer.Outcome.FIXED)));
    }

    private static List<String> ids(StudioSession session)
    {
        return session.molecule().atoms().stream().map(session::atomId).toList();
    }

    /**
     * @param smiles a molecule
     * @return a session opened on it
     */
    private StudioSession open(String smiles)
    {
        return StudioSession.open("session-" + smiles.hashCode(), molecules.add(parser.parse(smiles)), 1);
    }
}
