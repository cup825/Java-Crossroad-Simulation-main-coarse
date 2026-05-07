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

    static Lock verro = new ReentrantLock();

    Condition feuVertVoie1 = verro.newCondition();
    Condition feuVertVoie2 = verro.newCondition();
    Condition voie2_Cars_In_Intersection = verro.newCondition();
    Condition voie1_Cars_In_Intersection = verro.newCondition();

    Condition carSpacingChanged = verro.newCondition(); // Condition by Ziyue Ren: notifies spacing or stop-position changes.

    /*
     * Notifies waiting cars when the overall traffic state changes, such as
     * a light switch, the end of a yellow phase, or a front car leaving its waiting position.
     */
    Condition trafficStateChanged = verro.newCondition(); // Condition by Ziyue Ren: notifies general traffic-state changes.

    /*
     * The light states are read by car threads and written by the traffic light controller thread.
     * volatile makes the updated state visible to other threads.
     */
    volatile boolean feuVert1 = true; // volatile by Ziyue Ren: shares Voie1 green-light state between threads.
    volatile boolean feuVert2 = false; // volatile by Ziyue Ren: shares Voie2 green-light state between threads.

    /*
     * During the yellow phase, cars still waiting are not allowed to get new permission
     * to enter the intersection, while cars already inside can continue passing.
     */
    volatile boolean yellowPhase = false; // volatile by Ziyue Ren: shares yellow-phase state between threads.

    /*
     * Count cars inside the intersection separately for the two directions.
     * When switching lights, only the current direction needs to be cleared.
     */
    int nmbrVoitureIntersectionV1 = 0;
    int nmbrVoitureIntersectionV2 = 0;

    /*
     * Front stop positions.
     * Only the car at the front stop position can enter the intersection
     * when the light is green, and it is not the yellow phase.
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

    static Condition mainRestartTimer = verro.newCondition(); //to test if light duration changes than wait until to be applied
    static AtomicBoolean mainStopedTheTimer = new AtomicBoolean(false);  //this is used when you change the light duration

    /* //this was when we used locks
    static Lock verro2 = new ReentrantLock();
    static Condition restart = verro2.newCondition();
    */
    static Semaphore restart = new Semaphore(0, true);

    int[] voie1PositionPossible = {420, 470, 530, 580};
    int[] voie2PositionPossible = {327, 369, 457, 500};

    static final int CAR_SPACING = 80;

    int[] voit1stopPosition = {225, 225, 225, 225};
    AtomicIntegerArray voit1stopPositionAtomic = new AtomicIntegerArray(voit1stopPosition);

    int[] voit2stopPosition = {310, 310, 310, 310};
    AtomicIntegerArray voit2stopPositionAtomic = new AtomicIntegerArray(voit2stopPosition);

    //</editor-fold>

    /*
     * Swing components should be updated on the Event Dispatch Thread.
     * Car threads calculate positions, and this helper sends the actual UI update to the EDT.
     */
    private synchronized void setCarBounds(JPanel car, int x, int y, int w, int h) { // synchronized by Ziyue Ren: serialises calls to this UI update helper.
        if (SwingUtilities.isEventDispatchThread()) {
            car.setBounds(x, y, w, h);
            return;
        }
        SwingUtilities.invokeLater(() -> car.setBounds(x, y, w, h)); // SwingUtilities.invokeLater by Ziyue Ren: updates Swing UI on the EDT.
    }

    private void pauseIfStopped() throws InterruptedException {
        if (stopButtonIsActive.get()) {
            restart.acquire();
        }
    }

    private boolean voie1TargetIsFree(int lane, int targetPosition) { // Checks whether the target waiting position for Voie1 is free while holding verro.
        return !voie1WaitingPositions[lane].containsValue(targetPosition)
                && !voie1ReservedPositions[lane].contains(targetPosition);
    }

    private boolean voie2TargetIsFree(int lane, int targetPosition) { // Checks whether the target waiting position for Voie2 is free while holding verro.
        return !voie2WaitingPositions[lane].containsValue(targetPosition)
                && !voie2ReservedPositions[lane].contains(targetPosition);
    }

    /*
     * ReentrantLock + Condition by Ziyue Ren:
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
            voie1WaitingPositions[lane].put(C, currentStopPos); // Registers this car's waiting position so following cars can check whether the front is free.
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
                if (currentStopPos == VOIE1_FRONT_STOP && feuVert1 && !yellowPhase) { // Only the front car can enter when the light is green and not yellow.
                    nmbrVoitureIntersectionV1++;
                    canGo = true;
                } else {
                    int nextStopPos = Math.min(currentStopPos + CAR_SPACING, VOIE1_FRONT_STOP);

                    if (nextStopPos > currentStopPos && voie1TargetIsFree(lane, nextStopPos)) { // Reserves the free position in front before moving forward.
                        targetStopPos = nextStopPos;
                        voie1ReservedPositions[lane].add(targetStopPos);
                        shouldMoveForward = true;
                    } else {
                        /*
                         * If the car cannot pass or move forward, wait for the traffic state to change.
                         * Timed await avoids waiting forever if a signal is missed.
                         */
                        trafficStateChanged.await(50, TimeUnit.MILLISECONDS); // Condition.await by Ziyue Ren: waits without busy-waiting.
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
                    voie1WaitingPositions[lane].put(C, targetStopPos); // Updates the waiting position after moving forward.
                    voie1ReservedPositions[lane].remove(targetStopPos); // Releases the reserved position after the move is complete.
                    currentStopPos = targetStopPos;
                    trafficStateChanged.signalAll();
                } finally {
                    verro.unlock();
                }
            }
        }
    }

    /*
     * ReentrantLock + Condition by Ziyue Ren:
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
                if (currentStopPos == VOIE2_FRONT_STOP && feuVert2 && !yellowPhase) { // Only the front horizontal car can enter when the light is green and not yellow.
                    nmbrVoitureIntersectionV2++;
                    canGo = true;
                } else {
                    int nextStopPos = Math.min(currentStopPos + CAR_SPACING, VOIE2_FRONT_STOP);

                    if (nextStopPos > currentStopPos && voie2TargetIsFree(lane, nextStopPos)) {
                        targetStopPos = nextStopPos;
                        voie2ReservedPositions[lane].add(targetStopPos);
                        shouldMoveForward = true;
                    } else {
                        trafficStateChanged.await(50, TimeUnit.MILLISECONDS); // Condition.await by Ziyue Ren: waits without busy-waiting and checks again later.
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
                    voie2WaitingPositions[lane].put(C, targetStopPos); // Updates the waiting position after moving forward.
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
     * ReentrantLock + Condition by Ziyue Ren:
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

    private void releaseVoie2WaitingPosition(JPanel C, int p) { // Voie2 version of releasing the waiting position.
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
            if (mytimer.isRunning()) {
                mytimer.stop();

                yellowPhase = true; // volatile by Ziyue Ren: prevents waiting cars from getting new permission during the yellow phase.
                trafficStateChanged.signalAll(); // Condition.signalAll by Ziyue Ren: wakes cars to re-check the changed yellow-phase state.

                feuVoie1Orange.setEnabled(true);
                feuVoie2Orange.setEnabled(true);
                feuVoie1Green.setEnabled(false);
                feuVoie2Red.setEnabled(false);
                feuVoie1Red.setEnabled(false);
                feuVoie2Green.setEnabled(false);
            }

            if (feuVert1) {
                feuVert1 = false;

                while (nmbrVoitureIntersectionV1 != 0) {
                    voie1_Cars_In_Intersection.await(); // Condition.await by Ziyue Ren: waits until all Voie1 cars already inside have left.
                }

                yellowPhase = false; // Ends the yellow phase after the current direction is clear.
                trafficStateChanged.signalAll();

                feuVert2 = true;
                feuVoie1Orange.setEnabled(false);
                feuVoie2Orange.setEnabled(false);
                feuVoie1Green.setEnabled(false);
                feuVoie2Red.setEnabled(false);
                feuVoie1Red.setEnabled(true);
                feuVoie2Green.setEnabled(true);

                feuVertVoie2.signalAll(); // Condition.signalAll by Ziyue Ren: wakes up cars waiting for the Voie2 green light.
                trafficStateChanged.signalAll();

            } else {
                feuVert2 = false;

                while (nmbrVoitureIntersectionV2 != 0) {
                    voie2_Cars_In_Intersection.await(); // Condition.await by Ziyue Ren: waits until all Voie2 cars already inside have left.
                }

                yellowPhase = false; // Ends the yellow phase after the current direction is clear.
                trafficStateChanged.signalAll();

                feuVert1 = true;
                feuVoie1Orange.setEnabled(false);
                feuVoie2Orange.setEnabled(false);
                feuVoie1Green.setEnabled(true);
                feuVoie2Red.setEnabled(true);
                feuVoie1Red.setEnabled(false);
                feuVoie2Green.setEnabled(false);

                feuVertVoie1.signalAll(); // Condition.signalAll by Ziyue Ren: wakes up cars waiting for the Voie1 green light.
                trafficStateChanged.signalAll();
            }

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
            /*
             * ReentrantLock by Ziyue Ren:
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

            for (int j = -60; j < myStopPos; j++) { // Phase 1: move towards the stop line.
                pauseIfStopped();
                setCarBounds(C, voie1PositionPossible[p - 1], j, 30, 60);
                Thread.sleep(vitess);
            }

            setCarBounds(C, voie1PositionPossible[p - 1], myStopPos, 30, 60);
            myStopPos = waitGreenAndMoveForwardVoie1(C, p, myStopPos, vitess); // Phase 2: wait for green light or move forward.

            boolean waitingPositionReleased = false; // Phase 3: cross the intersection and release the waiting position after one car spacing.

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

            verro.lock();
            try {
                voit1stopPositionAtomic.set(p - 1,
                        voit1stopPositionAtomic.get(p - 1) + CAR_SPACING); // Returns one waiting position to this lane after the car crosses.
                carSpacingChanged.signalAll();
                trafficStateChanged.signalAll();
            } finally {
                verro.unlock();
            }

            verro.lock();
            try {
                nmbrVoitureIntersectionV1--; // ReentrantLock by Ziyue Ren: updates Voie1 intersection counter under the lock.
                if (nmbrVoitureIntersectionV1 == 0 && !feuVert1) {
                    voie1_Cars_In_Intersection.signal(); // Condition.signal by Ziyue Ren: notifies the light controller when Voie1 is clear.
                }
            } finally {
                verro.unlock();
            }

            for (int j = 555; j < 830; j++) { // Phase 4: leave the intersection area.
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
            /*
             * ReentrantLock by Ziyue Ren:
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

            for (int j = -60; j < myStopPos; j++) { // Phase 1: move towards the stop line.
                pauseIfStopped();
                setCarBounds(C, j, voie2PositionPossible[p - 1], 60, 30);
                Thread.sleep(vitess);
            }

            setCarBounds(C, myStopPos, voie2PositionPossible[p - 1], 60, 30);
            myStopPos = waitGreenAndMoveForwardVoie2(C, p, myStopPos, vitess); // Phase 2: wait for green light or move forward.

            boolean waitingPositionReleased = false; // Phase 3: cross the intersection and release the waiting position after one car spacing.

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

            verro.lock();
            try {
                voit2stopPositionAtomic.set(p - 1,
                        voit2stopPositionAtomic.get(p - 1) + CAR_SPACING); // Returns one waiting position to this lane after the car crosses.
                carSpacingChanged.signalAll();
                trafficStateChanged.signalAll();
            } finally {
                verro.unlock();
            }

            verro.lock();
            try {
                nmbrVoitureIntersectionV2--; // ReentrantLock by Ziyue Ren: updates Voie2 intersection counter under the lock.
                if (nmbrVoitureIntersectionV2 == 0 && !feuVert2) {
                    voie2_Cars_In_Intersection.signal(); // Condition.signal by Ziyue Ren: notifies the light controller when Voie2 is clear.
                }
            } finally {
                verro.unlock();
            }

            for (int j = 640; j < 1035; j++) { // Phase 4: leave the intersection area.
                pauseIfStopped();
                setCarBounds(C, j, voie2PositionPossible[p - 1], 60, 30);
                Thread.sleep(vitess);
            }

        } catch (InterruptedException ex) {
            Logger.getLogger(carrefourManager.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

}