
package mini.projet_dac;

import javax.swing.JPanel;
import static mini.projet_dac.MiniProjet_DAC.carCounterInTheStreet;
import static mini.projet_dac.MiniProjet_DAC.carNumberInTheStreet;


public class voitureV2_V1 extends Thread{
    
    //<editor-fold defaultstate="collapsed" desc="Variables Declaration">
    JPanel car;
    int p;
    int vitess;
    carrefourManager gestionnaire;
    //</editor-fold>
    
    public voitureV2_V1(carrefourManager gestionnaire, JPanel Car, int p ,int vitess){
        this.car = Car;
        this.p = p;
        this.vitess = vitess;
        this.gestionnaire =gestionnaire;
    }
    
    public void run(){
        //System.out.println("la voiture sur voie 2");
        carNumberInTheStreet.incrementAndGet();
        gestionnaire.traversee2(car,p,vitess);
        carNumberInTheStreet.decrementAndGet();
        if(carCounterInTheStreet!=null)
            carCounterInTheStreet.countDown();
        //System.out.println("la voiture "+matricule+" sortir du voie 2");
    }
}
