
package mini.projet_dac;

import javax.swing.JPanel;
import static mini.projet_dac.MiniProjet_DAC.carCounterInTheStreet;
import static mini.projet_dac.MiniProjet_DAC.carNumberInTheStreet;


// Voie 1 car thread — moves top-to-bottom on the vertical road.
// Each instance is a separate thread; concurrency is managed by
// carrefourManager via ReentrantLock, Conditions, and Semaphore.
public class voitureV1_V2 extends Thread {
    
    //Variables Declaration
    int matricule;
    int p;          // lane index (1-4)
    int vitess;     // speed (sleep ms per pixel)
    carrefourManager gestionnaire;
    JPanel car;     // the Swing panel representing this car
    
    public voitureV1_V2(carrefourManager gestionnaire, JPanel Car, int p ,int vitess){
        this.car = Car;
        this.p = p;
        this.vitess = vitess;
        this.gestionnaire =gestionnaire;
    }
    
    public void run(){
        // Concurrency technique: AtomicInteger increment/decrement
        // to track how many cars are currently on the road, used by
        // CountDownLatch for settings-change synchronisation.
        carNumberInTheStreet.incrementAndGet();

        // Call the FIXED traversal method (4-phase: approach, wait, cross, exit)
        gestionnaire.traversee1(car, p, vitess);

        carNumberInTheStreet.decrementAndGet();
        if(carCounterInTheStreet!=null)
            carCounterInTheStreet.countDown();
    }
}
