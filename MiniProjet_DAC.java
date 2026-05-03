package mini.projet_dac;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import static java.lang.Thread.sleep;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import static mini.projet_dac.carrefourManager.*; 

public class MiniProjet_DAC extends JFrame {
    
    //<editor-fold defaultstate="collapsed" desc="Variables Declaration">
    carrefourManager carrefour;          //carrefour manager
    lightManager changeFeu;             //light manager
    Thread createCars;                    //thread that manages the creation of the cars
    //Swing components for the graphic interface
    private JPanel containerPanel;                                              
    private JPanel settingPanel;
    private JButton stopButton;
    private JButton startButton;
    private JLabel timerLabel;
    static JLabel lightTimer;
    private JLabel tittleLabel;
    private JLabel speedLabel;
    private JLabel trafficLabel;
    private JLabel lightDurationLabel;
    private JLabel carCountLabel;
    private JSlider speedSlider;
    private JSlider trafficGrowthSlider;
    private JSlider lightDurationSlider;
    private JSlider carCountSlider;
    private imgVoitureVoie1 C1;
    private imgVoitureVoie2 C2;
    static JLabel feuVoie1Green;
    static JLabel feuVoie1Orange;
    static JLabel feuVoie1Red;
    private JPanel feuVoie1Panel;
    static JLabel feuVoie2Green;
    static JLabel feuVoie2Orange;
    static JLabel feuVoie2Red;
    private JPanel feuVoie2Panel;
    private backgroundPanel crossroadPanel;
    // atomic varibales are used to avoid concurrency on the use of the same variable by multiple threads ---> this can be replaced by semaphore mutex
    static AtomicInteger duree_de_feu = new AtomicInteger(10000);     //duree between (5000-->5s , 20000-->20s)
    int speed = 4;   //speed between (1-8)
    int circulationGrow = (new Random().nextInt(8) + 1) * 1000;       // traffic between (1000-->1s, 8000-->8s)
    int carsPerWave = 3;  //number of cars created in each direction every traffic interval
    static AtomicInteger seconds = new AtomicInteger(duree_de_feu.get() / 1000);//seconds for the timer counter
    AtomicBoolean settingChanged = new AtomicBoolean(false);      //this is used when you change the settings (speed or light duration) 
    static AtomicInteger carNumberInTheStreet = new AtomicInteger(0);   //counter for the cars in the street (v1 or v2)
    static CountDownLatch carCounterInTheStreet;         //coutdownlatch to wait for the cars in the street
    static AtomicBoolean stopButtonIsActive = new AtomicBoolean(true);    //this is used when you press the stop button
    
     //</editor-fold>
    
