
package mini.projet_dac;

import javax.swing.JPanel;
import static mini.projet_dac.MiniProjet_DAC.carCounterInTheStreet;
import static mini.projet_dac.MiniProjet_DAC.carNumberInTheStreet;


// Voie 2 car thread — moves left-to-right on the horizontal road.
// Each instance is a separate thread; concurrency is managed by
// carrefourManager via ReentrantLock, Conditions, and Semaphore.
public class voitureV2_V1 extends Thread{
    
    //Variables Declaration
    JPanel car;     // the Swing panel representing this car
    int p;          // lane index (1-4)
    int vitess;     // speed (sleep ms per pixel)
    carrefourManager gestionnaire;
    
    public voitureV2_V1(carrefourManager gestionnaire, JPanel Car, int p ,int vitess){
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
        gestionnaire.traversee2(car, p, vitess);

        carNumberInTheStreet.decrementAndGet();
        if(carCounterInTheStreet!=null)
            carCounterInTheStreet.countDown();
    }
}
