package vn.minhtri.xuongin.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import vn.minhtri.xuongin.export.TifQueue.Task;

class TifQueueTest {

    @Test
    void newestJobFirstAsABlockSheetsFromOne() throws Exception {
        TifQueue queue = new TifQueue();
        queue.enqueueJob("a", 2);
        queue.enqueueJob("b", 2);
        // b finished last: the worker is looking at it, a may be abandoned.
        assertThat(queue.snapshot())
                .containsExactly(new Task("b", 0), new Task("b", 1), new Task("a", 0), new Task("a", 1));
        assertThat(queue.poll(0, TimeUnit.MILLISECONDS)).isEqualTo(new Task("b", 0));
    }

    @Test
    void newJobGoesAfterTheSheetsSomeoneAskedFor() throws Exception {
        TifQueue queue = new TifQueue();
        queue.enqueueJob("a", 3);
        queue.prioritize("a", 2);
        queue.enqueueJob("b", 1);
        assertThat(queue.snapshot())
                .containsExactly(new Task("a", 2), new Task("b", 0), new Task("a", 0), new Task("a", 1));
        assertThat(queue.next(0, TimeUnit.MILLISECONDS)).isEqualTo(new TifQueue.Next(new Task("a", 2), true));
        assertThat(queue.next(0, TimeUnit.MILLISECONDS)).isEqualTo(new TifQueue.Next(new Task("b", 0), false));
        assertThat(queue.next(0, TimeUnit.MILLISECONDS)).isEqualTo(new TifQueue.Next(new Task("a", 0), false));
    }

    @Test
    void prioritizeMovesTheSheetToTheFrontWithoutDuplicating() {
        TifQueue queue = new TifQueue();
        queue.enqueueJob("a", 3);
        queue.enqueueJob("b", 2);
        queue.prioritize("b", 1);
        assertThat(queue.snapshot())
                .containsExactly(new Task("b", 1), new Task("b", 0), new Task("a", 0), new Task("a", 1), new Task("a", 2));
        queue.prioritize("a", 2);
        assertThat(queue.snapshot()).first().isEqualTo(new Task("a", 2));
        assertThat(queue.snapshot()).hasSize(5);
    }

    @Test
    void prioritizeQueuesASheetThatWasNotWaiting() {
        TifQueue queue = new TifQueue();
        queue.enqueueJob("a", 1);
        queue.prioritize("z", 4);
        assertThat(queue.snapshot()).containsExactly(new Task("z", 4), new Task("a", 0));
        assertThat(queue.contains("z", 4)).isTrue();
    }

    @Test
    void enqueueTwiceKeepsOneEntryPerSheet() {
        TifQueue queue = new TifQueue();
        queue.enqueueJob("a", 2);
        queue.enqueueJob("a", 2);
        assertThat(queue.snapshot()).hasSize(2);
    }

    @Test
    void removeJobDropsOnlyItsSheets() {
        TifQueue queue = new TifQueue();
        queue.enqueueJob("a", 2);
        queue.enqueueJob("b", 1);
        queue.prioritize("a", 1);
        queue.removeJob("a");
        assertThat(queue.snapshot()).containsExactly(new Task("b", 0));
        // A later sheet with the same key is not treated as asked for.
        queue.enqueueJob("a", 2);
        assertThat(queue.snapshot()).containsExactly(new Task("a", 0), new Task("a", 1), new Task("b", 0));
    }

    @Test
    void pollTimesOutWhenEmptyAndWakesOnEnqueue() throws Exception {
        TifQueue queue = new TifQueue();
        assertThat(queue.poll(20, TimeUnit.MILLISECONDS)).isNull();
        Thread producer = new Thread(() -> queue.enqueueJob("late", 1));
        producer.start();
        assertThat(queue.poll(5, TimeUnit.SECONDS)).isEqualTo(new Task("late", 0));
        producer.join();
    }
}
