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
     * 并发技术：ReentrantLock
     * 用同一把锁保护红绿灯状态、等待队列、停车位和路口车辆数量。
     * 这些数据会被多个车辆线程和红绿灯线程同时访问。
     */
    static Lock verro = new ReentrantLock();

    /*
     * 并发技术：Condition
     * 车辆线程在不能通行时等待，红绿灯线程在路口未清空时等待。
     * 这样可以避免线程一直循环检查条件。
     */
    Condition feuVertVoie1 = verro.newCondition();
    Condition feuVertVoie2 = verro.newCondition();
    Condition voie2_Cars_In_Intersection = verro.newCondition();
    Condition voie1_Cars_In_Intersection = verro.newCondition();

    // 用于通知车距或停车位状态发生变化。
    Condition carSpacingChanged = verro.newCondition();

    /*
     * 用于通知整体交通状态发生变化。
     * 例如切灯、黄灯结束、前车离开等待位置后，等待车辆会重新判断能否前进。
     */
    Condition trafficStateChanged = verro.newCondition();

    /*
     * 并发技术：volatile
     * 红绿灯状态会被红绿灯线程修改，也会被车辆线程读取。
     * volatile 可以让状态变化更及时地被其他线程看到。
     */
    volatile boolean feuVert1 = true;
    volatile boolean feuVert2 = false;

    /*
     * 黄灯阶段标志。
     * 黄灯时，不允许还在等待区的车辆获得新的通行资格；
     * 但已经进入路口的车辆可以继续通过。
     */
    volatile boolean yellowPhase = false;

    /*
     * 分开记录两个方向已经进入路口的车辆数量。
     * 这样切灯时只等待当前方向的车辆清空，不会被另一方向影响。
     */
    int nmbrVoitureIntersectionV1 = 0;
    int nmbrVoitureIntersectionV2 = 0;

    /*
     * 最前停车位。
     * 只有排到最前面的车，才可以在绿灯且非黄灯时进入路口。
     */
    static final int VOIE1_FRONT_STOP = 225;
    static final int VOIE2_FRONT_STOP = 310;

    /*
     * 记录每条车道中，正在等待的车辆和它们的位置。
     * HashMap 本身不是线程安全的，所以相关操作都放在 verro 锁内完成。
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
     * 记录已经被预定的补位位置。
     * 车辆移动到目标位置前，先预定该位置，避免另一辆车同时补到同一处。
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
     * 当主界面修改红绿灯时间时，红绿灯线程可以在这里等待，
     * 直到新的时间设置完成。
     */
    static Condition mainRestartTimer = verro.newCondition();

    /*
     * 并发技术：AtomicBoolean
     * 这个标志会被界面线程和红绿灯线程共同使用。
     */
    static AtomicBoolean mainStopedTheTimer = new AtomicBoolean(false);

    /*
     * 并发技术：Semaphore
     * 用作 START / STOP 的暂停门。
     * STOP 时线程在 pauseIfStopped() 中等待，START 后再继续执行。
     */
    static Semaphore restart = new Semaphore(0, true);

    int[] voie1PositionPossible = {420, 470, 530, 580};
    int[] voie2PositionPossible = {327, 369, 457, 500};

    static final int CAR_SPACING = 80;

    int[] voit1stopPosition = {225, 225, 225, 225};

    /*
     * 并发技术：AtomicIntegerArray
     * 用来保存每条车道下一个可用的等待位置。
     * 单次读写是原子的；涉及 get 后再 set 的组合操作时，仍然用 verro 锁保护。
     */
    AtomicIntegerArray voit1stopPositionAtomic = new AtomicIntegerArray(voit1stopPosition);

    int[] voit2stopPosition = {310, 310, 310, 310};

    // Voie2 的等待位置数组，作用和 Voie1 相同。
    AtomicIntegerArray voit2stopPositionAtomic = new AtomicIntegerArray(voit2stopPosition);

    //</editor-fold>

    /*
     * 并发技术：SwingUtilities.invokeLater
     * Swing 组件应由 EDT 更新。
     * 车辆线程只计算位置，真正的 UI 更新交给 EDT 执行。
     */
    private synchronized void setCarBounds(JPanel car, int x, int y, int w, int h) {
        if (SwingUtilities.isEventDispatchThread()) {
            car.setBounds(x, y, w, h);
            return;
        }
        SwingUtilities.invokeLater(() -> car.setBounds(x, y, w, h));
    }

    // STOP 状态下暂停当前线程，START 后继续。
    private void pauseIfStopped() throws InterruptedException {
        if (stopButtonIsActive.get()) {
            restart.acquire();
        }
    }

    // 判断 Voie1 的目标等待位是否空闲。调用时应在 verro 锁内。
    private boolean voie1TargetIsFree(int lane, int targetPosition) {
        return !voie1WaitingPositions[lane].containsValue(targetPosition)
                && !voie1ReservedPositions[lane].contains(targetPosition);
    }

    // 判断 Voie2 的目标等待位是否空闲。调用时应在 verro 锁内。
    private boolean voie2TargetIsFree(int lane, int targetPosition) {
        return !voie2WaitingPositions[lane].containsValue(targetPosition)
                && !voie2ReservedPositions[lane].contains(targetPosition);
    }

    /*
     * Voie1 的等待和补位逻辑。
     * 车辆只有排到最前停车位，并且当前是绿灯、非黄灯时，才可以进入路口。
     * 后面的车只能在前方位置空出后向前补位。
     */
    private int waitGreenAndMoveForwardVoie1(JPanel C, int p, int currentStopPos, int vitess)
            throws InterruptedException {

        int lane = p - 1;

        verro.lock();
        try {
            // 先登记当前车辆的等待位置，方便后车判断前方是否空出。
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
                // 只有最前面的车可以在绿灯且非黄灯时进入路口。
                if (currentStopPos == VOIE1_FRONT_STOP && feuVert1 && !yellowPhase) {
                    nmbrVoitureIntersectionV1++;
                    canGo = true;
                } else {
                    int nextStopPos = Math.min(currentStopPos + CAR_SPACING, VOIE1_FRONT_STOP);

                    // 如果前方等待位空出，当前车先预定目标位置，再向前补位。
                    if (nextStopPos > currentStopPos && voie1TargetIsFree(lane, nextStopPos)) {
                        targetStopPos = nextStopPos;
                        voie1ReservedPositions[lane].add(targetStopPos);
                        shouldMoveForward = true;
                    } else {
                        /*
                         * 当前不能通行也不能补位时，等待交通状态变化。
                         * 使用 timed await 是为了防止错过 signal 后永久等待。
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
                    // 补位完成后更新等待位置，并释放刚才预定的位置。
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
     * Voie2 的等待和补位逻辑。
     * 逻辑与 Voie1 相同，只是车辆沿 x 轴移动。
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
                // 只有最前面的横向车辆可以在绿灯且非黄灯时进入路口。
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
                        // 等待交通状态变化，然后重新判断能否前进。
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
                    // 补位完成后更新等待位置，并通知其他车辆重新判断。
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
     * 车辆开出一个车距后，才释放原来的等待位置。
     * 这样后车可以补位，但不会和前车重叠。
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

    // Voie2 版本的等待位置释放。
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
            // 切换红绿灯状态时加锁，避免车辆线程读到切换到一半的状态。
            if (mytimer.isRunning()) {
                mytimer.stop();

                // 进入黄灯阶段后，不再允许等待区车辆获得新的通行资格。
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

                // 等待 Voie1 已经进入路口的车辆全部离开。
                while (nmbrVoitureIntersectionV1 != 0) {
                    voie1_Cars_In_Intersection.await();
                }

                // 当前方向清空后，结束黄灯并切到 Voie2 绿灯。
                yellowPhase = false;
                trafficStateChanged.signalAll();

                feuVert2 = true;
                feuVoie1Orange.setEnabled(false);
                feuVoie2Orange.setEnabled(false);
                feuVoie1Green.setEnabled(false);
                feuVoie2Red.setEnabled(false);
                feuVoie1Red.setEnabled(true);
                feuVoie2Green.setEnabled(true);

                // 唤醒等待 Voie2 绿灯的车辆。
                feuVertVoie2.signalAll();
                trafficStateChanged.signalAll();

            } else {
                feuVert2 = false;

                // 等待 Voie2 已经进入路口的车辆全部离开。
                while (nmbrVoitureIntersectionV2 != 0) {
                    voie2_Cars_In_Intersection.await();
                }

                // 当前方向清空后，结束黄灯并切到 Voie1 绿灯。
                yellowPhase = false;
                trafficStateChanged.signalAll();

                feuVert1 = true;
                feuVoie1Orange.setEnabled(false);
                feuVoie2Orange.setEnabled(false);
                feuVoie1Green.setEnabled(true);
                feuVoie2Red.setEnabled(true);
                feuVoie1Red.setEnabled(false);
                feuVoie2Green.setEnabled(false);

                // 唤醒等待 Voie1 绿灯的车辆。
                feuVertVoie1.signalAll();
                trafficStateChanged.signalAll();
            }

            // 如果主界面正在修改红绿灯时间，红绿灯线程先等待修改完成。
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
            // Phase 1: 接近停止线。
            /*
             * 先给本车分配一个固定等待位。
             * get 和 set 放在同一个锁内，避免两辆车拿到同一个等待位置。
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
             * Phase 2: 等待绿灯 / 向前补位。
             * 只有排到最前面的车才可能进入路口。
             */
            myStopPos = waitGreenAndMoveForwardVoie1(C, p, myStopPos, vitess);

            /*
             * Phase 3: 穿过路口。
             * 车辆开出一个车距后，再释放原等待位给后车补位。
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

            // 车辆通过路口后，归还一个等待位给该车道。
            verro.lock();
            try {
                voit1stopPositionAtomic.set(p - 1,
                        voit1stopPositionAtomic.get(p - 1) + CAR_SPACING);
                carSpacingChanged.signalAll();
                trafficStateChanged.signalAll();
            } finally {
                verro.unlock();
            }

            // 更新 Voie1 路口车辆数量，并在清空时通知红绿灯线程。
            verro.lock();
            try {
                nmbrVoitureIntersectionV1--;
                if (nmbrVoitureIntersectionV1 == 0 && !feuVert1) {
                    voie1_Cars_In_Intersection.signal();
                }
            } finally {
                verro.unlock();
            }

            // Phase 4: 离开
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
            // Phase 1: 接近停止线。
            /*
             * Voie2 车辆也先分配固定等待位。
             * 这里同样用锁保护 get + set 这一组操作。
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
             * Phase 2: 等待绿灯 / 向前补位。
             * 横向车辆也必须排到最前面才可以进入路口。
             */
            myStopPos = waitGreenAndMoveForwardVoie2(C, p, myStopPos, vitess);

            /*
             * Phase 3: 穿过路口。
             * 开出一个车距后释放等待位置，避免后车和前车重叠。
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

            // 车辆通过路口后，归还一个等待位给该车道。
            verro.lock();
            try {
                voit2stopPositionAtomic.set(p - 1,
                        voit2stopPositionAtomic.get(p - 1) + CAR_SPACING);
                carSpacingChanged.signalAll();
                trafficStateChanged.signalAll();
            } finally {
                verro.unlock();
            }

            // 更新 Voie2 路口车辆数量，并在清空时通知红绿灯线程。
            verro.lock();
            try {
                nmbrVoitureIntersectionV2--;
                if (nmbrVoitureIntersectionV2 == 0 && !feuVert2) {
                    voie2_Cars_In_Intersection.signal();
                }
            } finally {
                verro.unlock();
            }

            // Phase 4: 离开
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