    public MiniProjet_DAC() {
        
        //<editor-fold defaultstate="collapsed" desc="initialisation of the swing components and configueration of there layout">
        containerPanel = new JPanel();
        settingPanel = new JPanel();
        crossroadPanel = new backgroundPanel();
        feuVoie2Panel = new JPanel();
        feuVoie2Red = new JLabel();
        feuVoie2Orange = new JLabel();
        feuVoie2Green = new JLabel();
        feuVoie1Panel = new JPanel();
        feuVoie1Red = new JLabel();
        feuVoie1Orange = new JLabel();
        feuVoie1Green = new JLabel();
        timerLabel = new JLabel();
        lightTimer = new JLabel();
        tittleLabel = new JLabel();
        speedLabel = new JLabel();
        speedSlider = new JSlider();
        trafficLabel = new JLabel();
        trafficGrowthSlider = new JSlider();
        lightDurationSlider = new JSlider();
        lightDurationLabel = new JLabel();
        carCountLabel = new JLabel();
        carCountSlider = new JSlider();
        stopButton = new JButton();
        startButton = new JButton();
        
        crossroadPanel.setLayout(null);
        crossroadPanel.setBounds(0, 0, 1035, 840);
        
        
        feuVoie2Panel.setLayout(null);

        feuVoie2Red.setBackground(new java.awt.Color(255, 255, 255));
        feuVoie2Red.setBounds(0, 0, 35, 30);
        feuVoie2Red.setIcon(new javax.swing.ImageIcon("lights/1.png"));
        feuVoie2Red.setEnabled(false);
        feuVoie2Panel.add(feuVoie2Red);
        feuVoie2Orange.setBounds(35, 0, 35, 30);
        feuVoie2Orange.setIcon(new javax.swing.ImageIcon("lights/3.jpg"));
        feuVoie2Orange.setEnabled(false);
        feuVoie2Panel.add(feuVoie2Orange);
        feuVoie2Green.setBounds(70, 0, 35, 30);
        feuVoie2Green.setIcon(new javax.swing.ImageIcon("lights/2.jpg"));
        feuVoie2Green.setEnabled(false);
        feuVoie2Panel.add(feuVoie2Green);
        
        feuVoie2Panel.setBounds(270, 540, 105, 30);
        crossroadPanel.add(feuVoie2Panel);

        feuVoie1Panel.setLayout(null);
        
        feuVoie1Red.setBounds(0, 0, 30, 35);
        feuVoie1Red.setIcon(new javax.swing.ImageIcon("lights/1*.png"));
        feuVoie1Red.setEnabled(false);
        feuVoie1Panel.add(feuVoie1Red);
        feuVoie1Orange.setBounds(0, 35, 30, 35);
        feuVoie1Orange.setIcon(new javax.swing.ImageIcon("lights/3*.jpg"));
        feuVoie1Orange.setEnabled(false);
        feuVoie1Panel.add(feuVoie1Orange);
        feuVoie1Green.setBounds(0, 70, 30, 35);
        feuVoie1Green.setIcon(new javax.swing.ImageIcon("lights/2*.jpg"));
        feuVoie1Green.setEnabled(false);
        feuVoie1Panel.add(feuVoie1Green);
        
        feuVoie1Panel.setBounds(630, 180, 30, 105);
        crossroadPanel.add(feuVoie1Panel);

        
        containerPanel.add(crossroadPanel);

        settingPanel.setBackground(new java.awt.Color(255, 255, 255));
        settingPanel.setLayout(null);

        timerLabel.setFont(new java.awt.Font("Chalkboard SE", 0, 36)); // NOI18N
        timerLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        timerLabel.setText("Timer :");
        timerLabel.setBounds(50, 110, 120, 50);
        settingPanel.add(timerLabel);

        lightTimer.setFont(new java.awt.Font("Myanmar MN", 0, 36)); // NOI18N
        lightTimer.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lightTimer.setBackground(new java.awt.Color(255, 255, 255));
        lightTimer.setForeground(new java.awt.Color(255, 0, 51));
        lightTimer.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(0, 0, 0), 1, true));
        lightTimer.setOpaque(true);
        lightTimer.setText(String.valueOf(seconds.get()));
        lightTimer.setBounds(200, 110, 120, 50);
        settingPanel.add(lightTimer);

        tittleLabel.setFont(new java.awt.Font("Chalkboard SE", 0, 36)); // NOI18N
        tittleLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        tittleLabel.setText("SETTINGS :");
        tittleLabel.setBounds(10, 10, 400, 70);
        settingPanel.add(tittleLabel);

        speedLabel.setFont(new java.awt.Font("Chalkboard SE", 0, 24)); // NOI18N
        speedLabel.setText("Speed :");
        speedLabel.setBounds(10, 200, 150, 50);
        settingPanel.add(speedLabel);

        speedSlider.setBackground(new java.awt.Color(230, 230, 230));
        speedSlider.setForeground(new java.awt.Color(0, 0, 0));
        speedSlider.setValue(speed);
        speedSlider.setMinimum(1);
        speedSlider.setMaximum(8);
        speedSlider.setMajorTickSpacing(1);
        speedSlider.setMinorTickSpacing(1);
        speedSlider.setPaintLabels(true);
        speedSlider.setPaintTicks(true);
        speedSlider.setSnapToTicks(true);
        speedSlider.setOpaque(true);
        speedSlider.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent event) {
                if (!settingChanged.get()) { //testing if this is the first setting changed before been applied
                    settingChanged.set(true); //setting changed waiting to be applied 
                    carCounterInTheStreet = new CountDownLatch(carNumberInTheStreet.get()); //initialisation of the countdownlatch with the number of cars in the street to wait for theme before applying setting changes
                }
                speed = 8 - speedSlider.getValue() + 1; //reseting the new spped of the cars
            }
        });
        speedSlider.setBounds(10, 260, 380, 50);
        settingPanel.add(speedSlider);

        trafficLabel.setFont(new java.awt.Font("Chalkboard SE", 0, 24)); // NOI18N
        trafficLabel.setText("Traffic Growth :");
        trafficLabel.setBounds(10, 320, 200, 50);
        settingPanel.add(trafficLabel);

        trafficGrowthSlider.setBackground(new java.awt.Color(230, 230, 230));
        trafficGrowthSlider.setForeground(new java.awt.Color(0, 0, 0));
        trafficGrowthSlider.setValue(circulationGrow / 1000);
        trafficGrowthSlider.setMaximum(8);
        trafficGrowthSlider.setMinimum(1);
        trafficGrowthSlider.setMajorTickSpacing(1);
        trafficGrowthSlider.setMinorTickSpacing(1);
        trafficGrowthSlider.setPaintLabels(true);
        trafficGrowthSlider.setPaintTicks(true);
        trafficGrowthSlider.setSnapToTicks(true);
        trafficGrowthSlider.setOpaque(true);
        trafficGrowthSlider.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent event) {
                circulationGrow = trafficGrowthSlider.getValue() * 1000;//resetting the waited time before entring new cars to the street
            }
        });
        trafficGrowthSlider.setBounds(10, 380, 380, 50);
        settingPanel.add(trafficGrowthSlider);

        lightDurationSlider.setBackground(new java.awt.Color(230, 230, 230));
        lightDurationSlider.setForeground(new java.awt.Color(0, 0, 0));
        lightDurationSlider.setValue(duree_de_feu.get() / 1000);
        lightDurationSlider.setMaximum(20);
        lightDurationSlider.setMinimum(5);
        lightDurationSlider.setMajorTickSpacing(1);
        lightDurationSlider.setMinorTickSpacing(1);
        lightDurationSlider.setPaintLabels(true);
        lightDurationSlider.setPaintTicks(true);
        lightDurationSlider.setSnapToTicks(true);
        lightDurationSlider.setOpaque(true);
        lightDurationSlider.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent event) {
                if (!settingChanged.get()) {//testing if this is the first setting changed before been applied
                    settingChanged.set(true);//setting changed waiting to be applied
                    carCounterInTheStreet = new CountDownLatch(carNumberInTheStreet.get());//initialisation of the countdownlatch with the number of cars in the street to wait for theme before applying setting changes
                }
                duree_de_feu.set(lightDurationSlider.getValue() * 1000);//resetting the new light duration
                mainStopedTheTimer.set(true); //resetting this boolean to true which indicate the change of light duration to stop the timer by the carrefour manager
            }
        });
        lightDurationSlider.setBounds(10, 510, 380, 50);
        settingPanel.add(lightDurationSlider);

        lightDurationLabel.setFont(new java.awt.Font("Chalkboard SE", 0, 24)); // NOI18N
        lightDurationLabel.setText("Light Duration :");
        lightDurationLabel.setBounds(10, 440, 200, 50);
        settingPanel.add(lightDurationLabel);

        carCountLabel.setFont(new java.awt.Font("Chalkboard SE", 0, 24)); // NOI18N
        carCountLabel.setText("Number of Cars :");
        carCountLabel.setBounds(10, 570, 220, 50);
        settingPanel.add(carCountLabel);

        carCountSlider.setBackground(new java.awt.Color(230, 230, 230));
        carCountSlider.setForeground(new java.awt.Color(0, 0, 0));
        carCountSlider.setValue(carsPerWave);
        carCountSlider.setMaximum(5);
        carCountSlider.setMinimum(1);
        carCountSlider.setMajorTickSpacing(1);
        carCountSlider.setMinorTickSpacing(1);
        carCountSlider.setPaintLabels(true);
        carCountSlider.setPaintTicks(true);
        carCountSlider.setSnapToTicks(true);
        carCountSlider.setOpaque(true);
        carCountSlider.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent event) {
                carsPerWave = carCountSlider.getValue();
            }
        });
        carCountSlider.setBounds(10, 630, 380, 50);
        settingPanel.add(carCountSlider);

        stopButton.setFont(new java.awt.Font("Chalkboard SE", 0, 18)); // NOI18N
        stopButton.setText("STOP");
        stopButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent arg0) {
                startButton.setEnabled(true);
                stopButton.setEnabled(false);
                stopButtonIsActive.set(true); //resseting the boolean that indicate the stop of the cars
            }
        });
        stopButton.setBounds(210, 740, 130, 50);
        settingPanel.add(stopButton);
        
        startButton.setFont(new java.awt.Font("Chalkboard SE", 0, 18)); // NOI18N
        startButton.setText("START");
        startButton.addActionListener(new ActionListener() {
            
            public void actionPerformed(ActionEvent arg0) { 
                //restarting the cars after the start button pressed
                creationNewCars(arg0);
            }
        });
        settingPanel.add(startButton);
        startButton.setBounds(50, 740, 130, 50);

        settingPanel.setBounds(1035, 0, 400, 840);
        containerPanel.add(settingPanel);

        containerPanel.setLayout(null);
        containerPanel.setBounds(0, 0, 1440, 840);
        
        setResizable(false);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setBounds(0, 0, 1440, 830);
        add(containerPanel);
         //</editor-fold>
    }
    
    public void creationNewCars(ActionEvent e){
        
        startButton.setEnabled(false);
        stopButton.setEnabled(true);

        // implementation with locks not the best because 
        //there is no guarantee to wake theme with FIFO 
        //it depends on JVM which could make some problem in high speed and big traffic
        /*
        verro2.lock();
        try{
            restart.signalAll();
        }finally{
            verro2.unlock();
        }*/
        //implementation with semaphore solves this problem to us
        if (stopButtonIsActive.get()) {

            restart.release(carNumberInTheStreet.get() + 2);
        }
        stopButtonIsActive.set(false);
        mainStopedTheTimer.set(false);

        if (carrefour == null) {
            carrefour = new carrefourManager();

            changeFeu = new lightManager(carrefour);
            changeFeu.start();

            createCars = new Thread(new Runnable() { //the work of the thread that creats cars
                @Override
                public void run() {
                    int voie1Position;
                    int voie2Position;

                    while (/*!stopButtonIsActive.get()*/true) {  //this comment was when we used locks
                        try {
                            if (stopButtonIsActive.get()) {
                                restart.acquire();
                                sleep(500); //this small sleep is for some security reason
                            }
                        } catch (InterruptedException ex) {
                            System.out.println(ex.getMessage());
                        }

                        try {
                            sleep(circulationGrow);
                            if (settingChanged.get()) { //if settings changes stop creating new cars until the settings been applied
                                carCounterInTheStreet.await();
                                settingChanged.set(false);
                                if (mainStopedTheTimer.get()) {
                                     verro.lock();
                                    try{
                                        mainStopedTheTimer.set(false);
                                        mainRestartTimer.signal();
                                    }finally{
                                        verro.unlock();
                                    }
                                    
                                }
                            }
                        } catch (InterruptedException ex) {
                            Logger.getLogger(carrefourManager.class.getName()).log(Level.SEVERE, null, ex);
                        }

                        for (int i = 0; i < carsPerWave; i++) {
                            voie1Position = (new Random().nextInt(4)) + 1;//taking random position for the car in voie1
                            voie2Position = (new Random().nextInt(4)) + 1;//taking random position for the car in voie2

                            voitureV1_V2 voitureVoie1;
                            voitureV2_V1 voitureVoie2;
                            C1 = new imgVoitureVoie1();
                            C1.setOpaque(true);
                            C1.setBounds(0, -60, 30, 60);
                            crossroadPanel.add(C1);
                            C2 = new imgVoitureVoie2();
                            C2.setOpaque(true);
                            crossroadPanel.add(C2);
                            C2.setBounds(-60, 0, 60, 30);
                            voitureVoie1 = new voitureV1_V2(carrefour, C1, voie1Position, voie1Position * speed);//creating the car thread 
                            voitureVoie2 = new voitureV2_V1(carrefour, C2, voie2Position, voie2Position * speed);//creating the car thread
                            voitureVoie1.start();
                            voitureVoie2.start();

                            try {
                                sleep(250);
                            } catch (InterruptedException ex) {
                                Logger.getLogger(carrefourManager.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }
                    }
                    /* //this was when we used locks
                    mytimer.stop();
                    mainStopedTheTimer.set(true);
                    seconds.set(duree_de_feu.get() / 1000);
                    lightTimer.setText(String.valueOf(seconds.get()));
                    */

                }
            });
            createCars.start();
        }
        //mytimer.start(); //this was when we used locks
    }

    public static void main(String[] args) {
        new MiniProjet_DAC().setVisible(true);

    }

}
