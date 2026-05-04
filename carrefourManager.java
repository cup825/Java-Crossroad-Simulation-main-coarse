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

    // [并发技术 - ReentrantLock] 互斥锁保护共享状态
    static Lock verro = new ReentrantLock();
    // [并发技术 - Condition Variables] 条件变量实现等待/唤醒机制
    Condition feuVertVoie1 = verro.newCondition();
    Condition feuVertVoie2 = verro.newCondition();
    Condition voie2_Cars_In_Intersection = verro.newCondition();
    Condition voie1_Cars_In_Intersection = verro.newCondition();

    Condition carSpacingChanged = verro.newCondition();

    // [修复 Bug + 并发技术 - Volatile] 确保对红绿灯的改变对所有线程立即可见，防止红灯违规
    volatile boolean feuVert1 = true;
    volatile boolean feuVert2 = false;

    // [修复 Bug] 分离计数器：Voie1/Voie2 各自独立，防止灯错误切换导致碰撞
    int nmbrVoitureIntersectionV1 = 0;
    int nmbrVoitureIntersectionV2 = 0;

    static Condition mainRestartTimer = verro.newCondition();
    static AtomicBoolean mainStopedTheTimer = new AtomicBoolean(false);

    // [并发技术 - Semaphore] 门控机制：START释放许可，STOP回收许可
    static Semaphore restart = new Semaphore(0, true);

    int[] voie1PositionPossible = {420, 470, 530, 580};
    int[] voie2PositionPossible = {327, 369, 457, 500};

    static final int CAR_SPACING = 80;

    int[] voit1stopPosition = {225, 225, 225, 225};
    // [并发技术 - AtomicIntegerArray] 原子数组，配合 Lock 保证停止位置的原子性
    AtomicIntegerArray voit1stopPositionAtomic = new AtomicIntegerArray(voit1stopPosition);

    int[] voit2stopPosition = {310, 310, 310, 310};
    AtomicIntegerArray voit2stopPositionAtomic = new AtomicIntegerArray(voit2stopPosition);

    //</editor-fold>

    // [并发技术 - Synchronized] 同步方法：保证 Swing UI 线程安全
    private synchronized void setCarBounds(JPanel car, int x, int y, int w, int h) {
        if (SwingUtilities.isEventDispatchThread()) {
            car.setBounds(x, y, w, h);
            return;
        }
        SwingUtilities.invokeLater(() -> car.setBounds(x, y, w, h));
    }

    // 暂停检查：如果 STOP 被按下，线程阻塞直到 START 释放许可
    private void pauseIfStopped() throws InterruptedException {
        if (stopButtonIsActive.get()) {
            restart.acquire();
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
                feuVoie1Orange.setEnabled(true);
                feuVoie2Orange.setEnabled(true);
                feuVoie1Green.setEnabled(false);
                feuVoie2Red.setEnabled(false);
                feuVoie1Red.setEnabled(false);
                feuVoie2Green.setEnabled(false);
            }

            if (feuVert1) {
                feuVert1 = false;

                // [修复 Bug] while 循环避免虚假唤醒；等待 Voie1 计数器变为0
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
                feuVertVoie2.signalAll();

            } else {
                feuVert2 = false;

                // [修复 Bug] while 循环避免虚假唤醒；等待 Voie2 计数器变为0
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
                feuVertVoie1.signalAll();
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
            // Phase 1: 接近停止线
            // [修复 Bug - 加锁保护] 防止多车竞态导致碰撞
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

            // Phase 2: 等待绿灯
            // [修复 Bug] 计数器增加在锁内，防止红灯违规
            verro.lock();
            try {
                while (!feuVert1) {
                    feuVertVoie1.await();
                }
                nmbrVoitureIntersectionV1++;
            } finally {
                verro.unlock();
            }

            // Phase 3: 穿过路口
            boolean spacingRestored = false;

            for (int j = myStopPos; j < 555; j++) {
                pauseIfStopped();
                setCarBounds(C, voie1PositionPossible[p - 1], j, 30, 60);

                if (!spacingRestored && j >= myStopPos + CAR_SPACING) {
                    verro.lock();
                    try {
                        voit1stopPositionAtomic.set(p - 1,
                                voit1stopPositionAtomic.get(p - 1) + CAR_SPACING);
                        carSpacingChanged.signalAll();
                    } finally {
                        verro.unlock();
                    }
                    spacingRestored = true;
                }

                Thread.sleep(vitess);
            }

            if (!spacingRestored) {
                verro.lock();
                try {
                    voit1stopPositionAtomic.set(p - 1,
                            voit1stopPositionAtomic.get(p - 1) + CAR_SPACING);
                    carSpacingChanged.signalAll();
                } finally {
                    verro.unlock();
                }
            }

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
            // Phase 1: 接近停止线
            // [修复 Bug - 加锁保护] 防止多车竞态导致碰撞（Voie2 版本）
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

            // Phase 2: 等待绿灯
            // [修复 Bug] 计数器增加在锁内，防止红灯违规
            verro.lock();
            try {
                while (!feuVert2) {
                    feuVertVoie2.await();
                }
                nmbrVoitureIntersectionV2++;
            } finally {
                verro.unlock();
            }

            // Phase 3: 穿过路口
            boolean spacingRestored = false;

            for (int j = myStopPos; j < 640; j++) {
                pauseIfStopped();
                setCarBounds(C, j, voie2PositionPossible[p - 1], 60, 30);

                if (!spacingRestored && j >= myStopPos + CAR_SPACING) {
                    verro.lock();
                    try {
                        voit2stopPositionAtomic.set(p - 1,
                                voit2stopPositionAtomic.get(p - 1) + CAR_SPACING);
                        carSpacingChanged.signalAll();
                    } finally {
                        verro.unlock();
                    }
                    spacingRestored = true;
                }

                Thread.sleep(vitess);
            }

            if (!spacingRestored) {
                verro.lock();
                try {
                    voit2stopPositionAtomic.set(p - 1,
                            voit2stopPositionAtomic.get(p - 1) + CAR_SPACING);
                    carSpacingChanged.signalAll();
                } finally {
                    verro.unlock();
                }
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