package com.ruben.chemistry.server.studio;

import com.ruben.chemistry.server.molecule.MoleculeParser;
import com.ruben.chemistry.server.molecule.MoleculeStore;
import com.ruben.chemistry.space.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeometrySchedulerTest
{
    private final MoleculeStore molecules = new MoleculeStore();
    private final MoleculeParser parser = new MoleculeParser();
    private GeometryScheduler scheduler;

    @AfterEach
    void stop()
    {
        if (scheduler != null)
        {
            scheduler.close();
        }
    }

    @Test
    void delivers_the_result_for_the_version_it_found_after_the_debounce()
    {
        scheduler = new GeometryScheduler(Duration.ofMillis(20));
        StudioSession session = open("CCO");
        BlockingQueue<GeometryReport> delivered = new LinkedBlockingQueue<>();

        scheduler.schedule(session, delivered::add);

        GeometryReport report = take(delivered);
        assertThat(report.version()).isEqualTo(1);
        assertThat(report.status()).isEqualTo("complete");
    }

    @Test
    void examines_a_session_once_for_a_run_of_requests_made_inside_the_debounce()
    {
        AtomicInteger examined = new AtomicInteger();
        scheduler = new GeometryScheduler(Duration.ofMillis(150), (session, snapshot) ->
        {
            examined.incrementAndGet();
            return GeometryReport.of(session, snapshot);
        });
        StudioSession session = open("CCO");
        BlockingQueue<GeometryReport> delivered = new LinkedBlockingQueue<>();

        for (int request = 0; request < 5; request++)
        {
            scheduler.schedule(session, delivered::add);
        }

        take(delivered);
        pause(300);
        assertThat(examined).hasValue(1);
        assertThat(delivered).isEmpty();
    }

    @Test
    void examines_the_latest_geometry_when_the_session_moved_during_the_debounce()
    {
        scheduler = new GeometryScheduler(Duration.ofMillis(100));
        StudioSession session = open("CCO");
        BlockingQueue<GeometryReport> delivered = new LinkedBlockingQueue<>();

        scheduler.schedule(session, delivered::add);
        session.move(List.of(new StudioSession.Move(ids(session).getFirst(), new Vec3(1, 2, 3))), true);
        scheduler.schedule(session, delivered::add);

        assertThat(take(delivered).version()).isEqualTo(2);
    }

    @Test
    void drops_a_result_when_the_session_changed_while_it_was_being_made()
    {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch go = new CountDownLatch(1);
        scheduler = new GeometryScheduler(Duration.ofMillis(10), (session, snapshot) ->
        {
            started.countDown();
            try
            {
                go.await(5, TimeUnit.SECONDS);
            }
            catch (InterruptedException interrupted)
            {
                Thread.currentThread().interrupt();
            }
            return GeometryReport.of(session, snapshot);
        });
        StudioSession session = open("CCO");
        BlockingQueue<GeometryReport> delivered = new LinkedBlockingQueue<>();

        scheduler.schedule(session, delivered::add);
        await(started);
        session.move(List.of(new StudioSession.Move(ids(session).getFirst(), new Vec3(1, 2, 3))), true);
        go.countDown();

        pause(300);
        assertThat(delivered).isEmpty();
    }

    @Test
    void delivers_a_failure_as_a_report_that_says_so()
    {
        scheduler = new GeometryScheduler(Duration.ofMillis(10), (session, snapshot) ->
        {
            throw new IllegalStateException("cannot examine this");
        });
        StudioSession session = open("CCO");
        BlockingQueue<GeometryReport> delivered = new LinkedBlockingQueue<>();

        scheduler.schedule(session, delivered::add);

        GeometryReport report = take(delivered);
        assertThat(report.status()).isEqualTo("failed");
        assertThat(report.message()).isEqualTo("cannot examine this");
        assertThat(report.version()).isEqualTo(1);
    }

    @Test
    void does_nothing_when_disabled()
    {
        scheduler = GeometryScheduler.disabled();
        BlockingQueue<GeometryReport> delivered = new LinkedBlockingQueue<>();

        scheduler.schedule(open("CCO"), delivered::add);

        pause(100);
        assertThat(delivered).isEmpty();
    }

    @Test
    void refuses_a_negative_debounce()
    {
        assertThatThrownBy(() -> new GeometryScheduler(Duration.ofMillis(-1))).isInstanceOf(IllegalArgumentException.class);
    }

    private static GeometryReport take(BlockingQueue<GeometryReport> delivered)
    {
        try
        {
            GeometryReport report = delivered.poll(5, TimeUnit.SECONDS);
            assertThat(report).as("a result arrived").isNotNull();
            return report;
        }
        catch (InterruptedException interrupted)
        {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    private static void await(CountDownLatch latch)
    {
        try
        {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        }
        catch (InterruptedException interrupted)
        {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    private static void pause(long millis)
    {
        try
        {
            Thread.sleep(millis);
        }
        catch (InterruptedException interrupted)
        {
            Thread.currentThread().interrupt();
        }
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
