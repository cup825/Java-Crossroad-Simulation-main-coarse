package mini.projet_dac;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
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

    //<editor-fold defaultstate="collapsed" desc="Variables Declaration">

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

    /*
     * Concurrency technique: ReentrantLock
     * This lock protects the shared traffic-light state, waiting queues,
     * stop positions, and the number of cars inside the intersection.
     * These values are accessed by multiple car threads and the traffic light controller thread.
     */
    static Lock verro = new ReentrantLock();

    /*
     * Concurrency technique: Condition
     * Car threads wait when they cannot pass, and the traffic light controller thread
     * waits until the current road has cleared the intersection.
     * This avoids busy-waiting.
     */
    Condition feuVertVoie1 = verro.newCondition();
    Condition feuVertVoie2 = verro.newCondition();
    Condition voie2_Cars_In_Intersection = verro.newCondition();
    Condition voie1_Cars_In_Intersection = verro.newCondition();

    // Used to notify threads when car spacing or stop-position state changes.
    Condition carSpacingChanged = verro.newCondition();

    /*
     * Used to notify waiting cars when the overall traffic state changes.
     * For example, after a light switch, the end of a yellow phase,
     * or when the front car leaves its waiting position.
     */
    Condition trafficStateChanged = verro.newCondition();

    /*
     * Concurrency technique: volatile
     * These traffic-light flags are written by the traffic light controller thread and read by car threads.
     * volatile ensures that updates to these flags are visible to other threads.
     */
    volatile boolean feuVert1 = true;
    volatile boolean feuVert2 = false;

    /*
     * Yellow-phase flag.
     * During the yellow phase, cars still waiting are not allowed to get new permission to enter the intersection.
     * Cars that have already entered the intersection can continue passing.
     */
    volatile boolean yellowPhase = false;

    /*
     * Count cars inside the intersection separately for the two directions.
     * When switching lights, only the current direction needs to be cleared.
     */
    int nmbrVoitureIntersectionV1 = 0;
    int nmbrVoitureIntersectionV2 = 0;

    /*
     * Front stop positions.
     * Only the car at the front stop position can enter the intersection
     * when the light is green and it is not the yellow phase.
     */
    static final int VOIE1_FRONT_STOP = 225;
    static final int VOIE2_FRONT_STOP = 310;

    /*
     * Records the cars waiting in each lane and their current waiting positions.
     * HashMap is not thread-safe, so all related operations are protected by verro.
     */
    @SuppressWarnings("unchecked")
    private final Map<JPanel, Integer>[] voie1WaitingPositions = new Map[]{
            new HashMap<JPanel, Integer>(),
            new HashMap<JPanel, Integer>(),
            new HashMap<JPanel, Integer>(),
            new HashMap<JPanel, Integer>()
    };

    @SuppressWarnings("unchecked")
    private final Map<JPanel, Integer>[] voie2WaitingPositions = new Map[]{
            new HashMap<JPanel, Integer>(),
            new HashMap<JPanel, Integer>(),
            new HashMap<JPanel, Integer>(),
            new HashMap<JPanel, Integer>()
    };

    /*
     * Records stop positions that have already been reserved.
     * Before a car moves to a target position, it reserves that position first,
     * so another car cannot move to the same position at the same time.
     */
    @SuppressWarnings("unchecked")
    private final Set<Integer>[] voie1ReservedPositions = new Set[]{
            new HashSet<Integer>(),
            new HashSet<Integer>(),
            new HashSet<Integer>(),
            new HashSet<Integer>()
    };

    @SuppressWarnings("unchecked")
    private final Set<Integer>[] voie2ReservedPositions = new Set[]{
            new HashSet<Integer>(),
            new HashSet<Integer>(),
            new HashSet<Integer>(),
            new HashSet<Integer>()
    };

    /*
     * When the main UI changes the traffic-light duration, the traffic light controller thread
     * can wait here until the new setting has been applied.
     */
    static Condition mainRestartTimer = verro.newCondition();

    /*
     * Concurrency technique: AtomicBoolean
     * This flag is shared by the UI thread and the traffic light controller thread.
     */
    static AtomicBoolean mainStopedTheTimer = new AtomicBoolean(false);

    /*
     * Concurrency technique: Semaphore
     * Used as the START / STOP gate.
     * In STOP state, threads wait in pauseIfStopped().
     * After START, they continue running.
     */
    static Semaphore restart = new Semaphore(0, true);

    int[] voie1PositionPossible = {420, 470, 530, 580};
    int[] voie2PositionPossible = {327, 369, 457, 500};

    static final int CAR_SPACING = 80;

    int[] voit1stopPosition = {225, 225, 225, 225};

    /*
     * Concurrency technique: AtomicIntegerArray
     * Stores the next available waiting position for each lane.
     * A single read or write is atomic; when get and set are used together,
     * the operation is still protected by verro.
     */
    AtomicIntegerArray voit1stopPositionAtomic = new AtomicIntegerArray(voit1stopPosition);

    int[] voit2stopPosition = {310, 310, 310, 310};

    // Waiting-position array for Voie2. It has the same role as the Voie1 array.
    AtomicIntegerArray voit2stopPositionAtomic = new AtomicIntegerArray(voit2stopPosition);

    //</editor-fold>

    /*
     * Concurrency technique: EDT handoff / SwingUtilities.invokeLater
     * Swing components should be updated on the EDT.
     * Car threads only calculate positions; the actual UI update is sent to the EDT.
     */
    private synchronized void setCarBounds(JPanel car, int x, int y, int w, int h) {
        if (SwingUtilities.isEventDispatchThread()) {
            car.setBounds(x, y, w, h);
            return;
        }
        SwingUtilities.invokeLater(() -> car.setBounds(x, y, w, h));
    }

    // Pause the current thread in STOP state, and continue after START.
    private void pauseIfStopped() throws InterruptedException {
        if (stopButtonIsActive.get()) {
            restart.acquire();
        }
    }

    // Check whether the target waiting position for Voie1 is free. This should be called while holding verro.
    private boolean voie1TargetIsFree(int lane, int targetPosition) {
        return !voie1WaitingPositions[lane].containsValue(targetPosition)
                && !voie1ReservedPositions[lane].contains(targetPosition);
    }

    // Check whether the target waiting position for Voie2 is free. This should be called while holding verro.
    private boolean voie2TargetIsFree(int lane, int targetPosition) {
        return !voie2WaitingPositions[lane].containsValue(targetPosition)
                && !voie2ReservedPositions[lane].contains(targetPosition);
    }

    /*
     * Waiting and moving-forward logic for Voie1.
     * A car can enter the intersection only when it is at the front stop position,
     * the light is green, and it is not the yellow phase.
     * Cars behind it can only move forward after the position in front becomes free.
     */
    private int waitGreenAndMoveForwardVoie1(JPanel C, int p, int currentStopPos, int vitess)
            throws InterruptedException {

        int lane = p - 1;

        verro.lock();
        try {
            // Register the current car's waiting position so the following cars can check whether the front is free.
            voie1WaitingPositions[lane].put(C, currentStopPos);
            trafficStateChanged.signalAll();
        } finally {
            verro.unlock();
        }

        while (true) {
            int targetStopPos = currentStopPos;
            boolean canGo = false;
            boolean shouldMoveForward = false;

            verro.lock();
            try {
                // Only the front car can enter the intersection when the light is green and not yellow.
                if (currentStopPos == VOIE1_FRONT_STOP && feuVert1 && !yellowPhase) {
                    nmbrVoitureIntersectionV1++;
                    canGo = true;
                } else {
                    int nextStopPos = Math.min(currentStopPos + CAR_SPACING, VOIE1_FRONT_STOP);

                    // If the position in front is free, reserve it first and then move forward.
                    if (nextStopPos > currentStopPos && voie1TargetIsFree(lane, nextStopPos)) {
                        targetStopPos = nextStopPos;
                        voie1ReservedPositions[lane].add(targetStopPos);
                        shouldMoveForward = true;
                    } else {
                        /*
                         * If the car cannot pass or move forward, wait for the traffic state to change.
                         * Timed await is used to avoid waiting forever if a signal is missed.
                         */
                        trafficStateChanged.await(50, TimeUnit.MILLISECONDS);
                    }
                }
            } finally {
                verro.unlock();
            }

            if (canGo) {
                return currentStopPos;
            }

            if (shouldMoveForward) {
                for (int j = currentStopPos; j < targetStopPos; j++) {
                    pauseIfStopped();
                    setCarBounds(C, voie1PositionPossible[p - 1], j, 30, 60);
                    Thread.sleep(vitess);
                }

                setCarBounds(C, voie1PositionPossible[p - 1], targetStopPos, 30, 60);

                verro.lock();
                try {
                    // After moving forward, update the waiting position and release the reserved position.
                    voie1WaitingPositions[lane].put(C, targetStopPos);
                    voie1ReservedPositions[lane].remove(targetStopPos);
                    currentStopPos = targetStopPos;
                    trafficStateChanged.signalAll();
                } finally {
                    verro.unlock();
                }
            }
        }
    }

    /*
     * Waiting and moving-forward logic for Voie2.
     * The logic is the same as Voie1, but cars move along the x-axis.
     */
    private int waitGreenAndMoveForwardVoie2(JPanel C, int p, int currentStopPos, int vitess)
            throws InterruptedException {

        int lane = p - 1;

        verro.lock();
        try {
            voie2WaitingPositions[lane].put(C, currentStopPos);
            trafficStateChanged.signalAll();
        } finally {
            verro.unlock();
        }

        while (true) {
            int targetStopPos = currentStopPos;
            boolean canGo = false;
            boolean shouldMoveForward = false;

            verro.lock();
            try {
                // Only the front horizontal car can enter the intersection when the light is green and not yellow.
                if (currentStopPos == VOIE2_FRONT_STOP && feuVert2 && !yellowPhase) {
                    nmbrVoitureIntersectionV2++;
                    canGo = true;
                } else {
                    int nextStopPos = Math.min(currentStopPos + CAR_SPACING, VOIE2_FRONT_STOP);

                    if (nextStopPos > currentStopPos && voie2TargetIsFree(lane, nextStopPos)) {
                        targetStopPos = nextStopPos;
                        voie2ReservedPositions[lane].add(targetStopPos);
                        shouldMoveForward = true;
                    } else {
                        // Wait for the traffic state to change, then check again whether the car can move.
                        trafficStateChanged.await(50, TimeUnit.MILLISECONDS);
                    }
                }
            } finally {
                verro.unlock();
            }

            if (canGo) {
                return currentStopPos;
            }

            if (shouldMoveForward) {
                for (int j = currentStopPos; j < targetStopPos; j++) {
                    pauseIfStopped();
                    setCarBounds(C, j, voie2PositionPossible[p - 1], 60, 30);
                    Thread.sleep(vitess);
                }

                setCarBounds(C, targetStopPos, voie2PositionPossible[p - 1], 60, 30);

                verro.lock();
                try {
                    // After moving forward, update the waiting position and notify other cars to check again.
                    voie2WaitingPositions[lane].put(C, targetStopPos);
                    voie2ReservedPositions[lane].remove(targetStopPos);
                    currentStopPos = targetStopPos;
                    trafficStateChanged.signalAll();
                } finally {
                    verro.unlock();
                }
            }
        }
    }

    /*
     * Release the original waiting position only after the car has moved one car spacing.
     * This allows the following car to move forward without overlapping the front car.
     */
    private void releaseVoie1WaitingPosition(JPanel C, int p) {
        int lane = p - 1;

        verro.lock();
        try {
            voie1WaitingPositions[lane].remove(C);
            voie1ReservedPositions[lane].remove(VOIE1_FRONT_STOP);
            trafficStateChanged.signalAll();
        } finally {
            verro.unlock();
        }
    }

    // Voie2 version of releasing the waiting position.
    private void releaseVoie2WaitingPosition(JPanel C, int p) {
        int lane = p - 1;

        verro.lock();
        try {
            voie2WaitingPositions[lane].remove(C);
            voie2ReservedPositions[lane].remove(VOIE2_FRONT_STOP);
            trafficStateChanged.signalAll();
        } finally {
            verro.unlock();
        }
    }

    public void Intersection() {
        /*  //this was when we used locks
        verro2.lock();
        try{
            if(stopButtonIsActive.get()){
                restart.await();
            }
        }catch(InterruptedException ex){
                    System.out.println(ex.getMessage());
        }finally{
            verro2.unlock();
        }
        */

        try {
            pauseIfStopped();
        } catch (InterruptedException ex) {
            System.out.println(ex.getMessage());
        }

        verro.lock();
        try {
            // Lock the light-switching process so car threads cannot read a half-updated traffic state.
            if (mytimer.isRunning()) {
                mytimer.stop();

                // Entering the yellow phase stops waiting cars from gaining new permission to pass.
                yellowPhase = true;
                trafficStateChanged.signalAll();

                feuVoie1Orange.setEnabled(true);
                feuVoie2Orange.setEnabled(true);
                feuVoie1Green.setEnabled(false);
                feuVoie2Red.setEnabled(false);
                feuVoie1Red.setEnabled(false);
                feuVoie2Green.setEnabled(false);
            }

            if (feuVert1) {
                feuVert1 = false;

                // Wait until all Voie1 cars that have entered the intersection have left.
                while (nmbrVoitureIntersectionV1 != 0) {
                    voie1_Cars_In_Intersection.await();
                }

                // After the current direction is clear, end the yellow phase and switch Voie2 to green.
                yellowPhase = false;
                trafficStateChanged.signalAll();

                feuVert2 = true;
                feuVoie1Orange.setEnabled(false);
                feuVoie2Orange.setEnabled(false);
                feuVoie1Green.setEnabled(false);
                feuVoie2Red.setEnabled(false);
                feuVoie1Red.setEnabled(true);
                feuVoie2Green.setEnabled(true);

                // Wake up cars waiting for the Voie2 green light.
                feuVertVoie2.signalAll();
                trafficStateChanged.signalAll();

            } else {
                feuVert2 = false;

                // Wait until all Voie2 cars that have entered the intersection have left.
                while (nmbrVoitureIntersectionV2 != 0) {
                    voie2_Cars_In_Intersection.await();
                }

                // After the current direction is clear, end the yellow phase and switch Voie1 to green.
                yellowPhase = false;
                trafficStateChanged.signalAll();

                feuVert1 = true;
                feuVoie1Orange.setEnabled(false);
                feuVoie2Orange.setEnabled(false);
                feuVoie1Green.setEnabled(true);
                feuVoie2Red.setEnabled(true);
                feuVoie1Red.setEnabled(false);
                feuVoie2Green.setEnabled(false);

                // Wake up cars waiting for the Voie1 green light.
                feuVertVoie1.signalAll();
                trafficStateChanged.signalAll();
            }

            // If the main UI is changing the light duration, the traffic light controller thread waits until it is done.
            if (mainStopedTheTimer.get()) {//if the main change light duration
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

    public void traversee1(JPanel C, int p, int vitess) {
        try {
            // Phase 1: Move towards the stop line.
            /*
             * Assign a fixed waiting position to this car first.
             * The get and set operations are protected by the same lock,
             * so two cars cannot get the same waiting position.
             */
            int myStopPos;
            verro.lock();
            try {
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

            setCarBounds(C, voie1PositionPossible[p - 1], myStopPos, 30, 60);

            /*
             * Phase 2: Wait for green light / move forward.
             * Only the front car can enter the intersection.
             */
            myStopPos = waitGreenAndMoveForwardVoie1(C, p, myStopPos, vitess);

            /*
             * Phase 3: Cross the intersection.
             * The original waiting position is released only after the car moves one car spacing.
             */
            boolean waitingPositionReleased = false;

            for (int j = myStopPos; j < 555; j++) {
                pauseIfStopped();
                setCarBounds(C, voie1PositionPossible[p - 1], j, 30, 60);

                if (!waitingPositionReleased && j >= myStopPos + CAR_SPACING) {
                    releaseVoie1WaitingPosition(C, p);
                    waitingPositionReleased = true;
                }

                Thread.sleep(vitess);
            }

            if (!waitingPositionReleased) {
                releaseVoie1WaitingPosition(C, p);
            }

            // After the car crosses the intersection, return one waiting position to this lane.
            verro.lock();
            try {
                voit1stopPositionAtomic.set(p - 1,
                        voit1stopPositionAtomic.get(p - 1) + CAR_SPACING);
                carSpacingChanged.signalAll();
                trafficStateChanged.signalAll();
            } finally {
                verro.unlock();
            }

            // Update the number of Voie1 cars in the intersection and notify the light thread when it is clear.
            verro.lock();
            try {
                nmbrVoitureIntersectionV1--;
                if (nmbrVoitureIntersectionV1 == 0 && !feuVert1) {
                    voie1_Cars_In_Intersection.signal();
                }
            } finally {
                verro.unlock();
            }

            // Phase 4: Leave
            for (int j = 555; j < 830; j++) {
                pauseIfStopped();
                setCarBounds(C, voie1PositionPossible[p - 1], j, 30, 60);
                Thread.sleep(vitess);
            }

        } catch (InterruptedException ex) {
            Logger.getLogger(carrefourManager.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void traversee2(JPanel C, int p, int vitess) {
        try {
            // Phase 1: Move towards the stop line.
            /*
             * Voie2 cars also get a fixed waiting position first.
             * The get + set operation is also protected by the lock.
             */
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

            /*
             * Phase 2: Wait for green light / move forward.
             * Horizontal cars also need to be at the front before entering the intersection.
             */
            myStopPos = waitGreenAndMoveForwardVoie2(C, p, myStopPos, vitess);

            /*
             * Phase 3: Cross the intersection.
             * The waiting position is released after one car spacing to avoid overlap.
             */
            boolean waitingPositionReleased = false;

            for (int j = myStopPos; j < 640; j++) {
                pauseIfStopped();
                setCarBounds(C, j, voie2PositionPossible[p - 1], 60, 30);

                if (!waitingPositionReleased && j >= myStopPos + CAR_SPACING) {
                    releaseVoie2WaitingPosition(C, p);
                    waitingPositionReleased = true;
                }

                Thread.sleep(vitess);
            }

            if (!waitingPositionReleased) {
                releaseVoie2WaitingPosition(C, p);
            }

            // After the car crosses the intersection, return one waiting position to this lane.
            verro.lock();
            try {
                voit2stopPositionAtomic.set(p - 1,
                        voit2stopPositionAtomic.get(p - 1) + CAR_SPACING);
                carSpacingChanged.signalAll();
                trafficStateChanged.signalAll();
            } finally {
                verro.unlock();
            }

            // Update the number of Voie2 cars in the intersection and notify the light thread when it is clear.
            verro.lock();
            try {
                nmbrVoitureIntersectionV2--;
                if (nmbrVoitureIntersectionV2 == 0 && !feuVert2) {
                    voie2_Cars_In_Intersection.signal();
                }
            } finally {
                verro.unlock();
            }

            // Phase 4: Leave
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