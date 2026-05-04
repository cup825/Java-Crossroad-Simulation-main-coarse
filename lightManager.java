
package mini.projet_dac;


import java.util.logging.Level;
import java.util.logging.Logger;
import static mini.projet_dac.MiniProjet_DAC.carNumberInTheStreet;
import static mini.projet_dac.MiniProjet_DAC.duree_de_feu;
import static mini.projet_dac.MiniProjet_DAC.stopButtonIsActive;


// Light controller thread — runs in an infinite loop, alternating
// the traffic lights between Voie 1 (green) and Voie 2 (green).
//
// Concurrency technique: this thread interacts with carrefourManager
// through the shared ReentrantLock 'verro' and its Conditions.
// It calls Intersection() which acquires the lock, waits for cars
// to clear the intersection, then switches the lights and signals
// waiting car threads.
public class lightManager extends Thread {
    
    carrefourManager gestionnaire;
    
    
    public lightManager(carrefourManager gestionnaire){
        this.gestionnaire = gestionnaire;
    }
    
    @Override
    public void run(){
        // BUG FIX: the original condition was:
        //   while(!stopButtonIsActive.get() || carNumberInTheStreet.get()!=0)
        // This caused the light thread to EXIT as soon as stop was pressed
        // AND no cars were left, meaning it could never be restarted.
        // The light thread should run forever (it will block inside
        // Intersection() when STOP is pressed via the semaphore gate).
        while(true){
            try {
                gestionnaire.Intersection();
                sleep(duree_de_feu.get());
            } catch (InterruptedException ex) {
                Logger.getLogger(lightManager.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
}
