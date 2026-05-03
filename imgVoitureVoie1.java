package mini.projet_dac;
import java.awt.Graphics; 
import java.awt.Graphics2D;
import java.awt.Image; 
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException; 
import java.util.Random;
import javax.imageio.ImageIO;
import javax.swing.JPanel;


public class imgVoitureVoie1 extends JPanel {
    
    BufferedImage voitureVoie1;
    int randomCarNumber;
    
    public imgVoitureVoie1(){
        try {  
            randomCarNumber = (new Random().nextInt(13)) + 1;
            voitureVoie1 = ImageIO.read(new File("cars/voie1/car"+randomCarNumber+".png"));      
            } catch (IOException e){
                System.out.println(e.getMessage());
            }
    
    }
    
    @Override
    public void paintComponent(Graphics g){  
            Graphics2D g2d  = (Graphics2D) g;
             g2d.drawImage(voitureVoie1, 0, 0, this.getWidth(), this.getHeight(), this); 

    }		                          

}
