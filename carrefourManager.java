package mini.projet_dac;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import static mini.projet_dac.MiniProjet_DAC.*;

public class carrefourManager {

    //===========================================================================
    // Variables Declaration
    //===========================================================================

    // Swing Timer for the countdown display on the settings panel.
    // Runs on the EDT every 1 second. Decrements the displayed timer value.
    static Timer mytimer = new Timer(1000, new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (!stopButtonIsActive.get()) {
                seconds.decrementAndGet();
                if (seconds.get() == 0) {
                    seconds.set(duree_de_feu.get() / 1000);
                }
                lightTimer.setText(String.valueOf(seconds.get()));
            }
        }
    });

    // =========================================================================
    // Concurrency technique: ReentrantLock + Conditions
    // =========================================================================
    // The single ReentrantLock 'verro' protects ALL shared mutable state:
    //   - feuVert1 / feuVert2  (traffic light state)
    //   - nmbrVoitureIntersectionV1 / V2  (intersection occupancy per road)
    //   - voit1stopPositionAtomic / voit2stopPositionAtomic  (lane spacing)
    // Conditions derived from 'verro' let threads wait efficiently:
    //   - feuVertVoie1 / feuVertVoie2  : cars wait for their green light
    //   - voie1_Cars_In_Intersection / voie2_Cars_In_Intersection :
    //         light controller waits for cars to clear before switching
    //   - carSpacingChanged : cars waiting for space in front signal each other
    //   - mainRestartTimer : producer thread signals after light-duration change
    // =========================================================================
    static Lock verro = new ReentrantLock();
    Condition feuVertVoie1 = verro.newCondition();
    Condition feuVertVoie2 = verro.newCondition();
    Condition voie2_Cars_In_Intersection = verro.newCondition();
    Condition voie1_Cars_In_Intersection = verro.newCondition();

    // Concurrency technique: Condition for lane-spacing notification.
    // Instead of busy-waiting pixel-by-pixel, trailing cars await this
    // condition, and a car leaving the queue signals it so the next car
    // can immediately proceed.
    Condition carSpacingChanged = verro.newCondition();

    // Concurrency technique: volatile ensures cross-thread visibility of
    // the traffic-light booleans WITHOUT requiring the lock for every
    // single-pixel movement step.  The authoritative reads/writes still
    // happen inside 'verro', but the volatile qualifier prevents a stale
    // cache from letting a car slip through on a red light between lock
    // acquisitions.
    volatile boolean feuVert1 = true;
    volatile boolean feuVert2 = false;

    // BUG FIX: separate intersection counters per road.
    // The original code used a single 'nmbrVoitureIntersection' for both
    // roads.  This meant a Voie-1 car decrementing the counter could
    // falsely signal the light-controller that Voie-2 is clear (or vice
    // versa), leading to premature light switches and collisions.
    // Now each road has its own counter; the light controller waits on
    // the correct counter reaching zero before switching.
    int nmbrVoitureIntersectionV1 = 0;
    int nmbrVoitureIntersectionV2 = 0;

    static Condition mainRestartTimer = verro.newCondition();
    static AtomicBoolean mainStopedTheTimer = new AtomicBoolean(false);

    // Concurrency technique: Semaphore for start/stop gating.
    // A fair semaphore ensures FIFO ordering of blocked threads when
    // START is pressed, unlike Condition.signalAll() which has no
    // ordering guarantee.  STOP drains permits; START releases them.
    static Semaphore restart = new Semaphore(0, true);

    // Fixed x-positions for the 4 lanes on Voie 1 (vertical road)
    int[] voie1PositionPossible = {420, 470, 530, 580};
    // Fixed y-positions for the 4 lanes on Voie 2 (horizontal road)
    int[] voie2PositionPossible = {327, 369, 457, 500};

    // BUG FIX: lane stop-position queues.
    // The stop position determines where the next car in each lane must
    // halt before the intersection.  Each time a car arrives, it lowers
    // the stop position by CAR_SPACING so the next car queues behind it.
    // When a car is admitted through the intersection (green light), it
    // restores the stop position so trailing cars can advance.
    //
    // Original bug: the get-then-set on AtomicIntegerArray was NOT atomic
    // (two cars could read the same value and both decrement to the same
    // slot, causing overlap).  Fix: every read-modify-write is performed
    // while holding 'verro'.
    static final int CAR_SPACING = 80;

    // Base stop positions (where the first car in a lane stops).
    // Voie 1 cars travel top-to-bottom; they stop at y=225 before the
    // intersection.  Voie 2 cars travel left-to-right; they stop at x=310.
    int[] voit1stopPosition = {225, 225, 225, 225};
    AtomicIntegerArray voit1stopPositionAtomic = new AtomicIntegerArray(voit1stopPosition);

    int[] voit2stopPosition = {310, 310, 310, 310};
    AtomicIntegerArray voit2stopPositionAtomic = new AtomicIntegerArray(voit2stopPosition);

    //===========================================================================
    // Helper: thread-safe Swing UI update
    //===========================================================================

    // Concurrency technique: synchronized monitor + EDT confinement.
    // Multiple car threads call setBounds concurrently.  The synchronized
    // keyword serialises these calls so that Swing component state is
    // never corrupted by interleaved updates.  If we are already on the
    // EDT we update directly; otherwise we schedule via invokeLater.
    private synchronized void setCarBounds(JPanel car, int x, int y, int w, int h) {
        if (SwingUtilities.isEventDispatchThread()) {
            car.setBounds(x, y, w, h);
            return;
        }
        SwingUtilities.invokeLater(() -> car.setBounds(x, y, w, h));
    }

    //===========================================================================
    // pauseIfStopped — common stop-button check for every movement step
    //===========================================================================
    // Extracted to avoid duplicating the semaphore-acquire pattern in every
    // loop.  If STOP has been pressed, the calling thread blocks until
    // START releases a permit.
    private void pauseIfStopped() throws InterruptedException {
        if (stopButtonIsActive.get()) {
            restart.acquire();
        }
    }

    //===========================================================================
    // Intersection — traffic-light switching logic (called by lightManager)
    //===========================================================================
    // Flow:
    //   1. Wait if STOP is active (semaphore gate).
    //   2. Lock 'verro'.
    //   3. Set both lights to yellow (orange).
    //   4. Set the current road's green flag to false.
    //   5. Wait (Condition) until all cars of that road have left the
    //      intersection (per-road counter == 0).
    //   6. Set the OTHER road's green flag to true, update UI lights,
    //      and signal all cars waiting for that green.
    //   7. Handle light-duration changes if pending.
    //   8. Reset timer, start Swing Timer, unlock.
    public void Intersection() {
        try {
            pauseIfStopped();
        } catch (InterruptedException ex) {
            System.out.println(ex.getMessage());
        }

        verro.lock();
        try {
            // --- yellow phase ---
            if (mytimer.isRunning()) {
                mytimer.stop();
                feuVoie1Orange.setEnabled(true);
                feuVoie2Orange.setEnabled(true);
                feuVoie1Green.setEnabled(false);
                feuVoie2Red.setEnabled(false);
                feuVoie1Red.setEnabled(false);
                feuVoie2Green.setEnabled(false);
            }

            if (feuVert1) {
                // Switching FROM green-1 TO green-2
                feuVert1 = false;

                // Concurrency technique: Condition await in while-loop
                // guards against spurious wakeups.  We wait until every
                // Voie-1 car that entered the intersection has exited.
                while (nmbrVoitureIntersectionV1 != 0) {
                    voie1_Cars_In_Intersection.await();
                }

                feuVert2 = true;
                feuVoie1Orange.setEnabled(false);
                feuVoie2Orange.setEnabled(false);
                feuVoie1Green.setEnabled(false);
                feuVoie2Red.setEnabled(false);
                feuVoie1Red.setEnabled(true);
                feuVoie2Green.setEnabled(true);
                // Wake ALL Voie-2 cars waiting for green
                feuVertVoie2.signalAll();

            } else {
                // Switching FROM green-2 TO green-1
                feuVert2 = false;

                while (nmbrVoitureIntersectionV2 != 0) {
                    voie2_Cars_In_Intersection.await();
                }

                feuVert1 = true;
                feuVoie1Orange.setEnabled(false);
                feuVoie2Orange.setEnabled(false);
                feuVoie1Green.setEnabled(true);
                feuVoie2Red.setEnabled(true);
                feuVoie1Red.setEnabled(false);
                feuVoie2Green.setEnabled(false);
                // Wake ALL Voie-1 cars waiting for green
                feuVertVoie1.signalAll();
            }

            // If the user changed light duration, wait for the producer
            // thread to signal that old cars have cleared.
            if (mainStopedTheTimer.get()) {
                mainRestartTimer.await();
            }
            seconds.set(duree_de_feu.get() / 1000);
            lightTimer.setText(String.valueOf(seconds.get()));
            mytimer.start();

        } catch (InterruptedException ex) {
            System.out.println(ex.getMessage());
        } finally {
            verro.unlock();
        }
    }

    //===========================================================================
    // traversee1 — Voie 1 (vertical, top-to-bottom) car movement
    //===========================================================================
    // Completely rewritten with a clean 4-phase structure:
    //   Phase 1: approach — move from off-screen (y=-60) to the stop position.
    //   Phase 2: wait    — wait for green light (under lock).
    //   Phase 3: cross   — move through the intersection (y to 555).
    //   Phase 4: exit    — move from 555 to off-screen (y=830).
    //
    // Between phases 2 and 3 the intersection counter is incremented.
    // At the end of phase 3 the counter is decremented.
    public void traversee1(JPanel C, int p, int vitess) {
        try {
            // === Phase 1: Approach the stop position ===
            // Concurrency technique: Lock protects the read-modify-write
            // on the stop position array.  Without the lock, two cars
            // arriving simultaneously could read the same stop value and
            // both park at the same y-coordinate (overlap bug).
            int myStopPos;
            verro.lock();
            try {
                // Atomically claim our stop position and push the queue
                // back for the next car.
                myStopPos = voit1stopPositionAtomic.get(p - 1);
                voit1stopPositionAtomic.set(p - 1, myStopPos - CAR_SPACING);
            } finally {
                verro.unlock();
            }

            for (int j = -60; j < myStopPos; j++) {
                pauseIfStopped();
                setCarBounds(C, voie1PositionPossible[p - 1], j, 30, 60);
                Thread.sleep(vitess);
            }

            // Car is now visually at the stop position.
            setCarBounds(C, voie1PositionPossible[p - 1], myStopPos, 30, 60);

            // === Phase 2: Wait for green light ===
            // Concurrency technique: Condition await in while-loop to
            // guard against spurious wakeups.  The car blocks here until
            // the light controller sets feuVert1 = true and signals
            // feuVertVoie1.
            //
            // BUG FIX (red-light violation): the intersection counter is
            // incremented INSIDE the same critical section as the green-
            // light check.  The original code released the lock between
            // checking the light and incrementing, creating a window
            // where the light controller could switch before the counter
            // reflected this car's presence.
            verro.lock();
            try {
                while (!feuVert1) {
                    feuVertVoie1.await();
                }
                // Increment BEFORE releasing lock — the light controller
                // cannot switch until this counter reaches zero.
                nmbrVoitureIntersectionV1++;
            } finally {
                verro.unlock();
            }

            // === Phase 3: Cross the intersection ===
            // Restore the stop position so the next queued car can advance.
            verro.lock();
            try {
                voit1stopPositionAtomic.set(p - 1,
                        voit1stopPositionAtomic.get(p - 1) + CAR_SPACING);
                // Concurrency technique: Condition signal for lane spacing.
                // Trailing cars awaiting space are woken immediately
                // instead of busy-waiting pixel-by-pixel.
                carSpacingChanged.signalAll();
            } finally {
                verro.unlock();
            }

            for (int j = myStopPos; j < 555; j++) {
                pauseIfStopped();
                setCarBounds(C, voie1PositionPossible[p - 1], j, 30, 60);
                Thread.sleep(vitess);
            }

            // Decrement intersection counter and signal light controller
            // if this was the last car.
            verro.lock();
            try {
                nmbrVoitureIntersectionV1--;
                if (nmbrVoitureIntersectionV1 == 0 && !feuVert1) {
                    voie1_Cars_In_Intersection.signal();
                }
            } finally {
                verro.unlock();
            }

            // === Phase 4: Exit off-screen ===
            for (int j = 555; j < 830; j++) {
                pauseIfStopped();
                setCarBounds(C, voie1PositionPossible[p - 1], j, 30, 60);
                Thread.sleep(vitess);
            }

        } catch (InterruptedException ex) {
            Logger.getLogger(carrefourManager.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    //===========================================================================
    // traversee2 — Voie 2 (horizontal, left-to-right) car movement
    //===========================================================================
    // Same four-phase structure as traversee1 but for the horizontal road.
    // Cars move left-to-right (x from -60 to 1035).
    // Stop position is on the x-axis at base 310.
    // Intersection ends at x = 640.
    public void traversee2(JPanel C, int p, int vitess) {
        try {
            // === Phase 1: Approach the stop position ===
            int myStopPos;
            verro.lock();
            try {
                myStopPos = voit2stopPositionAtomic.get(p - 1);
                voit2stopPositionAtomic.set(p - 1, myStopPos - CAR_SPACING);
            } finally {
                verro.unlock();
            }

            for (int j = -60; j < myStopPos; j++) {
                pauseIfStopped();
                setCarBounds(C, j, voie2PositionPossible[p - 1], 60, 30);
                Thread.sleep(vitess);
            }

            setCarBounds(C, myStopPos, voie2PositionPossible[p - 1], 60, 30);

            // === Phase 2: Wait for green light ===
            verro.lock();
            try {
                while (!feuVert2) {
                    feuVertVoie2.await();
                }
                nmbrVoitureIntersectionV2++;
            } finally {
                verro.unlock();
            }

            // === Phase 3: Cross the intersection ===
            verro.lock();
            try {
                voit2stopPositionAtomic.set(p - 1,
                        voit2stopPositionAtomic.get(p - 1) + CAR_SPACING);
                carSpacingChanged.signalAll();
            } finally {
                verro.unlock();
            }

            for (int j = myStopPos; j < 640; j++) {
                pauseIfStopped();
                setCarBounds(C, j, voie2PositionPossible[p - 1], 60, 30);
                Thread.sleep(vitess);
            }

            verro.lock();
            try {
                nmbrVoitureIntersectionV2--;
                if (nmbrVoitureIntersectionV2 == 0 && !feuVert2) {
                    voie2_Cars_In_Intersection.signal();
                }
            } finally {
                verro.unlock();
            }

            // === Phase 4: Exit off-screen ===
            for (int j = 640; j < 1035; j++) {
                pauseIfStopped();
                setCarBounds(C, j, voie2PositionPossible[p - 1], 60, 30);
                Thread.sleep(vitess);
            }

        } catch (InterruptedException ex) {
            Logger.getLogger(carrefourManager.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

}
