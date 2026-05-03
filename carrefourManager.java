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
import javax.swing.Timer;
import static mini.projet_dac.MiniProjet_DAC.*;

public class carrefourManager {
    
    //<editor-fold defaultstate="collapsed" desc="Variables Declaration">
    
    static Timer mytimer = new Timer(1000, new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
            
            if(!stopButtonIsActive.get()){
                    
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
    boolean feuVert1 = true;
    boolean feuVert2 = false;
    int nmbrVoitureIntersection = 0;
    
    static Condition mainRestartTimer = verro.newCondition(); //to test if light duration changes than wait until to be applied
    static AtomicBoolean mainStopedTheTimer = new AtomicBoolean(false);  //this is used when you change the light duration
    
    
    /* //this was when we used locks
    static Lock verro2 = new ReentrantLock();
    static Condition restart = verro2.newCondition();
    */
    static Semaphore restart = new Semaphore(0,true);
    
    int[] voie1PositionPossible = {420, 470, 530, 580};
    int[] voie2PositionPossible = {327, 369, 457, 500};

    int[] voit1stopPosition = {225, 225, 225, 225};
    AtomicIntegerArray voit1stopPositionAtomic = new AtomicIntegerArray(voit1stopPosition);

    int[] voit2stopPosition = {310, 310, 310, 310};
    AtomicIntegerArray voit2stopPositionAtomic = new AtomicIntegerArray(voit2stopPosition);

    //</editor-fold>
    
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
            if(stopButtonIsActive.get()){
                    restart.acquire();
            }
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
                
                if (nmbrVoitureIntersection != 0) {
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
                
                if (nmbrVoitureIntersection != 0) {
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

            for (int j = -60; j < 830; j++) {
                if (voit1stopPositionAtomic.get(p - 1) == -95) {
                    break;
                }
                try {
                    if(stopButtonIsActive.get()){
                            restart.acquire();
                    }
                } catch (InterruptedException ex) {
                       System.out.println(ex.getMessage());
                }
                /*
                verro2.lock();
                try{
                    if(stopButtonIsActive.get()){
                        restart.await();
                    }
                }finally{
                    verro2.unlock();
                }
                */
                if (C.getBounds().y == voit1stopPositionAtomic.get(p - 1)) {

                    //changerStopPositionVoie1(p,true);
                    voit1stopPositionAtomic.set(p - 1, voit1stopPositionAtomic.get(p - 1) - 80);
                    verro.lock();
                    try {
                        while (!feuVert1) {
                            feuVertVoie1.await();
                        }
                    } finally {
                        verro.unlock();
                    }

                    nmbrVoitureIntersection++;
                    circuler("Voie 1", C, p, j, vitess);

                    verro.lock();
                    try {
                        j = 555;
                        nmbrVoitureIntersection--;
                        if (nmbrVoitureIntersection == 0 && !feuVert1) {
                           voie1_Cars_In_Intersection.signal();
                        }
                    } finally {
                        verro.unlock();
                    }
                }
                
                C.setBounds(voie1PositionPossible[p - 1], j, 30, 60); //p%80 pour regler la position de la voiture dans la rue(gauche ,droite,centre)
                Thread.sleep(vitess);
            }

        } catch (InterruptedException ex) {
            Logger.getLogger(carrefourManager.class.getName()).log(Level.SEVERE, null, ex);
        }

    }
    
    public void traversee2(JPanel C, int p, int vitess) {

        try {

            for (int j = -60; j < 1035; j++) {
                
                if (voit2stopPositionAtomic.get(p - 1) == -90) {
                    break;
                }
                try {
                    if(stopButtonIsActive.get()){
                            restart.acquire();
                    }
                } catch (InterruptedException ex) {
                       System.out.println(ex.getMessage());
                }
                /*
                verro2.lock();
                try{
                    if(stopButtonIsActive.get()){
                        restart.await();
                    }
                }finally{
                    verro2.unlock();
                }
                */
                if (C.getBounds().x == voit2stopPositionAtomic.get(p - 1)) {
                    //changerStopPositionVoie2(p,true);
                    voit2stopPositionAtomic.set(p - 1, voit2stopPositionAtomic.get(p - 1) - 80);
                    verro.lock();
                    try {
                        while (!feuVert2) {
                            feuVertVoie2.await();
                        }

                    } finally {
                        verro.unlock();
                    }
                    nmbrVoitureIntersection++;
                    circuler("Voie 2", C, p, j, vitess);
                    verro.lock();
                    try {
                        j = 640;
                        nmbrVoitureIntersection--;
                        if (nmbrVoitureIntersection == 0 && !feuVert2) {
                           voie2_Cars_In_Intersection.signal();
                        }
                    } finally {
                        verro.unlock();
                    }
                }
                C.setBounds(j, voie2PositionPossible[p - 1], 60, 30);
                Thread.sleep(vitess);
            }

        } catch (InterruptedException ex) {
            Logger.getLogger(carrefourManager.class.getName()).log(Level.SEVERE, null, ex);
        }

    }
    
    public void circuler(String laVoie, JPanel C, int p, int possitionCirculation, int vitess) {

        try {
            if (laVoie.equals("Voie 1")) {

                voit1stopPositionAtomic.set(p - 1, voit1stopPositionAtomic.get(p - 1) + 80);
                for (int j = possitionCirculation; j < 555; j++) {// --> 555 la fin de carrfeur 
                    try {
                        if(stopButtonIsActive.get()){
                                restart.acquire();
                        }
                    } catch (InterruptedException ex) {
                           System.out.println(ex.getMessage());
                    }
                    /*
                    verro2.lock();
                    try{
                        if(stopButtonIsActive.get()){
                            restart.await();
                        }
                    }finally{
                        verro2.unlock();
                    }
                    */
                    C.setBounds(voie1PositionPossible[p - 1], j, 30, 60);
                    Thread.sleep(vitess);
                }
            } else if (laVoie.equals("Voie 2")) {

                voit2stopPositionAtomic.set(p - 1, voit2stopPositionAtomic.get(p - 1) + 80);
                for (int j = possitionCirculation; j < 640; j++) {// --> 640 la fin de carrfeur
                    try {
                        if(stopButtonIsActive.get()){
                                restart.acquire();
                        }
                    } catch (InterruptedException ex) {
                           System.out.println(ex.getMessage());
                    }
                    /*
                    verro2.lock();
                    try{
                        if(stopButtonIsActive.get()){
                            restart.await();
                        }
                    }finally{
                        verro2.unlock();
                    }
                    */
                    C.setBounds(j, voie2PositionPossible[p - 1], 60, 30);
                    Thread.sleep(vitess);
                }
            }

        } catch (InterruptedException ex) {
            Logger.getLogger(carrefourManager.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

}
