import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DeliverCallback;

public class App {
    private static String[] topics  = {"politics", "science", "sports"};
    private static String name, decision;
    private static int leftPort, rightPort;
    private static boolean hasToken = false;
    private static List<Integer> publishList = new ArrayList<>();
    private static List<Integer> subscriptionsList = new ArrayList<>();
    private static String storedMessage = "";

    public static void initializeProcess() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        System.out.print("Your debater name is?: ");
        name = reader.readLine();

        System.out.print("Do you want to be the first publisher?(y/n): ");
        String decision = reader.readLine();

        if (decision.toLowerCase().compareTo("y") == 0)
            hasToken = true;

        for (int i = 0; i < topics.length; i ++) {
            System.out.print("Do you want to subscribe to " + topics[i] + " topic?(y/n): ");
            decision = reader.readLine();

            if (decision.toLowerCase().compareTo("y") == 0)
                subscriptionsList.add(i);
        }

        for (int i = 0; i < topics.length; i ++) {
            System.out.print("Do you want to be a publisher for topic: " + topics[i] + "?(y/n): ");
            decision = reader.readLine();

            if (decision.toLowerCase().compareTo("y") == 0)
                publishList.add(i);
        }

        System.out.println("Read the left and the right sockets: ");
        System.out.print("Left Port is: ");
        String portNumber = reader.readLine();
        leftPort = Integer.parseInt(portNumber);

        System.out.print("Right Port is: ");
        portNumber = reader.readLine();
        rightPort = Integer.parseInt(portNumber);
    }

    public static void main(String[] argv) throws Exception {
        initializeProcess();

        TimeUnit.SECONDS.sleep(2);

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");

        Connection connection = factory.newConnection();
        Channel[] pubChannels = new Channel[topics.length];
        Channel[] subChannels = new Channel[topics.length];

        for (int i : publishList) {
            pubChannels[i] = connection.createChannel();
            pubChannels[i].exchangeDeclare(topics[i], "fanout");
        }

        String []queueNames = new String[topics.length];
        for (int i : subscriptionsList) {
            subChannels[i] = connection.createChannel();
            subChannels[i].exchangeDeclare(topics[i], "fanout");
            queueNames[i] = subChannels[i].queueDeclare().getQueue();
            subChannels[i].queueBind(queueNames[i], topics[i], "");
        }

        while (true)
        {
            if (hasToken == true) {
                Socket clientSocket = new Socket("localhost", rightPort);
                //clear the buffer
                System.out.println(name + " has the token!");

                for (int i : publishList) {
                    System.out.print(topics[i] + ": ");
                    ConsoleInput con = new ConsoleInput(10, TimeUnit.SECONDS);
                    String message = con.readLine();

                    if (message == null)
                        System.out.println("Nothing to publish for this topic!");
                    else {
                        String pubMessage = name + ":" + topics[i] + ":" + message;
                        storedMessage = pubMessage;
                        pubChannels[i].basicPublish(topics[i], "", null, pubMessage.getBytes("UTF-8"));
                    }
                }

                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                out.println("TOKEN");

                System.out.println(name + " doesn't have the token anymore!");
                hasToken = false;
                clientSocket.close();
            } else {
                System.out.println(name + " is a subcriber!");

                DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                    String message = new String(delivery.getBody(), "UTF-8");
                    if (storedMessage.compareTo(message) != 0) {
                        String[] tokens = message.split(":");
                        System.out.println("\n" + name + " recived a message from " + tokens[0] + ": " + tokens[1] + ":" + tokens[2]);
                    }
                };

                for (int i : subscriptionsList)
                    subChannels[i].basicConsume(queueNames[i], true, deliverCallback, consumerTag -> {});

                try(ServerSocket serverSocket = new ServerSocket(leftPort)) {
                    while (true)
                    {
                        try (Socket socket = serverSocket.accept()){
                            Scanner in = new Scanner(socket.getInputStream());
                            String message = in.nextLine();
                            if (message.compareTo("TOKEN") == 0) {
                                socket.close();
                                serverSocket.close();
                                hasToken = true;
                                break;
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

}
