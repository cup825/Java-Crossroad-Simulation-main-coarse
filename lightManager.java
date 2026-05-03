
package mini.projet_dac;


import java.util.logging.Level;
import java.util.logging.Logger;
import static mini.projet_dac.MiniProjet_DAC.carNumberInTheStreet;
import static mini.projet_dac.MiniProjet_DAC.duree_de_feu;
import static mini.projet_dac.MiniProjet_DAC.stopButtonIsActive;


public class lightManager extends Thread {
    
    carrefourManager gestionnaire;
    
    
    public lightManager(carrefourManager gestionnaire){
        
        this.gestionnaire = gestionnaire;
    
    }
    
    @Override
    public void run(){
        
        while(!stopButtonIsActive.get() || carNumberInTheStreet.get()!=0 ){
            try {
                gestionnaire.Intersection();
                sleep(duree_de_feu.get());
            } catch (InterruptedException ex) {
                Logger.getLogger(lightManager.class.getName()).log(Level.SEVERE, null, ex);
            }
            
            
        }
    }
}
