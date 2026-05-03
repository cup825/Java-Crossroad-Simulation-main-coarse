# Java Crossroad Simulation - Concurrency Project 🚗🚦

## Project Description 📝

The "Java Crossroad Simulation" is a university project developed as part of the "Development of Concurrent Applications" course. The objective of the project was to apply Java concurrency and multithreading concepts to simulate a crossroad scenario, where each car is represented as a separate thread.

The main challenge of this project was to design and implement a Java Swing application that simulates a bustling crossroad with multiple lanes. Each car was treated as an individual thread, and the task was to efficiently manage their movements and interactions to prevent potential collisions and ensure smooth traffic flow.

This project aimed to demonstrate a practical understanding of various concurrency mechanisms in Java, including semaphores, locks, countdown latches, and cyclic barriers. By applying these concepts, we sought to create a realistic and engaging crossroad simulation that showcased the power and complexity of concurrent applications.

## Key Features ✨

The "Java Crossroad Simulation" offers a dynamic dashboard with intuitive controls to customize and observe the crossroad behavior:

🚦 Traffic Control: Seamlessly manage the traffic flow with adjustable car speeds, allowing you to fine-tune the movement of each vehicle.

🚗 Traffic Growth: Control the number of cars crossing the crossroad per second, simulating varying traffic densities.

⏲️ Light Duration: Set the time interval for the traffic light to change, influencing the crossroad's traffic control.

🕐 Timer Display: A live countdown timer shows the time remaining before the traffic light changes, allowing you to anticipate traffic movements.

▶️ Start/Stop Button: Initiate and pause the simulation at your convenience, giving you full control of the crossroad scenario.

With this comprehensive dashboard, you can experience the excitement of managing a bustling crossroad while exploring the complexities of Java concurrency and multithreading. Enjoy experimenting with various settings to create unique and engaging traffic simulations! 🚦🚗

## Technologies and Concepts Used 🔧

The "Java Crossroad Simulation" leverages an array of technologies and concepts to create an efficient and realistic traffic simulation, making use of:

- **Java Concurrency and Multithreading**: The foundation of the project, Java's powerful concurrency and multithreading capabilities are harnessed to manage concurrent execution of multiple car threads and synchronize their interactions at the crossroad. The concept of threads allows us to model each car as an independent entity, simulating real-world traffic scenarios effectively.

- **Semaphore**: Semaphore acts as a key player in regulating the number of cars allowed to cross the crossroad simultaneously. By using semaphores, we ensure that only a limited number of cars, defined by the semaphore permits, can move through the intersection concurrently. Semaphore's signaling mechanism guarantees smooth traffic flow, preventing congestion and collisions.

- **Locks**: Employing locks ensures exclusive access to shared resources and critical sections of code. ReentrantLock, in particular, helps maintain mutual exclusion, enabling safe access to the crossroad's state variables and resources shared among multiple car threads. Locks play a vital role in avoiding data corruption and race conditions in the simulation.

- **CountDownLatch**: CountDownLatch facilitates synchronization between the traffic light and cars. The countdown latch allows the traffic light to wait until all cars have arrived at the intersection before changing the signal. This synchronization ensures that cars do not pass while the traffic light is transitioning, preventing any disruptions to traffic flow.

- **Java Swing**: Java Swing forms the graphical user interface (GUI) of the simulation, providing an interactive platform to visualize the bustling crossroad. We use Swing components such as buttons, labels, and panels to create the dashboard and display real-time updates. The event-driven nature of Swing allows us to respond to user inputs and dynamically adjust the simulation settings.

By expertly applying these technologies and concepts, the "Java Crossroad Simulation" achieves an engaging and highly interactive user experience. The project highlights the capabilities of Java's concurrency mechanisms in creating sophisticated simulations while emphasizing the significance of synchronization and efficient resource management in concurrent applications. Enjoy exploring the intricacies of this traffic simulation and witnessing how these concepts contribute to the seamless flow of cars in the crossroad! 🚦🚗🧰

## How to Run the Simulation ▶️

1. Clone or download the project to your local machine.

2. Open the project in your preferred Java development environment.

3. Locate the `MiniProjet_DAC.java` class as the entry point of the simulation.

4. Compile and run the `MiniProjet_DAC.java` class to initiate the simulation.

5. The Java Swing application will open, displaying the dynamic dashboard to control the crossroad simulation.

6. Use the following key features of the app to customize the crossroad behavior:

   - 🚗 Adjust Car Speeds: Fine-tune the movement speed of each car to observe different traffic patterns.
   - 🚦 Control Traffic Growth: Set the number of cars crossing the crossroad per second to simulate varying traffic densities.
   - ⏲️ Set Light Duration: Define the time interval for the traffic light to change, influencing traffic control.
   - ▶️ Start/Stop Button: Initiate and pause the simulation at your convenience, giving you full control of the crossroad scenario.

7. Experiment with different combinations of settings to create diverse traffic scenarios and observe the interactions between cars at the crossroad.

Enjoy the dynamic and interactive experience of managing the bustling crossroad, honing your skills in Java concurrency and multithreading while ensuring the smooth flow of traffic! 🚀🚗🚦

## License 📄

This project is licensed under the MIT License.

## Acknowledgements 🙏

Special thanks to our course instructor for providing valuable guidance and knowledge throughout the development of this project. Additionally, we extend our appreciation to the Java community and online resources for their invaluable contributions to our learning journey.
