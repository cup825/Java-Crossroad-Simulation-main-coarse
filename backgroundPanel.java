package mini.projet_dac;
import java.awt.Graphics; 
import java.awt.Image; 
import java.io.File;
import java.io.IOException; 
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.JPanel;


public class backgroundPanel extends JPanel {
    Image background;
    
    public backgroundPanel(){
        try { 
            this.background = ImageIO.read(new File("crossroad.png"));
        } catch (IOException ex) {
            Logger.getLogger(backgroundPanel.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
        
    @Override
        public void paintComponent(Graphics g){  
            g.drawImage(background, 0, 0, this.getWidth(), this.getHeight(), this);  
			  
			 
        }		                          

}
